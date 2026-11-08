#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

if [ -z "${JAVA_HOME:-}" ]; then
    for candidate in "$HOME"/.local/share/tecladolibre-tools/jdk*/Contents/Home; do
        if [ -x "$candidate/bin/java" ]; then export JAVA_HOME="$candidate"; break; fi
    done
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"
if [ ! -x "$BUILD_TOOLS/apksigner" ]; then
    echo 'Instala Android SDK Platform 35 y Build Tools 35.0.0; configura ANDROID_HOME.' >&2
    exit 1
fi

./gradlew --console=plain testDebugUnitTest lintDebug assembleRelease

# Private, persistent signing identity. Never include this directory in the project or APK.
SIGNING_DIR="${TECLADO_SIGNING_DIR:-$HOME/.local/share/tecladolibre/signing}"
mkdir -p "$SIGNING_DIR"
chmod 700 "$SIGNING_DIR"
if [ ! -f "$SIGNING_DIR/tecladolibre.jks" ]; then
    if [ ! -f "$SIGNING_DIR/password" ]; then
        (umask 077; openssl rand -hex 32 > "$SIGNING_DIR/password")
    fi
    keytool -genkeypair -keystore "$SIGNING_DIR/tecladolibre.jks" \
        -storetype JKS -alias tecladolibre -keyalg RSA -keysize 3072 -validity 10000 \
        -storepass:file "$SIGNING_DIR/password" -keypass:file "$SIGNING_DIR/password" \
        -dname 'CN=Teclado Libre, OU=Personal, O=Gustavo, C=PE' > /dev/null 2>&1
    chmod 600 "$SIGNING_DIR/tecladolibre.jks"
fi
mkdir -p entregables
"$BUILD_TOOLS/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk entregables/aligned.apk
"$BUILD_TOOLS/apksigner" sign --ks "$SIGNING_DIR/tecladolibre.jks" \
    --ks-key-alias tecladolibre --ks-pass "file:$SIGNING_DIR/password" \
    --out entregables/TecladoLibre.apk entregables/aligned.apk
rm entregables/aligned.apk
"$BUILD_TOOLS/apksigner" verify --verbose entregables/TecladoLibre.apk
if command -v shasum > /dev/null; then
    (cd entregables && shasum -a 256 TecladoLibre.apk > SHA256.txt)
else
    (cd entregables && sha256sum TecladoLibre.apk > SHA256.txt)
fi
echo "APK: $PROJECT_DIR/entregables/TecladoLibre.apk"
