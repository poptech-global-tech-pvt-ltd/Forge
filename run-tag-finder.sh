#!/bin/bash
# Run TagFinder standalone — ADB only, no Appium needed.
# Usage:
#   ./run-tag-finder.sh                     ← auto-detect device
#   ./run-tag-finder.sh 10BDCM0YJZ00043     ← specific device

set -e
cd "$(dirname "$0")"

# Copy test resources (properties files) to target
mkdir -p target/test-classes
cp -r src/test/resources/* target/test-classes/ 2>/dev/null || true

CP="target/classes:target/test-classes:$(cat .classpath)"
java -cp "$CP" com.popclub.ai.app.TagFinder "$@"
