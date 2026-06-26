#!/bin/bash
# Build and install ForgeDriver companion APK onto the connected device
# Run once — then ForgeDriverManager.start() handles the rest.
#
# Usage:
#   ./forge-driver/install.sh
#   ./forge-driver/install.sh <device-serial>

set -e

DEVICE=${1:-""}
ADB_FLAGS=${DEVICE:+"-s $DEVICE"}

echo "🔨 Building ForgeDriver APK..."
cd "$(dirname "$0")"
./gradlew assembleDebugAndroidTest

APK=$(find . -name "*.apk" -path "*/androidTest/*" | head -1)
if [ -z "$APK" ]; then
  echo "❌ APK not found — build may have failed"
  exit 1
fi

echo "📦 Installing $APK..."
adb $ADB_FLAGS install -r -t "$APK"

echo "✅ ForgeDriver installed"
echo ""
echo "Now run your tests normally — ForgeDriverManager.start() will launch it automatically."
