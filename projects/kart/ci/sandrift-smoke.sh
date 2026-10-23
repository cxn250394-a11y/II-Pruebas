#!/usr/bin/env bash
# Sandrift Karts v0.2 emulator smoke. Synthetic only: never touches a real phone, account or network service.
# Emulator numbers come from a software-rendered x86_64 AVD and say nothing about the POCO F7.
set -euo pipefail
APK="$1"; OUT="${2:-sandrift-evidence}"; PKG=mx.charly.kart; ACT="$PKG/.KartActivity"
mkdir -p "$OUT"; R="$OUT/RESULTS.txt"; : > "$R"
pass(){ echo "PASS $*" | tee -a "$R"; }
fail(){ echo "FAIL $*" | tee -a "$R"; adb logcat -d > "$OUT/logcat-full.txt" || true; exit 1; }
alive(){ adb shell pidof "$PKG" >/dev/null 2>&1; }
crashed(){ adb logcat -d -s AndroidRuntime:E | grep -q 'FATAL EXCEPTION'; }
shot(){ adb exec-out screencap -p > "$OUT/$1.png"; }
size(){ adb exec-out screencap -p > "$OUT/.probe.png"; python3 -c "import struct;b=open('$OUT/.probe.png','rb').read();print(*struct.unpack('>II',b[16:24]))"; }
vtap(){ read -r PX PY < <(size); python3 -c "
px,py,vx,vy=$PX,$PY,$1,$2
a=px/py
W,H=(600*a,600.0) if a>=1000/600 else (1000.0,1000/a)
print(int(vx/W*px),int(py-vy/H*py))" | { read -r X Y; adb shell input tap "$X" "$Y"; echo "tap $1,$2 -> $X,$Y ($PX x $PY)" >> "$OUT/taps.txt"; }; }

adb shell input keyevent 82 || true
adb install -r "$APK" > "$OUT/install.txt" && grep -q Success "$OUT/install.txt" && pass "install" || fail "install"
adb shell dumpsys package "$PKG" | grep -E "versionName|requested permissions|android.permission" > "$OUT/package.txt" || true

# 1) Cold start on the menu.
adb logcat -c; adb shell am start -W -n "$ACT" > "$OUT/launch.txt"; sleep 6
alive && ! crashed && pass "launch-menu" || fail "launch-menu"; shot 01-menu

# 2) Tap INICIAR through the real touch path (menu 'race' button centre = 325,88 virtual units).
vtap 325 88; sleep 8; shot 02-race-tap
alive && ! crashed && pass "menu-tap-start" || fail "menu-tap-start"

# 3) Synthetic race with automatic throttle (autostart extra) and steering/drift/item taps.
adb shell am force-stop "$PKG"; adb logcat -c
adb shell am start -W -n "$ACT" --es autostart race > "$OUT/launch-race.txt"; sleep 9; shot 03-race-start
for i in $(seq 1 6); do vtap 250 100; sleep .4; vtap 100 100; sleep .3; done
vtap 129 235; sleep 6; shot 04-race-running
alive && ! crashed && pass "race-running" || fail "race-running"

# 4) Pause with BACK, screenshot, resume with BACK.
adb shell input keyevent 4; sleep 2; shot 05-paused
adb shell input keyevent 4; sleep 2
alive && ! crashed && pass "pause-resume" || fail "pause-resume"

# 5) Background / foreground.
adb shell input keyevent 3; sleep 4; adb shell am start -n "$ACT" > /dev/null; sleep 5; shot 06-after-background
alive && ! crashed && pass "background-foreground" || fail "background-foreground"

# 6) Reverse landscape rotation (sensorLandscape).
adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 3; sleep 4; shot 07-reverse-landscape
adb shell settings put system user_rotation 1; sleep 3
alive && ! crashed && pass "rotation" || fail "rotation"

# 7) Second aspect ratio: 16:10 tablet-like surface.
adb shell wm size 800x1280; sleep 3; adb shell am force-stop "$PKG"; adb shell am start -W -n "$ACT" > /dev/null; sleep 6; shot 08-menu-16x10
vtap 325 88; sleep 9; shot 09-race-16x10
alive && ! crashed && pass "aspect-16x10" || fail "aspect-16x10"
adb shell wm size reset; sleep 2

# 8) Frame statistics measured by the game itself on this emulator (NOT a phone measurement).
adb shell am force-stop "$PKG"; adb logcat -c; adb shell am start -W -n "$ACT" --es autostart race > /dev/null; sleep 40
adb logcat -d -s SandriftPerf:I > "$OUT/perf-emulator-swiftshader.txt" || true
adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt" || true
alive && ! crashed && pass "perf-run-40s" || fail "perf-run-40s"
adb logcat -d -s AndroidRuntime:E > "$OUT/errors.txt" || true
rm -f "$OUT/.probe.png"
echo "NOTE emulator=API35 x86_64 swiftshader; FPS here is not representative of POCO F7" >> "$R"
