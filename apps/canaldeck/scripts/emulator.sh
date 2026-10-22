#!/usr/bin/env bash
set -euo pipefail
mkdir -p canal-device
# Both debug packages stay on this runner. Only the offline APK is published.
test "$(git rev-parse HEAD)" = "$(cat canal-offline-apk/SOURCE-COMMIT.txt)"
echo "DEVICE_SOURCE_HEAD=$(git rev-parse HEAD)"
( cd canal-offline-apk && sha256sum --check SHA256SUMS.txt )
cmp canal-offline-apk/CanalDeck-v0.4.3-offline-beta.apk apps/canaldeck/app/build/outputs/apk/offline/debug/app-offline-debug.apk
for flavor in normal offline; do
  apk="apps/canaldeck/app/build/outputs/apk/$flavor/debug/app-$flavor-debug.apk"
  sha256sum "$apk"
  adb install -r "$apk"
  adb install -r "apps/canaldeck/app/build/outputs/apk/androidTest/$flavor/debug/app-$flavor-debug-androidTest.apk"
done
adb logcat -c
# Only home/identity/sandbox tests run in normal: never start a TDLib session.
adb shell am instrument -w -e class mx.charly.canal.VariantIsolationDeviceTest mx.charly.canal.test/androidx.test.runner.AndroidJUnitRunner | tee canal-device/normal-instrumentation.txt
grep -q 'OK (3 tests)' canal-device/normal-instrumentation.txt
! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' canal-device/normal-instrumentation.txt
adb exec-out run-as mx.charly.canal cat files/variant-isolation-evidence.json > canal-device/normal-variant-isolation.json
adb shell am instrument -w mx.charly.canal.offline.test/androidx.test.runner.AndroidJUnitRunner | tee canal-device/offline-instrumentation.txt
grep -q 'OK (28 tests)' canal-device/offline-instrumentation.txt
! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' canal-device/offline-instrumentation.txt
adb exec-out run-as mx.charly.canal.offline cat files/variant-isolation-evidence.json > canal-device/offline-variant-isolation.json
python3 - <<'PY_VARIANT_EVIDENCE'
import json
for flavor in ('normal', 'offline'):
    with open(f'canal-device/{flavor}-variant-isolation.json') as f:
        e=json.load(f)
    offline=flavor=='offline'
    assert e['application_id']=='mx.charly.canal'+('.offline' if offline else '')
    assert e['sibling_application_id']=='mx.charly.canal'+('' if offline else '.offline')
    assert e['offline_rehearsal_only'] is offline
    assert e['internet_permission'] is (not offline)
    assert e['compiled_real_output_enabled'] is False
    assert e['different_uid'] is True and e['different_data_dir'] is True
    assert e['sibling_private_dir_readable'] is False
    for field in ('own_preferences_round_trip','own_sqlite_round_trip','own_file_round_trip','own_vault_round_trip'):
        assert e[field] is True
    assert e['account_connected'] is False and e['sent_real_messages']==0
    print(f'VARIANT_DEVICE_{flavor.upper()}='+json.dumps(e,sort_keys=True))
PY_VARIANT_EVIDENCE
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
adb exec-out run-as mx.charly.canal.offline cat files/canaldeck-single-evidence.json > canal-device/single-evidence.json
python3 - <<'PY_SINGLE_EVIDENCE'
import json
with open('canal-device/single-evidence.json') as f:
    e=json.load(f)
assert e['compiled_real_output_enabled'] is False
assert e['offline_rehearsal_only'] is True and e['sent_real_messages']==0
assert e['queued_was_final'] is False and e['quick_ack_was_final'] is False
assert e['source_original_count']==1 and e['exact_source_original'] is True
assert e['final_original_mapping_count']==1
assert e['fake_headers']==1 and e['fake_original_requests']==1
assert e['reopened_phase']=='COMPLETE'
assert e['new_requests_after_reopen']==0 and e['duplicate_requests']==0
print('PASS single integration Fake + SQLite; one original mapped to final ACK; reopened without replay; sent_real_messages=0')
PY_SINGLE_EVIDENCE
adb exec-out run-as mx.charly.canal.offline cat files/canaldeck-demo.png > canal-device/demo.png
adb shell am start -W -n mx.charly.canal.offline/mx.charly.canal.CanalActivity | tee canal-device/launch.txt
sleep 2
adb shell dumpsys activity activities > canal-device/activity.txt
adb logcat -d -s AndroidRuntime:E > canal-device/crashes.txt
! grep -q 'FATAL EXCEPTION' canal-device/crashes.txt
grep -q 'mx.charly.canal.offline/mx.charly.canal.CanalActivity' canal-device/activity.txt
adb exec-out screencap -p > canal-device/home.png
