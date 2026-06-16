#!/bin/bash
# Run TagFinder standalone — ADB only, no Appium needed.
# Usage:
#   ./run-tag-finder.sh                     ← auto-detect device
#   ./run-tag-finder.sh 10BDCM0YJZ00043     ← specific device
#
# APK: src/main/resources/pop-qaDebug.apk
#   - Auto-installed if app is not on device

set -e
cd "$(dirname "$0")"

APP_PACKAGE="com.popclub.android"
APP_ACTIVITY="com.popclub.android.LauncherFresh"
APK_PATH="src/main/resources/pop-qaDebug.apk"

# ── Detect device ─────────────────────────────────────────────────────────────
UDID="${1:-}"
if [ -z "$UDID" ]; then
  UDID=$(adb devices | awk '/\tdevice$/{print $1; exit}')
  if [ -z "$UDID" ]; then
    echo "ERROR: No ADB device connected."
    exit 1
  fi
fi
echo "Device : $UDID"

# ── Install if not present ────────────────────────────────────────────────────
INSTALLED=$(adb -s "$UDID" shell pm list packages "$APP_PACKAGE" 2>/dev/null)
if [ -z "$INSTALLED" ]; then
  if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: App not installed and APK not found at $APK_PATH"
    echo "Place the debug APK at: src/main/resources/pop-qaDebug.apk"
    exit 1
  fi
  echo "Installing $APK_PATH ..."
  adb -s "$UDID" install -r "$APK_PATH"
  echo "Installed ✓"
else
  echo "App already installed ✓"
fi

# ── Copy test resources and run ───────────────────────────────────────────────
mkdir -p target/test-classes
cp -r src/test/resources/* target/test-classes/ 2>/dev/null || true

CP="target/classes:target/test-classes:$(cat .classpath)"
java -cp "$CP" com.popclub.ai.app.TagFinder "$UDID"
