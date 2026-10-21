#!/usr/bin/env python3
"""Supplementary static contracts, not security certification or runtime tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re
root=Path(__file__).resolve().parents[1]
app=root/'apps/aural/app'
ns='{http://schemas.android.com/apk/res/android}'
m=ET.parse(app/'src/main/AndroidManifest.xml').getroot()
expected={'INTERNET','FOREGROUND_SERVICE','FOREGROUND_SERVICE_MEDIA_PLAYBACK','WAKE_LOCK'}
assert {p.get(ns+'name').removeprefix('android.permission.') for p in m.findall('uses-permission')}==expected
application=m.find('application')
assert application.get(ns+'allowBackup')=='false'
assert application.get(ns+'usesCleartextTraffic')=='false'
assert application.get(ns+'fullBackupContent')=='false'
assert application.find('service').get(ns+'foregroundServiceType')=='mediaPlayback'
assert 'applicationId = "com.charly.aural"' in (app/'build.gradle.kts').read_text()
for p in (app/'src/main').rglob('*.xml'): ET.parse(p)
for p in (app/'src/main/java').rglob('*.kt'):
    t=p.read_text()
    assert not re.search(r'(Super-Bet|RS1000|Super-Investment|Work-Work-Work|Info-para-SB|api_hash|bot_token)',t,re.I),p
    assert not re.search(r'(Log\.[diwe]\(|println\(|printStackTrace\()',t),p
assert 'Bit-perfect: NO VERIFICADO' in (root/'shared/audio-core/src/main/kotlin/com/charly/audio/AudioModel.kt').read_text()
assert 'implementation(project(":sports"))' not in (app/'build.gradle.kts').read_text()
print('PASS: isolated audio app, explicit network/FGS only, backups disabled, no private logging, sports not packaged, bit-perfect not claimed')
