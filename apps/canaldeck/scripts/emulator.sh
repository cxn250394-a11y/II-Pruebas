#!/usr/bin/env bash
set -euo pipefail
mkdir -p canal-device
adb install -r canal-apk/CanalDeck-v0.2-beta.apk
adb install -r canal-apk/tests.apk
adb logcat -c
adb shell am instrument -w mx.charly.canal.test/androidx.test.runner.AndroidJUnitRunner | tee canal-device/instrumentation.txt
grep -q 'OK (' canal-device/instrumentation.txt
! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' canal-device/instrumentation.txt
adb exec-out run-as mx.charly.canal cat files/canaldeck-demo.png > canal-device/demo.png
adb shell am start -W -n mx.charly.canal/.CanalActivity | tee canal-device/launch.txt
sleep 2
adb shell dumpsys activity activities > canal-device/activity.txt
adb logcat -d -s AndroidRuntime:E > canal-device/crashes.txt
! grep -q 'FATAL EXCEPTION' canal-device/crashes.txt
grep -q 'mx.charly.canal/.CanalActivity' canal-device/activity.txt
adb exec-out screencap -p > canal-device/home.png
