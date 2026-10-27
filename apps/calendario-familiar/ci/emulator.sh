#!/usr/bin/env bash
set -euo pipefail
mkdir -p calendario-evidence
APK="calendario-delivery/Calendario-Familiar-Terra-v0.2-beta.apk"
adb install -r "$APK" > calendario-evidence/install.txt
grep -q 'Success' calendario-evidence/install.txt
adb logcat -c
adb shell am start -W -n mx.charly.calendario.terra/mx.charly.calendario.Day24 > calendario-evidence/start.txt
sleep 5
adb shell pidof mx.charly.calendario.terra > calendario-evidence/pid.txt
adb exec-out screencap -p > calendario-evidence/month.png
adb shell uiautomator dump /sdcard/calendar-ui.xml >/dev/null
adb pull /sdcard/calendar-ui.xml calendario-evidence/month.xml >/dev/null
grep -q 'Calendario Familiar' calendario-evidence/month.xml
grep -q 'Pintar varios días' calendario-evidence/month.xml
grep -q 'Mamá' calendario-evidence/month.xml
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
grep -q 'Semana anterior' calendario-evidence/week.xml
grep -q 'Semana siguiente' calendario-evidence/week.xml
grep -q 'Vista grande' calendario-evidence/week.xml
adb exec-out screencap -p > calendario-evidence/week.png
adb logcat -d -s AndroidRuntime:E > calendario-evidence/crashes.txt
if grep -q 'FATAL EXCEPTION' calendario-evidence/crashes.txt; then cat calendario-evidence/crashes.txt; exit 1; fi
echo 'PASS: install, cold start, two profiles and paint action visible. Not tested physically on POCO or midnight launcher cache.' > calendario-evidence/SUMMARY.txt
