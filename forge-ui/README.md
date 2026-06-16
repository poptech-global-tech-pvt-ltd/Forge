# Forge UI

A Maestro Studio–style browser UI for the Forge test runner.

Live device mirror · tap/swipe/inspect · YAML editor · real-time step runner

---

## Prerequisites

| Tool | Install |
|------|---------|
| **Node.js 18+** | https://nodejs.org  or  `brew install node` |
| **ADB** | `brew install android-platform-tools` |
| **Android device** | USB debugging enabled, connected via USB |

Verify before starting:
```bash
node -v        # v18+
adb devices    # should list your device
```

---

## First-time setup

```bash
cd forge-ui
npm install
```

---

## Start

```bash
./start.sh
```

Or manually:
```bash
cd forge-ui
node server.js
# open http://localhost:3847
```

---

## What you can do

| Feature | How |
|---------|-----|
| **Live screen** | Mirrors device automatically on load |
| **Tap** | Select 👆 Tap mode → click anywhere on the screen |
| **Swipe** | Select ↕ Swipe → click-drag on the screen |
| **Long press** | Select ✋ Hold → click |
| **Inspect element** | Select 🔍 Inspect → click any element to see its `accessibilityId`, `text`, `bounds` |
| **Scroll device** | Mouse wheel over the device screen |
| **Type text** | Type in the text box → ⌨ Send (or Enter) |
| **Nav keys** | ◀ Back · ⬤ Home · ▣ Apps |
| **Scan Tags** | 📋 Scan Tags → shows all `qaTestTag` elements on screen with their text values |
| **Insert step** | In Scan Tags panel, click `+` on any element → pick an action → inserted into editor |
| **Editor** | ✏ Editor tab → edit YAML directly · Cmd+S to save |
| **Run test** | Select test from dropdown → ▶ Run |
| **Step status** | Steps panel highlights green/red in real time as the test runs |

---

## Capturing and asserting dynamic text

```yaml
# Capture whatever text is showing in an element at runtime
- action: captureText
  element: product_list_item_0   # qaTestTag
  value: saved_title             # key to store under

# Later (e.g. in cart) — assert the same text is visible
- action: assertStoredText
  value: saved_title

# Assert it is gone (e.g. after removal)
- action: assertStoredText
  value: saved_title
  text: absent
```

---

## Port

Default: **3847**. Override with `PORT=3000 node server.js`.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `adb: command not found` | Install platform-tools: `brew install android-platform-tools` |
| Device not showing | Run `adb devices` — if empty, reconnect USB and allow debugging on device |
| Port already in use | `pkill -f "node server.js"` then restart |
| Screen not loading | Check device is connected: green dot top-left of device panel |
| `npm install` fails | Make sure Node 18+: `node -v` |
