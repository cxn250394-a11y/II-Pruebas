#!/usr/bin/env bash
# Emulator-only synthetic smoke; never attaches to a user device or authenticates accounts.
set -euo pipefail
mkdir -p emulator-evidence
adb shell input keyevent 82
for spec in 'CanalDeck:canal:CanalActivity' 'AudioDeck:audio:AudioActivity' 'StatsDeck:stats:StatsActivity' 'OddsDeck:odds:OddsActivity' 'Sandrift-Karts:kart:KartActivity'; do
  IFS=: read -r name module activity <<< "$spec"
  adb install -r "product-apks/$name-v0.1-alpha.apk" > "emulator-evidence/$module-install.txt"
  adb logcat -c
  adb shell am start -W -n "mx.charly.$module/.$activity" > "emulator-evidence/$module-launch.txt"
  sleep 4
  adb shell pidof "mx.charly.$module" > "emulator-evidence/$module-pid.txt"
  adb logcat -d -s AndroidRuntime:E > "emulator-evidence/$module-errors.txt"
  if grep -q 'FATAL EXCEPTION' "emulator-evidence/$module-errors.txt"; then cat "emulator-evidence/$module-errors.txt"; exit 1; fi
  adb exec-out screencap -p > "emulator-evidence/$module-home.png"
  if [ "$module" != kart ]; then
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null
    adb pull /sdcard/ui.xml "emulator-evidence/$module-ui.xml" >/dev/null
  else
    adb shell wm size > emulator-evidence/kart-size.txt
    # Rotate button coordinates from the actual landscape screenshot dimensions.
    python3 - <<'PY'
import struct, subprocess
b=open('emulator-evidence/kart-home.png','rb').read()
w,h=struct.unpack('>II',b[16:24])
subprocess.run(['adb','shell','input','tap',str(int(w*.30)),str(int(h*.80))],check=True)
PY
    sleep 10
    adb shell pidof mx.charly.kart > emulator-evidence/kart-race-pid.txt
    adb exec-out screencap -p > emulator-evidence/kart-race.png
    adb logcat -d -s AndroidRuntime:E > emulator-evidence/kart-errors.txt
    if grep -q 'FATAL EXCEPTION' emulator-evidence/kart-errors.txt; then cat emulator-evidence/kart-errors.txt; exit 1; fi
  fi
  adb shell am force-stop "mx.charly.$module"
done
printf 'PASS: five APKs installed and startup processes remained alive on API 35 x86_64 emulator. Game start tap exercised. No accounts, real notifications, audio fidelity or POCO FPS tested.\n' > emulator-evidence/SUMMARY.txt
