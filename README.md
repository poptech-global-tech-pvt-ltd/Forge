# Forge — Mobile Test Automation Framework

Forge is a YAML-driven mobile test automation framework for the POP Android app.
Tests are written as plain YAML files and executed via Appium + TestNG on a real device.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Forge — Core Framework](#forge--core-framework)
4. [Forge UI](#forge-ui)
5. [Forge MCP](#forge-mcp)
6. [Claude Desktop Integration](#claude-desktop-integration)
7. [Project Structure](#project-structure)
8. [Writing Tests](#writing-tests)
9. [Element Keys](#element-keys)
10. [Troubleshooting](#troubleshooting)

---

## Prerequisites

Install **all** of these before setting up any Forge component.

### System tools

| Tool | Version | Install |
|------|---------|---------|
| Java (JDK) | 17+ | `brew install openjdk@17` |
| Maven | 3.8+ | `brew install maven` |
| Node.js | 20+ | `brew install nvm && nvm install 20` |
| Android SDK / ADB | latest | Android Studio → SDK Tools → Android SDK Platform-Tools |
| Appium | 3.x | `npm install -g appium` |
| Appium UiAutomator2 driver | latest | `appium driver install uiautomator2` |
| Claude CLI | latest | [claude.ai/download](https://claude.ai/download) |

### Verify setup

```bash
java -version        # must show 17+
mvn -version         # must show 3.8+
node --version       # must show 20+
adb devices          # must list your device with status "device"
appium -v            # must show 3.x
claude -v            # must show claude version
```

### Android device

1. Enable **Developer Options** (Settings → About Phone → tap Build Number 7 times)
2. Enable **USB Debugging** (Developer Options → USB Debugging)
3. Connect via USB and run `adb devices` — confirm status is `device` (not `unauthorized`)
4. Install POP app APK:
   ```bash
   adb install -r <path-to-pop.apk>
   ```

---

## Quick Start

**Option A — one-shot setup script (recommended)**

```bash
git clone <repo-url>
cd Forge
chmod +x setup.sh
./setup.sh          # installs everything + opens Forge UI at http://localhost:3847
```

Other modes:

```bash
./setup.sh --check  # verify all dependencies are installed (no install, no start)
./setup.sh --start  # skip install, just start Forge UI (deps already installed)
```

**Option B — manual**

```bash
# 1 — Clone
git clone <repo-url>
cd Forge

# 2 — Install Java deps
mvn install -DskipTests

# 3 — Install UI deps
cd forge-ui && npm install && cd ..

# 4 — Install MCP deps
cd forge-mcp && npm install && cd ..

# 5 — Start Forge UI
cd forge-ui && node server.js
# open http://localhost:3847
```

---

## Forge — Core Framework

### Setup

```bash
cd Forge
mvn install -DskipTests   # downloads all Java dependencies
```

### Running tests

```bash
# Run a single test
mvn test -DtestFile=shop/ts_add_to_cart.yaml

# Run multiple tests
mvn test -DtestFile=shop/ts_add_to_cart.yaml,shop/ts_checkout.yaml

# Run on a specific device (when multiple connected)
mvn test -DtestFile=shop/ts_add_to_cart.yaml -DdeviceSerial=10BDCM0YJZ00043

# Run without output noise
mvn test -DtestFile=shop/ts_add_to_cart.yaml --no-transfer-progress
```

### Maven dependencies (`pom.xml`)

| Dependency | Purpose |
|------------|---------|
| TestNG 7.11 | Test runner and suite management |
| Appium Java Client 10 | Device interaction via UiAutomator2 |
| Selenium Java 4.x | WebDriver base |
| Jackson YAML 2.17 | Parsing YAML test files |
| REST Assured 5.4 | API test actions (`fetchApi`, `callService`) |
| Playwright 1.52 | Web automation steps |
| Javalin 6.4 | Internal HTTP server for reports |
| Lombok 1.18 | Boilerplate reduction |

---

## Forge UI

A browser-based control panel for running, editing, recording, and AI-generating tests.

See [forge-ui/README.md](forge-ui/README.md) for full documentation.

### Setup

```bash
cd forge-ui
npm install
```

### Start

```bash
node server.js
# open http://localhost:3847
```

Override port: `PORT=4000 node server.js`

### Node dependencies

| Package | Purpose |
|---------|---------|
| `express` | HTTP server |
| `ws` | WebSocket for live log streaming |
| `js-yaml` | YAML parsing and validation |

### Features

| Tab | What it does |
|-----|-------------|
| Steps | Live step-by-step execution view, pass/fail highlights, run from any step, drag-reorder, duplicate step |
| Editor | CodeMirror YAML editor — create, edit, save tests. Ctrl+Enter runs step at cursor live on device |
| Flows | Manage reusable flow files callable via `action: call` |
| Network | Live HTTP traffic capture during test runs (request + response headers and body) |
| Record | Tap-to-record test steps from device. Auto-read mode, pause/resume, undo/redo, live YAML preview |
| Elements | Browse, add, edit, delete element keys. Usage report (missing/unused). Untagged screen check |
| Chat | AI test generation via Claude with live device screen context |

### Test Picker toolbar

| Control | Purpose |
|---------|---------|
| Search box | Search across all tests by name, folder or tag |
| Rename | Rename test file |
| Duplicate | Duplicate test file |
| Delete | Delete test file |
| Run | Run the selected test |
| Run Folder | Batch-run all tests in the selected folder |
| History | Run history — last 60 runs with pass/fail badges |

### Device panel

| Control | Purpose |
|---------|---------|
| Tap | Tap mode — click anywhere on the screen image to tap that point on device |
| Swipe | Click-drag on screen image to swipe |
| Hold | Long press |
| Inspect | Click any element to see its key, accessibilityId, text, bounds, clickable state |
| Text box + Send | Type text and send to the focused field on device |
| Back / Home / Apps | Hardware key shortcuts |
| Scan Tags | Shows all qaTestTag elements on screen |
| Tag Sync | Pushes element keys to device |

---

## Forge MCP

An MCP server that gives Claude real-time visibility into the Android device and the ability to write, run, and heal tests autonomously.

See [forge-mcp/README.md](forge-mcp/README.md) for full documentation of all 19 tools.

### Setup

```bash
cd forge-mcp
npm install
```

### Node dependencies

| Package | Purpose |
|---------|---------|
| `@modelcontextprotocol/sdk` | MCP server SDK |
| `xml2js` | Parse uiautomator XML hierarchy |
| `zod` | Tool parameter validation |
| `yaml` | YAML parsing/serialising for element operations |

### Test the server

```bash
node forge-mcp/index.js
# Starts and waits silently — no output = healthy
```

### Register with Claude Code

Add to `.claude/settings.json` (already done in this repo):

```json
{
  "mcpServers": {
    "forge": {
      "command": "node",
      "args": ["/Users/deepa/repos/Forge/forge-mcp/index.js"],
      "cwd": "/Users/deepa/repos/Forge/forge-mcp"
    }
  }
}
```

Restart Claude Code after any changes to `index.js`.

### All 19 tools

| Tool | Category | What it does |
|------|----------|-------------|
| `forge_device_screenshot` | Device | Live screenshot of device screen |
| `forge_device_tap` | Device | Tap by element key / tag / text / x,y |
| `forge_device_type` | Device | Type text into focused field |
| `forge_device_key` | Device | Press back / home / enter / search / tab / delete |
| `forge_device_swipe` | Device | Swipe up / down / left / right |
| `forge_device_launch` | Device | Cold-launch the POP app |
| `forge_get_hierarchy` | Screen | Dump live screen — maps every element to best Forge locator (4 tiers) |
| `forge_list_tests` | Tests | List all YAML test files by folder |
| `forge_read_test` | Tests | Read full YAML of a test file |
| `forge_validate_test` | Tests | Check all `element:` keys exist before running |
| `forge_save_test` | Tests | Save generated YAML to correct path |
| `forge_rename_test` | Tests | Rename a test file |
| `forge_duplicate_test` | Tests | Duplicate a test as a starting point |
| `forge_run_test` | Tests | Run test via Maven, returns per-step pass/fail |
| `forge_heal_step` | Healing | Fuzzy-match broken element on live screen, return fixed YAML step |
| `forge_list_elements` | Elements | List all registered elements (filter by file or search) |
| `forge_save_element` | Elements | Add or update an element in elements YAML |
| `forge_delete_element` | Elements | Remove an element key from elements YAML |
| `forge_check_elements` | Elements | Report missing + unused elements across all tests |

---

## Claude Desktop Integration

To use Forge MCP tools directly in Claude desktop app:

1. Open `~/Library/Application Support/Claude/claude_desktop_config.json`
2. Add under `mcpServers`:

```json
"forge": {
  "command": "/Users/deepa/.nvm/versions/node/v20.20.2/bin/node",
  "args": ["/Users/deepa/repos/Forge/forge-mcp/index.js"],
  "cwd": "/Users/deepa/repos/Forge/forge-mcp"
}
```

3. Quit and reopen Claude desktop.

Usage examples:
- "Test the add to cart flow" — scans screen, writes test, runs it, heals failures
- "Fix the failing step in ts_checkout.yaml" — reads test, heals step, saves, re-runs
- "What elements are on the home screen?" — runs `forge_get_hierarchy`
- "Clean up unused elements" — runs `forge_check_elements`, deletes unused

---

## Project Structure

```
Forge/
├── src/
│   ├── main/java/com/popclub/
│   │   ├── android/actions/        # Action implementations (tap, scroll, enterText...)
│   │   ├── core/                   # WaitUtil, TestContext, LocatorUtil
│   │   ├── heal/                   # Self-healing engine
│   │   ├── ai/                     # ForgeRecorder, tag analysis
│   │   ├── model/                  # TestCase, Step, Locator models
│   │   └── runner/                 # TestExecutor, TestRunnerTest
│   └── test/
│       ├── java/com/popclub/
│       │   ├── androidTests/       # YAML test scripts (by feature)
│       │   │   ├── shop/
│       │   │   ├── login/
│       │   │   ├── home/
│       │   │   ├── profile/
│       │   │   ├── rewards/
│       │   │   ├── upi/
│       │   │   ├── billpay/
│       │   │   └── recorded/
│       │   └── androidFlows/       # Reusable flow YAML files
│       └── resources/
│           ├── testdata/elements/  # Element key -> accessibilityId mappings
│           │   ├── shop.yaml
│           │   ├── common.yaml
│           │   ├── home.yaml
│           │   ├── login.yaml
│           │   ├── profile.yaml
│           │   ├── rewards.yaml
│           │   ├── upi.yaml
│           │   ├── billpay.yaml
│           │   └── credit_card.yaml
│           ├── testdata/           # Test fixtures and data files
│           └── suites/             # TestNG XML suite files
├── forge-ui/                       # Browser UI (Node.js + Express)
│   ├── server.js                   # Express + WebSocket server
│   ├── public/index.html           # Single-page app
│   └── README.md
├── forge-mcp/                      # MCP server for Claude integration
│   ├── index.js                    # 19 MCP tools
│   ├── package.json
│   └── README.md
├── reports/                        # Screenshots, failure images, run reports
├── scripts/                        # Utility scripts
├── setup.sh                        # One-shot dependency installer + launcher
├── .claude/
│   ├── settings.json               # MCP registration for Claude Code
│   └── skills/                     # Claude Code slash command skills
└── pom.xml
```

---

## Writing Tests

Tests live in `src/test/java/com/popclub/androidTests/<feature>/`.

### Minimal test template

```yaml
testName: Verify add to cart
platform: android
noReset: true
loginRequired: true

features:
  - common
  - shop

tags:
  - smoke
  - shop

retry: 1

steps:
  - action: launchApp

  - action: loginIfNeeded
    value: "9999900000"   # phone number
    text: "560102"        # OTP

  - action: waitFor
    element: home_tab

  - action: tap
    element: common_shop_tab

  - action: waitFor
    element: shop_search_bar

  - action: tap
    element: shop_search_bar

  - action: enterText
    value: "shoes"

  - action: pressKey
    value: search

  - action: waitFor
    element: product_list_item_0

  - action: tap
    element: product_list_item_0

  - action: waitFor
    element: pdp_add_to_cart_button

  - action: tap
    element: pdp_add_to_cart_button

  - action: verifyElement
    element: cart_toast_message
```

### All actions

| Action | Key params | Description |
|--------|-----------|-------------|
| `launchApp` | — | Cold-start the app. Always first step |
| `loginIfNeeded` | `value` phone, `text` OTP | Login if not already logged in |
| `waitFor` | `element` | Wait until element is visible |
| `tap` | `element` / `text` / `x,y` | Tap an element |
| `tapIfPresent` | `element` | Tap only if visible (dialogs, banners) |
| `longPress` | `element` | Long press |
| `enterText` | `element`, `value` | Clear field and type |
| `clearText` | `element` | Clear field |
| `pressKey` | `value` | back / home / enter / search / tab |
| `verifyElement` | `element` | Assert element is visible |
| `waitForAbsence` | `element` | Assert element disappears |
| `scrollTo` | `element` | Scroll until element is visible |
| `scrollDown` / `scrollUp` | — | Scroll the screen |
| `swipe` | `value` | up / down / left / right |
| `pullToRefresh` | — | Pull-to-refresh gesture |
| `captureText` | `element`, `value` | Save element's text to a variable |
| `assertText` | `element`, `text` | Assert element contains text |
| `assertStoredText` | `value` | Assert previously captured text is visible |
| `tapByText` | `text` | Tap element containing exact text |
| `tapUntilGone` | `element` | Keep tapping until element disappears |
| `ifPresent` | `element`, `steps[]` | Conditional block — runs steps only if visible |
| `ifNotPresent` | `element`, `steps[]` | Conditional block — runs steps only if absent |
| `call` | `flow` | Execute a reusable flow |
| `screenshot` | — | Save screenshot to reports/ |
| `scanTags` | — | Print all qaTestTags visible on screen |
| `sleep` | `value` ms | Explicit wait (use sparingly) |
| `fetchApi` | `url`, `method`, `body` | Make an API call |
| `launchUrl` | `value` | Open a deep link |

---

## Element Keys

Element keys live in `src/test/resources/testdata/elements/<feature>.yaml`.

### Format

```yaml
shop_add_to_cart_button:
  android:
    - type: accessibilityId
      value: shop_add_to_cart_button
  ios:
    - type: accessibilityId
      value: shop_add_to_cart_button
```

### Rules

- Always use the **key name** (left side) in test `steps:` — never the raw `accessibilityId` value
- Keys must be `snake_case` alphanumeric only
- Each key lives in exactly one feature file
- Declare which files your test uses under `features:` in the test header
- Add new keys via Forge UI Elements tab, or directly edit the YAML file

### Finding element keys

```bash
# Option 1 — Forge UI Inspector
# Click Inspect on device panel -> click element -> key shown in inspector popup

# Option 2 — Scan Tags button
# Forge UI -> Scan Tags -> lists all qaTestTags on current screen

# Option 3 — Claude MCP
# "What elements are available on the cart screen?"
# -> forge_get_hierarchy returns all element keys ready to use
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `adb: command not found` | `brew install android-platform-tools` |
| Device not listed in `adb devices` | Reconnect USB — allow debugging prompt on device |
| `INSTALL_FAILED` | `adb install -r` (replace existing) or uninstall first |
| UiAutomator2 not connected | Forge MCP auto-recovers. Manual fix: force-stop `io.appium.uiautomator2.server` on device |
| Port 3847 already in use | `pkill -f "node server.js"` then restart |
| Screen not loading in Forge UI | Check green dot top-left of device panel; run `adb devices` |
| `npm install` fails | Check Node version: `node --version` must be 20+ |
| Maven build fails | Check Java version: `java -version` must be 17+ |
| Element not found in test | Run `forge_validate_test` (MCP) or Usage Report (Elements tab) |
| Claude chat not working | Ensure `claude` is in PATH: `which claude` |
| `setup.sh` check fails | Run `./setup.sh --check` to see which dependency is missing |
| `PKIX path building failed` during Maven | Zscaler SSL interception — the JDK doesn't trust the Zscaler CA. Export the Zscaler cert from Keychain Access → System Roots, then: `sudo keytool -import -trustcacerts -alias zscaler -file ~/zscaler.pem -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit -noprompt` |
