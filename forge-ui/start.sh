#!/bin/bash
# Start Forge UI — mirrors your Android device in the browser
set -e
cd "$(dirname "$0")"

# Auto-install dependencies on first run (or after a fresh clone)
if [ ! -d "node_modules" ]; then
  echo "📦 Installing dependencies..."
  npm install
fi

echo ""
echo "  ⚡ Forge UI  →  http://localhost:3847"
echo ""

node server.js &
PID=$!
sleep 1
open http://localhost:3847 2>/dev/null || true   # macOS — silently skip on Linux

echo "  Running (PID $PID) — press Ctrl+C to stop"
wait $PID
