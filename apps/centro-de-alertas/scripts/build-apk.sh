#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if ! command -v gradle >/dev/null; then
  echo 'BLOCKED: Gradle 8.9 is not installed; use Android Studio or the GitHub Actions workflow.' >&2
  exit 2
fi
if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]] && [[ ! -e local.properties ]]; then
  echo 'BLOCKED: Android SDK location is missing (ANDROID_HOME / local.properties).' >&2
  exit 3
fi
bash scripts/check-permissions.sh
python3 scripts/verify-structure.py
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
sha256sum "$APK"
echo "APK AVAILABLE: $APK"
