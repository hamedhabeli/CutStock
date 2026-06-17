#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
export ANDROID_SDK_ROOT ANDROID_HOME
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

cd "$ROOT_DIR"

./scripts/gradle.sh clean :app:assembleBazaarRelease

UNSIGNED_APK="$(find app/build/outputs/apk/bazaar/release -maxdepth 1 -type f -name '*unsigned*.apk' | sort | tail -n1 || true)"
if [[ -z "${UNSIGNED_APK:-}" || ! -f "$UNSIGNED_APK" ]]; then
  UNSIGNED_APK="$(find app/build/outputs/apk/bazaar/release -maxdepth 1 -type f -name '*.apk' | sort | tail -n1)"
fi

if [[ ! -f "$UNSIGNED_APK" ]]; then
  echo "Release APK not found."
  exit 1
fi

BUILD_TOOLS_DIR="$(find "$ANDROID_SDK_ROOT/build-tools" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -n1)"
ZIPALIGN="$BUILD_TOOLS_DIR/zipalign"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"

mkdir -p .ci
KEYSTORE_PATH="${CUTSTOCK_KEYSTORE_PATH:-$ROOT_DIR/.ci/cutstock-release.jks}"
KEY_ALIAS="${CUTSTOCK_KEY_ALIAS:-cutstock-release}"
KEYSTORE_PASSWORD="${CUTSTOCK_KEYSTORE_PASSWORD:-CutStock!2026}"
KEY_PASSWORD="${CUTSTOCK_KEY_PASSWORD:-$KEYSTORE_PASSWORD}"

if [[ -n "${CUTSTOCK_KEYSTORE_BASE64:-}" ]]; then
  printf '%s' "$CUTSTOCK_KEYSTORE_BASE64" | base64 -d > "$KEYSTORE_PATH"
elif [[ ! -f "$KEYSTORE_PATH" ]]; then
  keytool -genkeypair     -v     -keystore "$KEYSTORE_PATH"     -storetype JKS     -alias "$KEY_ALIAS"     -keyalg RSA     -keysize 2048     -validity 10000     -storepass "$KEYSTORE_PASSWORD"     -keypass "$KEY_PASSWORD"     -dname "CN=CutStock, OU=Engineering, O=OpenAI, L=Tehran, ST=Tehran, C=IR"
fi

echo "alias=$KEY_ALIAS" > app/keystore-info.txt
echo "storePassword=$KEYSTORE_PASSWORD" >> app/keystore-info.txt
echo "keyPassword=$KEY_PASSWORD" >> app/keystore-info.txt
echo "keystore=$KEYSTORE_PATH" >> app/keystore-info.txt

ALIGNED_APK="$ROOT_DIR/app-release-aligned.apk"
SIGNED_APK="$ROOT_DIR/app/build/outputs/apk/bazaar/release/app-bazaar-release-signed.apk"

"$ZIPALIGN" -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"
"$APKSIGNER" sign   --ks "$KEYSTORE_PATH"   --ks-key-alias "$KEY_ALIAS"   --ks-pass pass:"$KEYSTORE_PASSWORD"   --key-pass pass:"$KEY_PASSWORD"   --out "$SIGNED_APK"   "$ALIGNED_APK"

"$APKSIGNER" verify --verbose "$SIGNED_APK"

echo "$SIGNED_APK"
