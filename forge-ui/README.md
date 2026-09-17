# Forge UI

A Maestro Studio–style browser control panel for the Forge test automation framework.

Live device mirror · tap/swipe/inspect · YAML editor · recorder · AI chat · element registry

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Node.js | 18+ | `brew install node` |
| ADB | latest | `brew install android-platform-tools` |
| Android device | — | USB debugging enabled, connected via USB |
| Claude CLI | latest | Required for Chat tab only |

```bash
node -v       # v18+
adb devices   # must list your device
```

---

## Setup & Start

```bash
cd forge-ui
npm install
node server.js
```

Open **http://localhost:3847**

Override port: `PORT=4000 node server.js`

---

## Layout

```
┌─────────────────┬──────────────────────────────────────────┐
│  Device Panel   │  Steps / Editor / Flows / Network /      │
│  (left)         │  Record / Elements / Chat  (right tabs)  │
├─────────────────┴──────────────────────────────────────────┤
│  Log Panel (bottom, resizable)                             │
└────────────────────────────────────────────────────────────┘
```

---

## Test Picker (top toolbar)

| Control | Description |
|---------|-------------|
| 🔍 Search box | Search all tests by name, folder, or tag — dropdown shows results |
| ✏ | Rename the selected test file |
| ⎘ | Duplicate the selected test file |
| 🗑 | Delete the selected test file |
| ▶ Run | Run the selected test |
| ▶ Folder | Batch-run all tests in the current folder sequentially |
| 📊 | Run history — last 60 runs with pass/fail badge, test name, timestamp |

---

## Device Panel

### Screen

- Live screenshot refreshed every 2 seconds (faster during test run)
- Click to interact based on current mode

### Interaction modes

| Button | Mode | Gesture |
|--------|------|---------|
| 👆 Tap | Tap | Click anywhere on screen image |
| ↕ Swipe | Swipe | Click-drag on screen image |
| ✋ Hold | Long press | Click |
| 🔍 Inspect | Inspect | Click any element to inspect it |

### Inspector popup

When you tap in Inspect mode, a popup shows:

| Field | Description |
|-------|-------------|
| **element key** | Green = registered in elements YAML ✅, Yellow = unregistered ⚠️ |
| **accessibilityId** | Raw testTag value |
| **resource-id** | (XML view apps only) |
| **text** | Visible text |
| **class** | Compose/View class name |
| **bounds** | Screen coordinates |
| **clickable / scrollable** | Boolean attributes |

Buttons in inspector:
- **✅ Copy element key** — copies the registered key, ready to paste into YAML
- **Copy accessibilityId** — copies raw tag value
- **Copy text** — copies visible text
- **➕ Add to elements.yaml** — shown when element has no registered key; switches to Elements tab with locator pre-filled

### Device controls

| Control | Description |
|---------|-------------|
| Text box + ⌨ Send | Type text and send to focused field on device |
| ◀ Back | Press Back key |
| ⬤ Home | Press Home key |
| ▣ Apps | Open recents |
| 📋 Scan Tags | Lists all qaTestTag values visible on current screen |
| 🏷 Tag Sync | Pushes element keys to device |

---

## ▶ Steps Tab

Live execution view while a test runs.

| Feature | Description |
|---------|-------------|
| Step highlights | Running = blue pulse, Passed = green ✅, Failed = red ❌ |
| Run from step | Hover any step → click ▶ to run from that point |
| Drag-reorder | ⠿ handle on each step — drag to reorder |
| Duplicate step | ⎘ button on hover — duplicates the step in the YAML |
| Stats bar | Total / ✓ Passed / ✗ Failed counters |
| Result badge | PASSED / FAILED shown after run completes |
| 🔧 Fix with Claude | Appears on failure — one click sends failing steps to Chat tab |

---

## ✏ Editor Tab

Full CodeMirror YAML editor.

| Feature | Description |
|---------|-------------|
| Syntax highlighting | Dracula theme, YAML mode |
| Autocomplete | Ctrl+Space — suggests action names and element keys |
| Run step at cursor | **Ctrl+Enter** — executes the step under cursor live on device |
| Element pick mode | Click `👆 Pick` banner → tap device → element key inserted at cursor |
| Inline error bar | YAML parse errors shown immediately |
| Save | **Cmd+S** or Save button |
| New test | `+ New` button — prompts for folder + filename (`.yaml` added automatically) |

### Creating a new test

1. Click `+ New`
2. Select an existing folder or type a new folder name
3. Enter test filename (without `.yaml`)
4. Template is inserted — edit and save

---

## ♻ Flows Tab

Manage reusable step sequences.

```yaml
# Call a flow from any test:
- action: call
  flow: login_with_otp
```

| Control | Description |
|---------|-------------|
| Flow list | Left sidebar — click to open a flow |
| `+ New Flow` | Creates a new flow file |
| Save / Delete | Save or remove the current flow |
| Hint bar | Shows the exact YAML syntax to call this flow |

---

## 🌐 Network Tab

Captures all OkHttp API calls made during a test run.

| Feature | Description |
|---------|-------------|
| Request list | Method · URL · status code · duration |
| Detail pane | Full request headers + body, response headers + body |
| Badge | Tab shows count of new calls since last viewed |
| Clear | Wipes the captured calls list |

---

## ⏺ Record Tab

Tap on the device to record test steps automatically.

### Controls

| Button | Action |
|--------|--------|
| ⏺ Start Recording | Begins recording session |
| ⏸ Pause | Pauses recording (state saved — survives page refresh) |
| ▶ Resume | Resumes paused recording |
| 👁 Auto-read | Toggle — when ON, taps record instantly without showing action picker |
| ⏹ Stop | Stops recording, shows YAML |
| 💾 Save as Test | Prompts for folder + filename, saves YAML |
| Clear | Discards all recorded steps |

### While recording

- Tap the device → action picker appears (tap / long press / enter text / scroll / back / etc.)
- With Auto-read ON → taps are recorded immediately as `tap` steps
- Swipe/drag on device screen → recorded as `swipe` step automatically
- Each step appears as a chip in the step feed
- **Undo** (Ctrl+Z) / **Redo** (Ctrl+Y) — up to 50 levels
- **Drag** the ⠿ handle on any step chip to reorder
- **✕** on any step chip to delete it

### Live YAML preview

The YAML panel updates in real-time as you record. After stopping:
- **Copy** — copies YAML to clipboard
- **Open in Editor** — loads the YAML into the Editor tab

### Session persistence

Recording state (steps, pause/resume) is saved to `localStorage`. If you accidentally refresh the page mid-recording, the state is restored with a warning banner.

---

## 🗂 Elements Tab

Browse and manage the entire element registry across all YAML files.

### Toolbar

| Control | Description |
|---------|-------------|
| 🔍 Search | Live filter by key name or locator value |
| `+ Add Element` | Opens inline add form |
| 📊 Usage Report | Scans all tests — shows missing and unused elements |
| 🔍 Untagged Check | Dumps live device screen — shows locators with no registered key |

### File pills

Click any pill to filter the table to that file:
`All` · `billpay.yaml` · `common.yaml` · `credit_card.yaml` · `home.yaml` · `login.yaml` · `profile.yaml` · `rewards.yaml` · `shop.yaml` · `upi.yaml`

### Table columns

| Column | Description |
|--------|-------------|
| KEY | Element key (purple, monospace) — use this in test YAML |
| ANDROID LOCATOR | accessibilityId value for Android |
| IOS LOCATOR | accessibilityId value for iOS (— if same as Android) |
| FILE | Which YAML file this element is defined in |
| ACTIONS | ✏ Edit · 🗑 Delete |

### Add / Edit form

Fields:
- **Key name** — snake_case only, e.g. `home_cart_icon`
- **Android locator value** — the accessibilityId / testTag string
- **iOS locator value** — optional, leave blank if same as Android
- **File** — which elements YAML to save into

### 📊 Usage Report

Scans all test YAML files and elements files and reports:

- **Missing** (red badge) — element keys used in tests but not defined in any elements file
  - Each missing key shows which test files use it
  - `+ Add` button opens the Add form pre-filled with the key name
- **Unused** (yellow badge) — element keys defined in elements files but never used in any test
  - `🗑 Delete` button removes the key immediately

### 🔍 Untagged Check

Runs a live `adb uiautomator dump` and compares screen elements against the registry:

- Shows locator values present on screen that have **no registered element key**
- `+ Add key` button opens the Add form with the locator pre-filled
- Badge shows: `N untagged / M total` locators on current screen

---

## 💬 Chat Tab

AI-powered test generation and diagnosis using Claude.

### Context chips

Automatically attached to every message:
- **Active test** chip — shows the test currently selected in the picker
- **Screen** chip — shown when screen elements have been captured

### Input bar buttons

| Button | Description |
|--------|-------------|
| 📋 From screen | Captures live device screen elements and attaches as context |
| Send ↵ | Send message (also Enter key) |
| ✕ Stop | Cancel mid-stream response |

### Auto-context

When you send a message, the following are automatically included:
- Currently selected test YAML
- Last run result (PASSED / FAILED)
- Details of any failed steps (action, element, error message)
- Screen elements (if captured via 📋 From screen)

### 🔧 Fix with Claude

After a test failure, the **🔧 Fix with Claude** button appears in the Steps tab.
Click it to automatically switch to Chat and send the failure context in one action.

### YAML card

When Claude generates a test, a card appears with:
- **Open in Editor** — loads the YAML into the Editor tab
- **💾 Save…** — prompts for folder + filename and saves immediately

### Tips

```
"Generate a test for adding a product to wishlist"
"Why is step 4 failing? element: pdp_add_to_cart_button not found"
"Rewrite this test to use the new checkout flow"
"What element key should I use for the search bar on the home screen?"
```

---

## Log Panel

- Live output during test runs
- Lines tagged to the current executing step
- 🔍 Search log — filter by keyword
- Clear button

Resize by dragging the handle between the tabs panel and the log.

---

## Keyboard Shortcuts

| Shortcut | Where | Action |
|----------|-------|--------|
| `Ctrl+Enter` | Editor | Run step at cursor on device |
| `Ctrl+Space` | Editor | Trigger autocomplete |
| `Cmd+S` | Editor | Save file |
| `Ctrl+Z` | Recorder | Undo last recorded step |
| `Ctrl+Y` | Recorder | Redo |
| `Enter` | Chat | Send message |
| `Shift+Enter` | Chat | New line in message |

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Screen not showing | Check green dot top-left of device panel; run `adb devices` |
| Port 3847 in use | `pkill -f "node server.js"` then restart |
| Chat tab not responding | Ensure `claude` CLI is in PATH: `which claude` |
| Elements tab empty | Device doesn't need to be connected — check `src/test/resources/testdata/elements/` has YAML files |
| Untagged Check fails | Device must be connected and app open on screen |
| `npm install` fails | Check `node -v` is 18+ |
| Steps not updating during run | Ensure WebSocket is connected — refresh the page |
