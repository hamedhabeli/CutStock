#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./scripts/gradle.sh testDebugUnitTest

if command -v adb >/dev/null 2>&1; then
  if adb devices | awk 'NR>1 && $2=="device" {found=1} END {exit !found}'; then
    ./scripts/gradle.sh connectedDebugAndroidTest
  else
    echo "No connected adb device; skipping instrumentation tests."
  fi
else
  echo "adb not available; skipping instrumentation tests."
fi
