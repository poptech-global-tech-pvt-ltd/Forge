#!/usr/bin/env bash
# Records a browser session and saves it as a Playwright spec file.
# Usage: ./ai/web/codegen.sh <output-filename>
# Example: ./ai/web/codegen.sh card-onboarding

set -euo pipefail

FILENAME="${1:-recording}"
OUTPUT_DIR="$(dirname "$0")/tests"
mkdir -p "$OUTPUT_DIR"
OUTPUT_FILE="$OUTPUT_DIR/${FILENAME}.spec.js"

echo "Starting Playwright codegen → $OUTPUT_FILE"
npx playwright codegen \
  --target playwright-test \
  --output "$OUTPUT_FILE"

echo "Saved: $OUTPUT_FILE"
