#!/usr/bin/env bash
# Test: mesin deteksi (RepostDetector), mesin pembersih (CleanerEngine), dan
# logika situs di dalam APK (web-test, dengan fetch tiruan).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
TC="$ROOT/.work/tc"
JAVA="$TC/jdk/jdk4py/java-runtime/bin/java"
ECJ="$TC/minapk/package/tools/ecj-3.45.0.jar"
OUT="$ROOT/build/test"

rm -rf "$OUT" && mkdir -p "$OUT"
"$JAVA" -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
  -source 8 -target 8 -nowarn -g \
  -d "$OUT" \
  app/src/com/unrepost/app/RepostDetector.java \
  app/src/com/unrepost/app/CleanerEngine.java \
  tools/test/com/unrepost/app/DetectorTest.java \
  tools/test/com/unrepost/app/CleanerEngineTest.java
"$JAVA" -cp "$OUT" com.unrepost.app.DetectorTest
echo
"$JAVA" -cp "$OUT" com.unrepost.app.CleanerEngineTest
echo
if [ -d "$ROOT/web-test" ] && command -v node >/dev/null 2>&1; then
  (cd "$ROOT/web-test" && node run-tests.js)
fi
