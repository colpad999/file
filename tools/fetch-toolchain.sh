#!/usr/bin/env bash
# Mengunduh toolchain build APK ke .work/tc (tidak masuk Git, ada di .gitignore).
# Sumber: pypi (jdk4py, aapt2), npm (minapk: ecj, d8, apksigner, android.jar).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DL="$ROOT/.work/dl"
TC="$ROOT/.work/tc"
mkdir -p "$DL" "$TC"

echo "==> Mengunduh paket toolchain (sekitar 90 MB)"
pip3 download --no-deps -d "$DL" jdk4py aapt2 \
  || python3 -m pip download --break-system-packages --no-deps -d "$DL" jdk4py aapt2
(cd "$DL" && npm pack @drxiaozhi/minapk --silent)

echo "==> Mengekstrak"
unzip -q -o "$DL"/jdk4py-*.whl -d "$TC/jdk"
unzip -q -o "$DL"/aapt2-*.whl -d "$TC/aapt2w"
mkdir -p "$TC/minapk" && tar xzf "$DL"/drxiaozhi-minapk-*.tgz -C "$TC/minapk"

chmod +x "$TC/aapt2w/aapt2/bin/Linux/aapt2"
chmod +x "$TC/jdk/jdk4py/java-runtime/bin/"* 2>/dev/null || true

echo "==> Selesai. Cek:"
"$TC/jdk/jdk4py/java-runtime/bin/java" -version
"$TC/aapt2w/aapt2/bin/Linux/aapt2" version
