#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./scripts/gradle.sh :app:assembleDebug
python3 -m http.server 8080 --directory app/build/outputs/apk/debug
