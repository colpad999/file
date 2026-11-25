#!/usr/bin/env bash
# Unit test mesin deteksi (RepostDetector) di JVM biasa — tanpa Android/emulator.
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
  tools/test/com/unrepost/app/DetectorTest.java
"$JAVA" -cp "$OUT" com.unrepost.app.DetectorTest
