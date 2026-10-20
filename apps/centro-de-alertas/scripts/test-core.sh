#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(mktemp --suffix=.jar)"
trap 'rm -f "$JAR"' EXIT
kotlinc \
  "$ROOT/app/src/main/java/com/charly/alertcenter/AlertRules.kt" \
  "$ROOT/tests/AlertRulesCliTest.kt" \
  -include-runtime -d "$JAR"
java -jar "$JAR"
