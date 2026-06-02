#!/bin/bash
# Full pipeline: Record → Patch missing tags → Build APK → Swap APK
#
# Usage:
#   ./run-record-and-build.sh                    ← auto-detect device
#   ./run-record-and-build.sh 10BDCM0YJZ00043    ← specific device
#
# What it does:
#   1. Launches the POP app and starts recording your interactions
#   2. When you type  exit + ENTER  (or Ctrl+C), recording stops and YAML is saved
#   3. Reads the _missing.yaml — adds missing constants to TestTags.kt
#      and auto-applies .qaTestTag() to Composables where safe
#   4. Builds a new debug APK  (./gradlew assembleDebug in popdroid)
#   5. Renames old  src/main/resources/pop-debug.apk  →  pop-debug-1.apk
#   6. Copies the new APK into  src/main/resources/pop-debug.apk
#   7. Re-installs the new APK on the device
#
# Requirements:
#   - ADB device connected
#   - ../popdroid repo present and buildable (Java 17, Android SDK)

set -e
cd "$(dirname "$0")"

POPDROID="../popdroid"
APK_SRC="$POPDROID/pop/build/outputs/apk/debug/pop-debug.apk"
APK_DEST="src/main/resources/pop-debug.apk"
APK_BACKUP="src/main/resources/pop-debug-1.apk"
APP_PACKAGE="com.popclub.android"

# ── Step 1: Record ────────────────────────────────────────────────────────────
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Step 1 / 4  :  Record interactions                 ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
echo "  Navigate the shop flow on your device."
echo "  Type  exit + ENTER  when done (or Ctrl+C)."
echo ""

# Run recorder — allow Ctrl+C/non-zero exit without stopping the pipeline.
# The Java shutdown hook saves the YAML even on SIGINT.
set +e
./run-recorder.sh "${1:-}"
RECORDER_EXIT=$?
set -e

# Give shutdown hook 2s to finish writing the YAML
sleep 2

echo ""
echo "  Recording stopped (exit code: $RECORDER_EXIT)"

# ── Step 2: Patch missing tags ────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Step 2 / 4  :  Patch missing tags                  ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# Find latest _missing.yaml
MISSING_YAML=$(find reports/recorded -name "*_missing.yaml" 2>/dev/null \
  | sort | tail -1)

if [ -z "$MISSING_YAML" ]; then
  echo "  No missing tags found — nothing to patch."
else
  echo "  Using: $MISSING_YAML"
  echo ""
  CP="target/classes:target/test-classes:$(cat .classpath)"
  java -cp "$CP" com.popclub.ai.app.TagPatcher "$MISSING_YAML"
fi

# ── Step 3: Build APK ─────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Step 3 / 4  :  Build debug APK                     ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

if [ ! -d "$POPDROID" ]; then
  echo "  ERROR: $POPDROID not found. Skipping build."
  exit 1
fi

echo "  Running: ./gradlew assembleDebug ..."
(cd "$POPDROID" && ./gradlew assembleDebug --quiet)

if [ ! -f "$APK_SRC" ]; then
  echo "  ERROR: APK not found at $APK_SRC after build."
  exit 1
fi
echo "  Build ✓  →  $APK_SRC"

# ── Step 4: Swap APK ─────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Step 4 / 4  :  Swap APK                            ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

mkdir -p src/main/resources

# Backup old APK
if [ -f "$APK_DEST" ]; then
  mv "$APK_DEST" "$APK_BACKUP"
  echo "  Backed up old APK → $APK_BACKUP"
fi

# Copy new APK
cp "$APK_SRC" "$APK_DEST"
echo "  New APK → $APK_DEST"

# Re-install on device
UDID="${1:-$(adb devices | awk '/\tdevice$/{print $1; exit}')}"
if [ -n "$UDID" ]; then
  echo "  Installing on $UDID ..."
  adb -s "$UDID" install -r "$APK_DEST"
  echo "  Installed ✓"
fi

echo ""
echo "══════════════════════════════════════════════════════"
echo "  Done!  New APK with missing tags is live on device."
echo "  Re-run  ./run-recorder.sh  to record more tests."
echo "══════════════════════════════════════════════════════"
