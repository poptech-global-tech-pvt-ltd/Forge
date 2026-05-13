#!/usr/bin/env bash
# Converts a Playwright codegen spec into enterprise Page Object Model classes.
# Usage: ./ai/web/pom-convert.sh <spec-file>
# Example: ./ai/web/pom-convert.sh ai/web/tests/card-onboarding.spec.js

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROMPT_TEMPLATE="$SCRIPT_DIR/prompts/codegen-to-pom.md"
SPEC_FILE="${1:-}"

if [[ -z "$SPEC_FILE" ]]; then
  echo "Usage: $0 <path-to-spec-file>"
  exit 1
fi

if [[ ! -f "$SPEC_FILE" ]]; then
  echo "Error: spec file not found: $SPEC_FILE"
  exit 1
fi

echo "Converting: $SPEC_FILE"

PROMPT=$(python3 - <<EOF
template = open("$PROMPT_TEMPLATE").read()
spec     = open("$SPEC_FILE").read()
print(template.replace("\$SPEC_CONTENT", spec))
EOF
)

echo "$PROMPT" | claude
