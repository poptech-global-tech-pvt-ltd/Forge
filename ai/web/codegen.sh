 #!/usr/bin/env bash
# Records a browser session and saves it as a Playwright spec file.
# Usage: ./ai/web/codegen.sh <output-filename> [url]
# Example: ./ai/web/codegen.sh card-onboarding https://example.com

set -euo pipefail

FILENAME="${1:-recording}"
URL="${2:-}"
OUTPUT_DIR="$(dirname "$0")/tests"
mkdir -p "$OUTPUT_DIR"
OUTPUT_FILE="$OUTPUT_DIR/${FILENAME}.spec.js"

echo "Starting Playwright codegen → $OUTPUT_FILE"
npx playwright codegen \
  --target playwright-test \
  --output "$OUTPUT_FILE" \
  ${URL:+"$URL"}

echo "Saved: $OUTPUT_FILE"
