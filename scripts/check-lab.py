#!/usr/bin/env python3
"""Static contracts complement, not replace, runtime tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re
root = Path(__file__).resolve().parent.parent
app = root / 'apps/centro-alertas-lab/app'
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(app / 'src/main/AndroidManifest.xml').getroot()
assert not manifest.findall('uses-permission'), 'This LAB may not request runtime/network permissions'
a = manifest.find('application')
assert a is not None and a.get(ns+'allowBackup') == 'false'
assert a.get(ns+'usesCleartextTraffic') == 'false'
assert len(a.findall('service')) == 1
service = a.find('service')
assert service.get(ns+'permission') == 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE'
assert service.get(ns+'name') == '.LabListener'
build = (app / 'build.gradle.kts').read_text()
assert 'applicationId = "com.charly.alertaslab"' in build
for p in (app / 'src/main/res').rglob('*.xml'):
    ET.parse(p)
for p in (app / 'src/main/java').rglob('*.kt'):
    text = p.read_text()
    assert not re.search(r'(https?://|java\.net\.|okhttp|Retrofit|api_hash|api_id|bot_token|cancelNotification\(|getActiveNotifications\()', text), p
    assert not re.search(r'(Super-Bet|RS1000|Super-Investment|Work-Work-Work|Info-para-SB)', text, re.I), p
assert 'FOREIGN KEY' not in (app / 'src/main/java/com/charly/alertaslab/LabStore.kt').read_text()
print('PASS: separate app ID; no runtime permissions, network code, notification cancellation or external-project integration; backup disabled')
