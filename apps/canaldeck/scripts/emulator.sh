#!/usr/bin/env bash
set -euo pipefail
mkdir -p canal-device
# The produced APK filename changes across beta versions: install exactly one signed
# artifact and verify it against the checksum produced by the build job.
shopt -s nullglob
apk_files=(canal-apk/CanalDeck-v*-beta.apk)
if [[ ${#apk_files[@]} -ne 1 ]]; then
  echo "Expected exactly one CanalDeck beta APK; found ${#apk_files[@]}" >&2
  exit 1
fi
( cd canal-apk && sha256sum --check SHA256SUMS.txt )
adb install -r "${apk_files[0]}"
adb install -r canal-apk/tests.apk
adb logcat -c
adb shell am instrument -w mx.charly.canal.offline.test/androidx.test.runner.AndroidJUnitRunner | tee canal-device/instrumentation.txt
adb exec-out run-as mx.charly.canal.offline cat files/canaldeck-relay-evidence.json > canal-device/relay-evidence.json
python3 - <<'PY_RELAY_EVIDENCE'
import json
with open('canal-device/relay-evidence.json') as f:
    e=json.load(f)
assert e['sent_real_messages']==0
assert e['compile_real_output_enabled'] is False
assert e['reopened_dedup'] is True
assert e['fake_headers']==1 and e['fake_forwards']==1
print('PASS Fake TDLib + SQLite Android; sent_real_messages=0')
PY_RELAY_EVIDENCE
adb exec-out run-as mx.charly.canal.offline cat files/canaldeck-async-evidence.json > canal-device/async-evidence.json
python3 - <<'PY_ASYNC_EVIDENCE'
import json
with open('canal-device/async-evidence.json') as f:
    e=json.load(f)
assert e['sent_real_messages']==0
assert e['compiled_real_output_enabled'] is False
assert e['intent_preceded_output'] is True
assert e['provider_unknown_album_held'] is True and e['fake_manifest_only'] is True
assert e['queued_was_final'] is False and e['quick_ack_was_final'] is False
assert e['partial_final_was_complete'] is False
assert e['fake_headers']==1 and e['fake_media_requests']==1
assert e['expected_photos']==e['final_photo_set_size']==2
assert e['reopened_phase']=='COMPLETE' and e['new_requests_after_reopen']==0
print('PASS asynchronous Fake + SQLite; exact two-photo finals; reopen without dispatch; sent_real_messages=0')
PY_ASYNC_EVIDENCE
grep -q 'OK (' canal-device/instrumentation.txt
! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' canal-device/instrumentation.txt
adb exec-out run-as mx.charly.canal.offline cat files/canaldeck-demo.png > canal-device/demo.png
adb shell am start -W -n mx.charly.canal.offline/mx.charly.canal.CanalActivity | tee canal-device/launch.txt
sleep 2
adb shell dumpsys activity activities > canal-device/activity.txt
adb logcat -d -s AndroidRuntime:E > canal-device/crashes.txt
! grep -q 'FATAL EXCEPTION' canal-device/crashes.txt
grep -q 'mx.charly.canal.offline/mx.charly.canal.CanalActivity' canal-device/activity.txt
adb exec-out screencap -p > canal-device/home.png
