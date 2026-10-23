from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parent.parent
activity = root / 'app/src/main/java/mx/charly/canal/CanalActivity.kt'
assert 'AlertDialog.Builder(' not in activity.read_text(), 'Use the independently protected dialog factory'
ns = '{http://schemas.android.com/apk/res/android}'
m = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
a = m.find('application')
assert a.get(ns+'allowBackup') == 'false'
assert a.get(ns+'usesCleartextTraffic') == 'false'
allowed = {'android.permission.INTERNET', 'android.permission.POST_NOTIFICATIONS', 'android.permission.FOREGROUND_SERVICE', 'android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING'}
assert {p.get(ns+'name') for p in m.findall('uses-permission')} == allowed
assert len(a.findall('service')) == 1 and a.find('service').get(ns+'exported') == 'false'
for path in [root / 'app/src/main/java', root / 'ui/src/main/java']:
 for f in path.rglob('*.kt'):
  s = f.read_text()
  assert not any(x in s for x in ['Super-Bet', 'RS1000', 'bot_token', 'BIND_NOTIFICATION_LISTENER_SERVICE', 'READ_CONTACTS']), f
print('PASS: notification reader absent; no contacts; protected dialogs; private foreground service; backups disabled; isolated code')

# Personal build is intentionally a different Android identity and contains no Pick$ relay.
settings=(root / 'build.gradle').read_text()
assert "applicationId 'mx.charly.canal.personal.v032'" in settings
assert "versionName '0.3.2-personal-beta'" in settings
for file in (root / 'app/src/main/java').rglob('*.kt'):
    content=file.read_text()
    assert 'PICK_RELAY_REAL_ENABLED' not in content and 'PickBridgeRules' not in content, file
