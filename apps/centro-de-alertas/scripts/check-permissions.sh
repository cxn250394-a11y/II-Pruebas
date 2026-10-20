#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
for permission in INTERNET ACCESSIBILITY_SERVICE READ_SMS READ_CONTACTS READ_EXTERNAL_STORAGE QUERY_ALL_PACKAGES; do
  if grep -E "<uses-permission[^>]+android.permission.$permission" "$MANIFEST" >/dev/null; then
    echo "Prohibited permission: $permission" >&2
    exit 1
  fi
done
if ! grep -q 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE' "$MANIFEST"; then
  echo 'Missing notification listener permission contract' >&2
  exit 1
fi
if ! grep -q 'android:allowBackup="false"' "$MANIFEST"; then
  echo 'Private notification data must not be backed up' >&2
  exit 1
fi
echo 'PASS: minimal permission manifest / no internet or contacts / backup OFF'
