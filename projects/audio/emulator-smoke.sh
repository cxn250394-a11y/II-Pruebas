#!/usr/bin/env bash
set -euo pipefail
mkdir -p audio-emulator
adb install -r audio-v02-artifacts/AudioDeck-v0.2-beta.apk > audio-emulator/install.txt
adb logcat -c
adb shell am start -W -n mx.charly.audio/.AudioActivity > audio-emulator/start.txt
sleep 4
adb shell pidof mx.charly.audio > audio-emulator/pid.txt
adb exec-out screencap -p > audio-emulator/home.png
adb shell uiautomator dump /sdcard/audio.xml >/dev/null
adb pull /sdcard/audio.xml audio-emulator/ui.xml >/dev/null
adb logcat -d -s AndroidRuntime:E > audio-emulator/crashes.txt
if grep -q 'FATAL EXCEPTION' audio-emulator/crashes.txt; then cat audio-emulator/crashes.txt; exit 1; fi
printf 'PASS: install and cold start. No DAC, streaming account or radio playback physically tested.\n' > audio-emulator/SUMMARY.txt
