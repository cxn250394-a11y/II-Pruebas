#!/usr/bin/env bash
# Offline audit helper. Never downloads dependencies or invokes Android/TDLib.
set -euo pipefail

die() { echo "ERROR: $*" >&2; exit 1; }
if [[ $# -ne 4 ]]; then
  die "Usage: bash repro-header.sh BASELINE_CHECKOUT TOOLS_DIR JAVA17_BIN EXTERNAL_EMPTY_OUTPUT_DIR"
fi
script_dir=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
candidate_root=$(git -C "$script_dir" rev-parse --show-toplevel)
baseline_root=$(CDPATH= cd -- "$1" && pwd -P)
tools_root=$(CDPATH= cd -- "$2" && pwd -P)
java_bin=$(realpath -- "$3")
test -x "$java_bin" || die "JAVA17_BIN is not executable"
expected_baseline=85c616c41d6390a294e6f7b3d3b7095867aacf8a
baseline_head=$(git -C "$baseline_root" rev-parse --verify HEAD)
candidate_head=$(git -C "$candidate_root" rev-parse --verify HEAD)
test "$baseline_head" = "$expected_baseline" || die "Baseline HEAD must be exactly $expected_baseline"
test "$candidate_head" != "$baseline_head" || die "Candidate must contain the functional correction"

mkdir -p -- "$4"
output_root=$(CDPATH= cd -- "$4" && pwd -P)
case "$output_root/" in
  "$candidate_root/"*|"$baseline_root/"*) die "Output must be outside both checkouts" ;;
esac
test -z "$(find "$output_root" -mindepth 1 -maxdepth 1 -print -quit)" || die "Output directory must be empty; prior evidence is never overwritten"
for jar in compiler stdlib reflect script coroutines annotations junit hamcrest; do
  test -f "$tools_root/$jar.jar" || die "Missing existing dependency: $jar.jar"
done
"$java_bin" -version > "$output_root/JAVA-VERSION.txt" 2>&1
grep -Eq 'version "17([."]|-)' "$output_root/JAVA-VERSION.txt" || die "Java 17 required"
"$java_bin" -cp "$tools_root/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -version > "$output_root/KOTLIN-VERSION.txt" 2>&1
grep -Fq 'kotlinc-jvm 2.4.10' "$output_root/KOTLIN-VERSION.txt" || die "Pinned Kotlin compiler 2.4.10 required"
sha256sum "$tools_root/"{compiler,stdlib,reflect,script,coroutines,annotations,junit,hamcrest}.jar > "$output_root/TOOLS-SHA256.txt"
printf 'BASELINE_HEAD=%s\nCANDIDATE_HEAD=%s\n' "$baseline_head" "$candidate_head" > "$output_root/SOURCE-HEADS.txt"

# Reject modified input files even when the checkout HEAD itself is correct.
verify_blob() {
  local checkout=$1 relative=$2 label=$3 expected actual
  expected=$(git -C "$checkout" rev-parse "HEAD:$relative")
  actual=$(git -C "$checkout" hash-object -- "$checkout/$relative")
  test "$actual" = "$expected" || die "Dirty or mismatching $label source: $relative"
  printf '%s %s %s\n' "$label" "$actual" "$relative" >> "$output_root/SOURCE-BLOBS.txt"
}
main_relative=apps/canaldeck/app/src/main/java/mx/charly/canal
test_relative=apps/canaldeck/app/src/test/java/mx/charly/canal/SingleRelayHeaderIntegrityTest.kt
common=(RelayModels AsyncRelayModels AsyncRelayValidation FakeAsyncRelay SingleRelayPlanning PickRelayPlanner PickBridgeRules PickContentRules PickAttribution TelegramMessageLinks)
sources=()
for name in "${common[@]}"; do
  relative="$main_relative/$name.kt"
  verify_blob "$baseline_root" "$relative" BASELINE
  verify_blob "$candidate_root" "$relative" CANDIDATE
  cmp -- "$baseline_root/$relative" "$candidate_root/$relative" || die "Common input changed: $relative; revise the harness scope"
  sources+=("$baseline_root/$relative")
done
for name in SingleRelayModels SingleRelayController; do
  verify_blob "$baseline_root" "$main_relative/$name.kt" BASELINE
  verify_blob "$candidate_root" "$main_relative/$name.kt" CANDIDATE
done
verify_blob "$candidate_root" "$test_relative" CANDIDATE
for name in BaselineHeaderMissingReproTest.kt repro-header.sh README.md; do
  verify_blob "$candidate_root" "apps/canaldeck/qa/inherited/$name" CANDIDATE
done
cat > "$output_root/BuildConfig.kt" <<'KOTLIN'
package mx.charly.canal
internal object BuildConfig { const val PICK_RELAY_REAL_ENABLED = false }
KOTLIN
classpath="$tools_root/stdlib.jar:$tools_root/junit.jar:$tools_root/hamcrest.jar"
compiler=("$java_bin" -cp "$tools_root/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$classpath")
probe="$script_dir/BaselineHeaderMissingReproTest.kt"
if ! "${compiler[@]}" -d "$output_root/red-classes" "$output_root/BuildConfig.kt" "${sources[@]}" \
    "$baseline_root/$main_relative/SingleRelayModels.kt" "$baseline_root/$main_relative/SingleRelayController.kt" \
    "$probe" > "$output_root/compile-red.log" 2>&1; then
  die "RED compilation failed; this is NOT a semantic reproduction"
fi
set +e
"$java_bin" -cp "$output_root/red-classes:$classpath" org.junit.runner.JUnitCore mx.charly.canal.BaselineHeaderMissingReproTest > "$output_root/junit-red.log" 2>&1
red_status=$?
set -e
test "$red_status" -eq 1 || die "Expected exactly one baseline assertion failure"
grep -Fq 'expected:<1> but was:<2>' "$output_root/junit-red.log" || die "Baseline failed for a different reason"
grep -Fq 'Tests run: 1,  Failures: 1' "$output_root/junit-red.log" || die "Unexpected baseline test count"

if ! "${compiler[@]}" -d "$output_root/green-classes" "$output_root/BuildConfig.kt" "${sources[@]}" \
    "$candidate_root/$main_relative/SingleRelayModels.kt" "$candidate_root/$main_relative/SingleRelayController.kt" \
    "$probe" "$candidate_root/$test_relative" > "$output_root/compile-green.log" 2>&1; then
  die "GREEN compilation failed; no passing result can be claimed"
fi
"$java_bin" -cp "$output_root/green-classes:$classpath" org.junit.runner.JUnitCore \
  mx.charly.canal.BaselineHeaderMissingReproTest mx.charly.canal.SingleRelayHeaderIntegrityTest > "$output_root/junit-green.log" 2>&1
grep -Fq 'OK (14 tests)' "$output_root/junit-green.log" || die "Expected the repro plus 13 candidate regression tests"
printf 'RED=1_semantic_failure\nGREEN=14_pass\nPROVIDER=synthetic_only\nREAL_OUTPUT=compiled_false\n' > "$output_root/RESULT.txt"
cat "$output_root/SOURCE-HEADS.txt" "$output_root/RESULT.txt"
