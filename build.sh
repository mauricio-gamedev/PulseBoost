#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-/tmp/pulseboost-android-sdk}"
BUILD_TOOLS="$SDK_ROOT/build-tools/36.1.0"
ANDROID_JAR="$SDK_ROOT/platforms/android-36/android.jar"
OUT="$PROJECT_DIR/build"
DIST="$PROJECT_DIR/dist"
SRC="$PROJECT_DIR/app/src/main"

if [[ ! -x "$BUILD_TOOLS/aapt2" || ! -f "$ANDROID_JAR" ]]; then
  echo "Android SDK Platform 36 e Build Tools 36.1.0 não encontrados em $SDK_ROOT" >&2
  exit 1
fi

mkdir -p "$OUT" "$DIST"
find "$OUT" -mindepth 1 -delete
mkdir -p "$OUT/classes" "$OUT/generated" "$OUT/lib" "$OUT/res" "$OUT/dex" "$OUT/apk"

(cd "$PROJECT_DIR/vendor" && sha256sum -c CHECKSUMS.sha256 >/dev/null)

for dependency in shizuku-api-13.1.5 shizuku-aidl-13.1.5 shizuku-shared-13.1.5 shizuku-provider-13.1.5; do
  mkdir -p "$OUT/lib/$dependency"
  unzip -q -j "$PROJECT_DIR/vendor/$dependency.aar" classes.jar -d "$OUT/lib/$dependency"
  mv "$OUT/lib/$dependency/classes.jar" "$OUT/lib/$dependency.jar"
  rmdir "$OUT/lib/$dependency"
done

cp "$PROJECT_DIR/vendor/annotation-1.3.0.jar" "$OUT/lib/annotation-1.3.0.jar"

"$BUILD_TOOLS/aidl" \
  -p"$SDK_ROOT/platforms/android-36/framework.aidl" \
  -I"$SRC/aidl" \
  -o"$OUT/generated" \
  "$SRC/aidl/io/github/astromg01/pulseboost/shizuku/ICommandService.aidl"

"$BUILD_TOOLS/aapt2" compile \
  --dir "$SRC/res" \
  -o "$OUT/res/resources.zip"

"$BUILD_TOOLS/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$SRC/AndroidManifest.xml" \
  --java "$OUT/generated" \
  --min-sdk-version 26 \
  --target-sdk-version 36 \
  --version-code 4 \
  --version-name 0.3.1-beta \
  --auto-add-overlay \
  -o "$OUT/apk/resources.apk" \
  "$OUT/res/resources.zip"

CLASSPATH="$ANDROID_JAR:$OUT/lib/shizuku-api-13.1.5.jar:$OUT/lib/shizuku-aidl-13.1.5.jar:$OUT/lib/shizuku-shared-13.1.5.jar:$OUT/lib/shizuku-provider-13.1.5.jar:$OUT/lib/annotation-1.3.0.jar"
mapfile -t SOURCES < <(find "$SRC/java" "$OUT/generated" -name '*.java' -type f | sort)

java com.sun.tools.javac.Main \
  -source 8 \
  -target 8 \
  -encoding UTF-8 \
  -classpath "$CLASSPATH" \
  -d "$OUT/classes" \
  "${SOURCES[@]}"

java sun.tools.jar.Main --create --file "$OUT/lib/pulseboost-app.jar" -C "$OUT/classes" .

"$BUILD_TOOLS/d8" \
  --release \
  --min-api 26 \
  --lib "$ANDROID_JAR" \
  --output "$OUT/dex" \
  "$OUT/lib/pulseboost-app.jar" \
  "$OUT/lib/shizuku-api-13.1.5.jar" \
  "$OUT/lib/shizuku-aidl-13.1.5.jar" \
  "$OUT/lib/shizuku-shared-13.1.5.jar" \
  "$OUT/lib/shizuku-provider-13.1.5.jar" \
  "$OUT/lib/annotation-1.3.0.jar"

cp "$OUT/apk/resources.apk" "$OUT/apk/unsigned.apk"
(cd "$OUT/dex" && zip -q -j "$OUT/apk/unsigned.apk" classes*.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$OUT/apk/unsigned.apk" "$OUT/apk/aligned.apk"

KEYSTORE_PATH="${PULSEBOOST_KEYSTORE:-}"
KEY_ALIAS="${PULSEBOOST_KEY_ALIAS:-}"
STORE_PASSWORD="${PULSEBOOST_STORE_PASSWORD:-}"
KEY_PASSWORD="${PULSEBOOST_KEY_PASSWORD:-}"
SIGNED_APK="$DIST/PulseBoost-v0.3.1-beta.apk"
UNSIGNED_APK="$DIST/PulseBoost-v0.3.1-beta-unsigned.apk"

if [[ -n "$KEYSTORE_PATH$KEY_ALIAS$STORE_PASSWORD$KEY_PASSWORD" ]]; then
  if [[ -z "$KEYSTORE_PATH" || -z "$KEY_ALIAS" || -z "$STORE_PASSWORD" || -z "$KEY_PASSWORD" ]]; then
    echo "Defina todas as variáveis de assinatura PULSEBOOST_* solicitadas." >&2
    exit 1
  fi
  if [[ ! -f "$KEYSTORE_PATH" ]]; then
    echo "Keystore não encontrado no caminho informado." >&2
    exit 1
  fi
  "$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE_PATH" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "env:PULSEBOOST_STORE_PASSWORD" \
    --key-pass "env:PULSEBOOST_KEY_PASSWORD" \
    --out "$SIGNED_APK" \
    "$OUT/apk/aligned.apk"
  "$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$SIGNED_APK"
  "$BUILD_TOOLS/aapt" dump badging "$SIGNED_APK" | head -8
  sha256sum "$SIGNED_APK"
else
  cp "$OUT/apk/aligned.apk" "$UNSIGNED_APK"
  "$BUILD_TOOLS/aapt" dump badging "$UNSIGNED_APK" | head -8
  sha256sum "$UNSIGNED_APK"
  echo "APK não assinado criado. As credenciais oficiais nunca ficam no repositório."
fi
