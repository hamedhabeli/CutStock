#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -x "$ROOT_DIR/gradlew" ]]; then
  exec "$ROOT_DIR/gradlew" "$@"
fi

GRADLE_VERSION="${GRADLE_VERSION:-8.9}"
GRADLE_HOME="${GRADLE_HOME:-$HOME/.cache/cutstock-gradle-$GRADLE_VERSION}"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"

if [[ ! -x "$GRADLE_BIN" ]]; then
  mkdir -p "$GRADLE_HOME"
  TMP_DIR="$(mktemp -d)"
  ZIP_FILE="$TMP_DIR/gradle.zip"
  curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$ZIP_FILE"
  unzip -q "$ZIP_FILE" -d "$TMP_DIR"
  rm -rf "$GRADLE_HOME"
  mv "$TMP_DIR/gradle-${GRADLE_VERSION}" "$GRADLE_HOME"
fi

exec "$GRADLE_BIN" "$@"
