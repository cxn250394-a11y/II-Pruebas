#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
manifest = root / "app/src/main/AndroidManifest.xml"
xml = ET.parse(manifest)
ns = "{http://schemas.android.com/apk/res/android}"
application = xml.getroot().find("application")
assert application is not None
assert application.attrib.get(ns + "allowBackup") == "false"
assert application.attrib.get(ns + "dataExtractionRules") == "@xml/data_extraction_rules"
service = application.find("service")
assert service is not None
assert service.attrib.get(ns + "permission") == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
assert service.attrib.get(ns + "exported") == "true"
assert service.find("intent-filter/action").attrib.get(ns + "name") == "android.service.notification.NotificationListenerService"
assert not xml.getroot().findall("uses-permission")
ET.parse(root / "app/src/main/res/xml/data_extraction_rules.xml")
ET.parse(root / "app/src/main/res/drawable/ic_app.xml")
ET.parse(root / "app/src/main/res/values/styles.xml")
ET.parse(root / "app/src/main/res/values/strings.xml")
files = list((root / "app/src/main/java").rglob("*.kt"))
assert len(files) >= 5
print(f"PASS: Android manifest and resources parsed, {len(files)} Kotlin source files, 0 uses-permission")
