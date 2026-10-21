#!/usr/bin/env python3
"""Bounded static checks. This is not a security audit or a substitute for device tests."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parent
ns = '{http://schemas.android.com/apk/res/android}'
expected = {
 'odds': set(), 'stats': {'INTERNET'}, 'kart': set(),
 'audio': {'INTERNET','FOREGROUND_SERVICE','FOREGROUND_SERVICE_MEDIA_PLAYBACK','WAKE_LOCK','MODIFY_AUDIO_SETTINGS'},
 'canal': {'INTERNET','POST_NOTIFICATIONS','FOREGROUND_SERVICE','FOREGROUND_SERVICE_REMOTE_MESSAGING'},
}
for app, permissions in expected.items():
 m = ET.parse(root / app / 'src/main/AndroidManifest.xml').getroot()
 got = {e.get(ns+'name','').replace('android.permission.','') for e in m.findall('uses-permission')}
 assert got == permissions, (app, got)
 assert m.find('application').get(ns+'allowBackup') == 'false', app
for f in root.rglob('*'):
 if not f.is_file() or 'build' in f.parts or '.gradle' in f.parts: continue
 assert f.suffix.lower() not in {'.jks','.keystore','.session','.apk'}, f
 if f.suffix == '.xml': ET.parse(f)
 if f.suffix == '.kt':
  text=f.read_text()
  assert not re.search(r'(https?://[^\s\"]*(?:rs1000)|/var/lib/rs1000|Super-Bet-3\.0|Super-Investment-1\.0)', text, re.I), f
print('PASS: five distinct app modules; least-privilege manifest budgets; backups disabled; no signing/session files; no external project integration')
