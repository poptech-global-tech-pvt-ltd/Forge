#!/bin/bash
# Record device interactions and generate a Maestro-compatible YAML flow file.
#
# Usage:
#   ./run-maestro-recorder.sh                              ← auto-detect device
#   ./run-maestro-recorder.sh shop_add_to_cart             ← named flow
#   ./run-maestro-recorder.sh 10BDCM0YJZ00043              ← specific device
#   ./run-maestro-recorder.sh 10BDCM0YJZ00043 shop_flow    ← device + name
#
# Output: reports/maestro/<flow-name>_<timestamp>.yaml
# Run the recorded flow with:
#   maestro test reports/maestro/<flow-name>_<timestamp>/<name>.yaml
#
# Maestro output format:
#   appId: com.popclub.android
#   ---
#   - tapOn:
#       id: "home_search_bar"       ← qaTestTag (accessibilityId)
#   - tapOn:
#       text: "Add to cart"         ← text fallback (no tag yet)
#   - swipe:
#       direction: UP
#   - inputText: "yoga bar"
#
# Install Maestro: curl -Ls "https://get.maestro.mobile.dev" | bash
#
# APK: src/main/resources/pop-debug.apk
#   - Auto-installed if app is not on device
#   - App is launched before recording starts
#
# Controls while recording:
#   exit + ENTER  → stop and save YAML to reports/maestro/

set -e
cd "$(dirname "$0")"

# ── Add Maestro to PATH if installed but not on PATH ─────────────────────────
if ! command -v maestro &>/dev/null && [ -x "$HOME/.maestro/bin/maestro" ]; then
  export PATH="$HOME/.maestro/bin:$PATH"
fi

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
echo "Device  : $UDID"
echo "Format  : Maestro YAML"

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
adb -s "$UDID" shell svc power stayon true 2>/dev/null || true
trap 'adb -s "$UDID" shell svc power stayon false 2>/dev/null || true' EXIT

# ── Unlock screen & launch app ───────────────────────────────────────────────
echo "Unlocking screen..."
adb -s "$UDID" shell input keyevent 224           # wake screen
sleep 1
adb -s "$UDID" shell wm dismiss-keyguard 2>/dev/null || true
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

# ── Check Maestro is installed (optional — warn only) ────────────────────────
if ! command -v maestro &>/dev/null; then
  echo ""
  echo "  ℹ  Maestro not found. Install with:"
  echo "     curl -Ls \"https://get.maestro.mobile.dev\" | bash"
  echo "     Then add to shell:  echo 'export PATH=\"\$HOME/.maestro/bin:\$PATH\"' >> ~/.zshrc"
  echo "     Recording will still work — install Maestro before running the flow."
  echo ""
else
  MAESTRO_VER=$(maestro --version 2>/dev/null || echo "unknown")
  echo "Maestro : $MAESTRO_VER ✓"
fi

# ── Start Maestro recorder ────────────────────────────────────────────────────
mkdir -p target/test-classes
cp -r src/test/resources/* target/test-classes/ 2>/dev/null || true

CP="target/classes:target/test-classes:$(cat .classpath)"
java -cp "$CP" com.popclub.ai.app.MaestroRecorder "$UDID"
