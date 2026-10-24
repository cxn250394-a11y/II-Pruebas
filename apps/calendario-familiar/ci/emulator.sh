#!/usr/bin/env bash
set -euo pipefail
mkdir -p calendario-evidence
APK="calendario-delivery/Calendario-Familiar-v0.1-beta.apk"
adb install -r "$APK" > calendario-evidence/install.txt
grep -q 'Success' calendario-evidence/install.txt
adb logcat -c
adb shell am start -W -n mx.charly.calendario/.Day24 > calendario-evidence/start.txt
sleep 5
adb shell pidof mx.charly.calendario > calendario-evidence/pid.txt
adb exec-out screencap -p > calendario-evidence/month.png
adb shell uiautomator dump /sdcard/calendar-ui.xml >/dev/null
adb pull /sdcard/calendar-ui.xml calendario-evidence/month.xml >/dev/null
grep -q 'Calendario Familiar' calendario-evidence/month.xml
grep -q 'Pintar varios días' calendario-evidence/month.xml
grep -q 'Mamá' calendario-evidence/month.xml
adb logcat -d -s AndroidRuntime:E > calendario-evidence/crashes.txt
if grep -q 'FATAL EXCEPTION' calendario-evidence/crashes.txt; then cat calendario-evidence/crashes.txt; exit 1; fi
echo 'PASS: install, cold start, two profiles and paint action visible. Not tested physically on POCO or midnight launcher cache.' > calendario-evidence/SUMMARY.txt
