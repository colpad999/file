#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Unrepost — skrip build APK tanpa Android Studio / Gradle.
#
# Pipeline: aapt2 (resource) -> ecj (Java) -> d8 (dex) -> apksigner
# Toolchain diunduh dari npm/pypi/GitHub ke .work/tc (lihat tools/fetch-toolchain.sh).
#
#   tools/build-apk.sh            # APK debug (ditandatangani keystore debug)
#   tools/build-apk.sh --release  # APK release + keystore permanen di keys/
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TC="$ROOT/.work/tc"
JAVA="$TC/jdk/jdk4py/java-runtime/bin/java"
AAPT2="$TC/aapt2w/aapt2/bin/Linux/aapt2"
ECJ="$TC/minapk/package/tools/ecj-3.45.0.jar"
D8="$TC/minapk/package/tools/d8.jar"
ANDROID_JAR="$TC/minapk/package/tools/android.jar"
APKSIGNER="$TC/minapk/package/tools/apksigner.jar"
DEBUG_KS="$TC/minapk/package/tools/debug.keystore"
ZIPALIGN="$TC/zipalign"

MODE="debug"
if [[ "${1:-}" == "--release" ]]; then MODE="release"; fi

APP_NAME="Unrepost"
OUT="$ROOT/build"
DIST="$ROOT/dist"
SRC="$ROOT/app/src"
RES="$ROOT/app/res"
MANIFEST="$ROOT/app/AndroidManifest.xml"
MIN_SDK=24
TARGET_SDK=34

banner() { printf '\n\033[36m==> %s\033[0m\n' "$*"; }

[[ -x "$JAVA" ]] || { echo "Toolchain belum ada. Jalankan tools/fetch-toolchain.sh"; exit 1; }

banner "Membersihkan build/"
rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/classes" "$OUT/dex" "$OUT/gen" "$DIST"

banner "1/5 aapt2 compile (resource)"
"$AAPT2" compile --dir "$RES" -o "$OUT/compiled/res.zip"

banner "2/5 aapt2 link (manifest + resource -> base.apk)"
"$AAPT2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$MANIFEST" \
  "$OUT/compiled/res.zip" \
  --java "$OUT/gen" \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --auto-add-overlay

banner "3/5 ecj (Java -> .class)"
mapfile -t SOURCES < <(find "$SRC" "$OUT/gen" -name '*.java' ! -name 'DetectorTest.java' | sort)
# ecj di JVM 21+: pakai -source/-target 8 + bootclasspath android.jar (hindari
# bentrok modul java.base dengan class android). android.jar belum punya
# java.lang.invoke.LambdaMetafactory, jadi stub kecil ditambahkan ke bootclasspath
# agar lambda (d8 desugar) bisa dikompilasi.
mkdir -p "$OUT/lambda-stub" "$OUT/lambda-stub-classes"
cp -r "$ROOT/tools/lambda-stub/java" "$OUT/lambda-stub/"
"$JAVA" -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
  -source 8 -target 8 -nowarn -proceedOnError \
  -d "$OUT/lambda-stub-classes" \
  $(find "$OUT/lambda-stub" -name '*.java') > /dev/null 2>&1
(cd "$OUT/lambda-stub-classes" && zip -q -r "$OUT/lambda-stub.jar" .)
"$JAVA" -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
  -source 8 -target 8 -nowarn \
  -bootclasspath "$ANDROID_JAR:$OUT/lambda-stub.jar" \
  -d "$OUT/classes" \
  "${SOURCES[@]}" > "$OUT/ecj.log" 2>&1 || true
if grep -q "ERROR in" "$OUT/ecj.log"; then
  echo "Kompilasi gagal:"; grep -A3 "ERROR in" "$OUT/ecj.log" | head -40; exit 1
fi
[[ -f "$OUT/classes/com/unrepost/app/MainActivity.class" ]] || { echo "Kompilasi gagal"; exit 1; }
echo "Kompilasi OK ($(find "$OUT/classes" -name '*.class' | wc -l) kelas)"

banner "4/5 d8 (class -> dex)"
mapfile -t CLASSES < <(find "$OUT/classes" -name '*.class')
"$JAVA" -Xmx512m -cp "$D8" com.android.tools.r8.D8 \
  --min-api "$MIN_SDK" \
  --lib "$ANDROID_JAR" \
  --output "$OUT/dex" \
  "${CLASSES[@]}"
(cd "$OUT/dex" && zip -q -X "$OUT/base.apk" classes*.dex)

banner "5/5 zipalign + apksigner"
if [[ -x "$ZIPALIGN" ]]; then
  "$ZIPALIGN" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"
else
  cp "$OUT/base.apk" "$OUT/aligned.apk"
fi

if [[ "$MODE" == "release" ]]; then
  KS="$ROOT/keys/unrepost-release.jks"
  if [[ ! -f "$KS" ]]; then
    banner "Membuat release keystore (SIMPAN dan cadangkan!)"
    mkdir -p "$ROOT/keys"
    "$TC/jdk/jdk4py/java-runtime/bin/keytool" -genkeypair -v \
      -keystore "$KS" -alias unrepost -keyalg RSA -keysize 2048 -validity 10000 \
      -storepass unrepost2026 -keypass unrepost2026 \
      -dname "CN=Unrepost, OU=Mobile, O=Unrepost, L=Jombang, ST=East Java, C=ID" >/dev/null
  fi
  APK="$DIST/unrepost-$MODE.apk"
  "$JAVA" -jar "$APKSIGNER" sign \
    --ks "$KS" --ks-key-alias unrepost \
    --ks-pass pass:unrepost2026 --key-pass pass:unrepost2026 \
    --min-sdk-version "$MIN_SDK" --v1-signing-enabled true --v2-signing-enabled true \
    --out "$APK" "$OUT/aligned.apk"
else
  APK="$DIST/unrepost-debug.apk"
  "$JAVA" -jar "$APKSIGNER" sign \
    --ks "$DEBUG_KS" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --min-sdk-version "$MIN_SDK" --v1-signing-enabled true --v2-signing-enabled true \
    --out "$APK" "$OUT/aligned.apk"
fi

banner "Verifikasi tanda tangan"
"$JAVA" -jar "$APKSIGNER" verify --verbose "$APK" | sed -n '1,8p'

banner "Selesai"
ls -lh "$APK"
echo
echo "APK siap dipasang. Pindahkan ke HP lalu buka (izinkan 'instal dari sumber tak dikenal')."
