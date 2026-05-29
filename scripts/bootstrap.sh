#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
export ANDROID_SDK_ROOT ANDROID_HOME

mkdir -p "$ANDROID_SDK_ROOT"

SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  TMP_DIR="$(mktemp -d)"
  ZIP_FILE="$TMP_DIR/cmdline-tools.zip"
  curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o "$ZIP_FILE"
  mkdir -p "$TMP_DIR/extract"
  unzip -q "$ZIP_FILE" -d "$TMP_DIR/extract"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mv "$TMP_DIR/extract/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
fi

export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

yes | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null
sdkmanager --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0" \
  "emulator" \
  "ndk;27.0.12077973" \
  "cmake;3.22.1" >/dev/null

cat > "$ROOT_DIR/local.properties" <<EOF
sdk.dir=$ANDROID_SDK_ROOT
EOF

chmod +x "$ROOT_DIR/scripts"/*.sh 2>/dev/null || true
"$ROOT_DIR/scripts/gradle.sh" --version >/dev/null
