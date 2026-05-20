#!/bin/bash
# Run TagFinder standalone — no TestNG, no YAML needed.
# Usage: ./run-tag-finder.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Compile (test-compile also copies src/test/resources → target/test-classes)
mvn test-compile -q

# Build classpath (uses cached local jars — no network)
mvn dependency:build-classpath -Dmdep.outputFile=.classpath -q

CP="target/classes:target/test-classes:$(cat .classpath)"

java -cp "$CP" com.popclub.ai.app.TagFinder "$@"
