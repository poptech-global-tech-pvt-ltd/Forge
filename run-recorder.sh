#!/bin/bash
# Record device interactions and generate a Forge YAML test file.
# Usage:
#   ./run-recorder.sh                     ← auto-detect device
#   ./run-recorder.sh 10BDCM0YJZ00043     ← specific device
#
# APK: src/main/resources/pop-qaDebug.apk
#   - Auto-installed if app is not on device
#   - App is launched before recording starts
#
# Controls while recording:
#   exit + ENTER  → stop and save YAML to reports/recorded/

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

# ── Keep screen awake while recording ────────────────────────────────────────
adb -s "$UDID" shell svc power stayon true 2>/dev/null || true           # stay on (works for both USB and wireless)
trap 'adb -s "$UDID" shell svc power stayon false 2>/dev/null || true' EXIT

# ── Unlock screen & launch app ───────────────────────────────────────────────
echo "Unlocking screen..."
adb -s "$UDID" shell input keyevent 224           # wake screen
sleep 1
adb -s "$UDID" shell wm dismiss-keyguard 2>/dev/null || true   # dismiss lock screen (no PIN)
adb -s "$UDID" shell input keyevent 82            # swipe-unlock fallback
sleep 1

echo "Launching app..."
adb -s "$UDID" shell am force-stop "$APP_PACKAGE"
sleep 1
adb -s "$UDID" shell am start -n "$APP_PACKAGE/$APP_ACTIVITY"

# Wait until our app is actually in the foreground (up to 20s)
echo "Waiting for app to reach foreground..."
for i in $(seq 1 20); do
  IN_FG=$(adb -s "$UDID" shell dumpsys activity activities 2>/dev/null \
    | grep -E "Resumed:|ResumedActivity:" | grep "com.popclub.android" | head -1)
  if [ -n "$IN_FG" ]; then
    echo "App is in foreground ✓"
    break
  fi
  sleep 1
done

if [ -z "$IN_FG" ]; then
  echo "WARNING: App may not be in foreground — check for crash or login screen"
fi

# ── Start recorder ────────────────────────────────────────────────────────────
mkdir -p target/test-classes
cp -r src/test/resources/* target/test-classes/ 2>/dev/null || true

CP="target/classes:target/test-classes:$(cat .classpath)"
java -cp "$CP" com.popclub.ai.app.Recorder "$UDID"
