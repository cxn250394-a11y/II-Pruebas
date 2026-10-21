#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
kotlinc shared/alerts-core/src/main/kotlin/com/charly/alertcenter/*.kt \
  shared/alerts-core/src/test/kotlin/com/charly/alertcenter/LabCases.kt scripts/RunLabCases.kt \
  -include-runtime -d "$tmp/tests.jar"
java -jar "$tmp/tests.jar"
