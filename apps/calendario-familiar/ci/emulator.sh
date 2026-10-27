#!/usr/bin/env bash
set -euo pipefail
mkdir -p calendario-evidence
# Preserve evidence when a foreground assertion fails; a live PID alone does not
# prove that the app is the visible activity.
diagnose_failure() {
  local status=$?
  if (( status != 0 )); then
    adb shell dumpsys activity activities > calendario-evidence/activity-on-failure.txt 2>&1 || true
    adb logcat -d -v threadtime > calendario-evidence/logcat-on-failure.txt 2>&1 || true
    adb exec-out screencap -p > calendario-evidence/screen-on-failure.png 2>/dev/null || true
  fi
}
trap diagnose_failure EXIT
APK="calendario-delivery/Calendario-Familiar-Terra-v0.2-beta.apk"
adb install -r "$APK" > calendario-evidence/install.txt
grep -q 'Success' calendario-evidence/install.txt
# This script is destructive only to the disposable emulator's test package.
# Never clear app data on a physical device or an unrecognized target.
SERIAL=$(adb get-serialno | tr -d '\r')
QEMU=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
[[ "$SERIAL" == emulator-* && "$QEMU" == 1 ]] || { echo 'Refusing to clear data outside an Android emulator' >&2; exit 1; }
adb shell pm clear mx.charly.calendario.terra > calendario-evidence/clear-disposable-package.txt
grep -q '^Success' calendario-evidence/clear-disposable-package.txt
adb logcat -c
# Resolve the currently enabled launcher alias; its day need not be 24.
LAUNCHER=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER mx.charly.calendario.terra | tr -d '\r' | tail -1)
[[ "$LAUNCHER" =~ ^mx\.charly\.calendario\.terra/mx\.charly\.calendario\.Day[0-9][0-9]$ ]]
printf '%s\n' "$LAUNCHER" > calendario-evidence/launcher.txt
adb shell am start -W -n "$LAUNCHER" > calendario-evidence/start.txt
sleep 5
adb shell pidof mx.charly.calendario.terra > calendario-evidence/pid.txt
adb shell uiautomator dump /sdcard/calendar-ui.xml >/dev/null
adb pull /sdcard/calendar-ui.xml calendario-evidence/month.xml >/dev/null
python3 apps/calendario-familiar/ci/ui_guard.py calendario-evidence/month.xml
grep -q 'Calendario Familiar' calendario-evidence/month.xml
grep -q 'Pintar varios días' calendario-evidence/month.xml
grep -q 'Mamá' calendario-evidence/month.xml
grep -q 'Ver semana' calendario-evidence/month.xml
adb exec-out screencap -p > calendario-evidence/month.png
# Force a configuration recreation while the launcher alias remains in use.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 4
adb shell uiautomator dump /sdcard/calendar-rotated.xml >/dev/null
adb pull /sdcard/calendar-rotated.xml calendario-evidence/rotated.xml >/dev/null
python3 apps/calendario-familiar/ci/ui_guard.py calendario-evidence/rotated.xml
grep -q 'mx.charly.calendario.terra' calendario-evidence/rotated.xml
adb exec-out screencap -p > calendario-evidence/rotated.png
python3 - <<'PY'
import struct
from pathlib import Path
header=Path('calendario-evidence/rotated.png').read_bytes()[:24]
assert header[:8] == b'\x89PNG\r\n\x1a\n'
width,height=struct.unpack('>II',header[16:24])
assert width>height, f'No landscape rotation: {width}x{height}'
PY
adb shell settings put system user_rotation 0
sleep 4
adb shell uiautomator dump /sdcard/calendar-ui.xml >/dev/null
adb pull /sdcard/calendar-ui.xml calendario-evidence/month.xml >/dev/null
python3 apps/calendario-familiar/ci/ui_guard.py calendario-evidence/month.xml
grep -q 'Ver semana' calendario-evidence/month.xml
# Locate the actual native button using UI Automator rather than guessing screen coordinates.
python3 - <<'PY' > calendario-evidence/week-tap.txt
import xml.etree.ElementTree as ET
import re
root=ET.parse("calendario-evidence/month.xml").getroot()
nodes=[n for n in root.iter() if "Ver semana" in n.get("text","")]
assert len(nodes)==1, len(nodes)
coords=list(map(int,re.findall(r"\d+",nodes[0].get("bounds",""))))
assert len(coords)==4,coords
print((coords[0]+coords[2])//2,(coords[1]+coords[3])//2)
PY
read -r TAP_X TAP_Y < calendario-evidence/week-tap.txt
adb shell input tap "$TAP_X" "$TAP_Y"
sleep 3
adb shell uiautomator dump /sdcard/week-ui.xml >/dev/null
adb pull /sdcard/week-ui.xml calendario-evidence/week.xml >/dev/null
python3 apps/calendario-familiar/ci/ui_guard.py calendario-evidence/week.xml
grep -q 'Semana anterior' calendario-evidence/week.xml
grep -q 'Semana siguiente' calendario-evidence/week.xml
grep -q 'Vista grande' calendario-evidence/week.xml
adb exec-out screencap -p > calendario-evidence/week.png
# Build the fixture through native UI, force-stop/restart, and verify UI + SQLite.
python3 apps/calendario-familiar/ci/real_fixture.py
adb logcat -d -s AndroidRuntime:E > calendario-evidence/crashes.txt
if grep -q 'FATAL EXCEPTION' calendario-evidence/crashes.txt; then cat calendario-evidence/crashes.txt; exit 1; fi
echo 'PASS: install, cold start, rotation, native synthetic appointment and painted day, week/day display, and persistence after force-stop/restart. Not tested physically on POCO or with TalkBack; date icon is disabled.' > calendario-evidence/SUMMARY.txt
if [[ -f calendario-evidence/launcher-anr-events.txt ]]; then
  echo 'ENVIRONMENT: Pixel Launcher ANR observed; Wait was selected. See launcher-anr evidence.' >> calendario-evidence/SUMMARY.txt
fi
