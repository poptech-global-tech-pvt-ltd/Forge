# Forge MCP

An MCP (Model Context Protocol) server that gives Claude real-time access to the Android device, test files, and element registry — enabling autonomous test writing, running, and healing.

**Version**: 1.5.0 · **Tools**: 19

---

## Prerequisites

- Node.js 20+
- ADB installed and device connected
- Forge project at `/Users/deepa/repos/Forge/`
- Claude Code or Claude Desktop

---

## Setup

```bash
cd forge-mcp
npm install
```

### Dependencies

| Package | Purpose |
|---------|---------|
| `@modelcontextprotocol/sdk` | MCP server SDK |
| `xml2js` | Parse uiautomator XML hierarchy |
| `zod` | Tool parameter validation |
| `yaml` | YAML parsing/serialising for element operations |

---

## Registration

### Claude Code

Add to `.claude/settings.json` in the Forge repo root (already present):

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

Restart Claude Code after changes to `index.js`.

Verify tools are loaded:
```
/mcp
```
Should list 19 forge tools.

### Claude Desktop

Open `~/Library/Application Support/Claude/claude_desktop_config.json` and add:

```json
{
  "mcpServers": {
    "forge": {
      "command": "/Users/deepa/.nvm/versions/node/v20.20.2/bin/node",
      "args": ["/Users/deepa/repos/Forge/forge-mcp/index.js"],
      "cwd": "/Users/deepa/repos/Forge/forge-mcp"
    }
  }
}
```

Quit and reopen Claude Desktop.

---

## All 19 Tools

---

### 📱 Device Tools

---

#### `forge_device_screenshot`

Takes a live screenshot of the connected device and returns it as an image.

**Use when**: Confirming which screen the app is on before scanning or writing a test.

```
Use forge_device_screenshot
```

---

#### `forge_device_tap`

Taps an element on the device. Resolves element → coordinates automatically.

**Parameters**:

| Param | When to use |
|-------|-------------|
| `element` | Registered element key from elements.yaml — preferred |
| `tag` | Raw accessibilityId / testTag string |
| `text` | Visible text on screen |
| `x`, `y` | Absolute coordinates — last resort |

```
Use forge_device_tap with element: home_search_bar
Use forge_device_tap with text: "Add to Cart"
Use forge_device_tap with x: 540, y: 1200
```

Returns the YAML step that was executed.

---

#### `forge_device_type`

Types text into the currently focused input field.

**Parameters**: `text` (required), `element` (optional — tap to focus first)

```
Use forge_device_type with text: "test@example.com"
Use forge_device_type with element: login_email_input, text: "hello@pop.com"
```

---

#### `forge_device_key`

Presses a hardware or software key.

**Parameters**: `key` — one of: `back` · `home` · `search` · `enter` · `tab` · `delete`

```
Use forge_device_key with key: back
Use forge_device_key with key: enter
```

---

#### `forge_device_swipe`

Swipes on the screen in a direction.

**Parameters**: `direction` (`up` / `down` / `left` / `right`), `distance` (pixels, default 600)

```
Use forge_device_swipe with direction: up
Use forge_device_swipe with direction: left, distance: 800
```

---

#### `forge_device_launch`

Cold-launches the POP app (`com.popclub.android`).

```
Use forge_device_launch
```

---

### 🔍 Screen Analysis

---

#### `forge_get_hierarchy`

**Most important tool.** Dumps the live Android screen via `adb uiautomator dump`, parses every element, and maps each to the best Forge YAML locator across 4 tiers.

Tier 2 elements are **auto-registered** into the appropriate `elements/*.yaml` file.

**Parameters**: `udid` (optional — auto-detected)

**Output tiers**:

| Tier | Condition | YAML syntax | Action required |
|------|-----------|-------------|-----------------|
| ✅ **Tier 1** | Registered testTag | `element: cart_checkout_button` | None — ready to use |
| ⚠️ **Tier 2** | Unregistered testTag | `element: new_key` | Auto-registered — available next run |
| 💬 **Tier 3** | Visible text only, no tag | `text: "Add to Cart"` | Fragile — ask dev to add testTag |
| 📍 **Tier 4** | No tag, no text | `x: 540  y: 1200` | Coordinate only — ask dev to add `Modifier.testTag()` |

```
Use forge_get_hierarchy to see all elements on the current screen
```

**Always call this before writing a test.** It tells you exactly which element keys to use.

---

### 📋 Test Management

---

#### `forge_list_tests`

Lists all YAML test files organised by folder.

**Parameters**: `feature` (optional) — `shop` / `login` / `home` / `profile` / `rewards` / `upi` / `billpay`

```
Use forge_list_tests
Use forge_list_tests with feature: shop
```

**Output**:
```
📁 shop/
   ts_add_to_cart.yaml
   ts_checkout.yaml
   ts_pdp_verify_price.yaml
📁 login/
   ts_otp_login.yaml
```

---

#### `forge_read_test`

Reads the full YAML content of an existing test file.

**Always call this before editing a test** — never reconstruct steps from memory.

**Parameters**: `filename`, `subfolder` (optional)

```
Use forge_read_test with filename: ts_checkout.yaml, subfolder: shop
```

---

#### `forge_validate_test`

Checks every `element:` key in a test exists in the elements files. Run before `forge_run_test` to catch missing elements early.

**Parameters**: `filename`, `subfolder` (optional)

```
Use forge_validate_test with filename: ts_checkout.yaml
```

**Output**:
```
📋 Validating: ts_checkout.yaml
   12 elements OK  |  1 missing

❌ Missing elements:
   Step 4: element: cart_checkout_btn_v2

Fix: add to elements/<feature>.yaml or change to  locator: <tag>
```

---

#### `forge_save_test`

Saves a YAML test to the correct location under `androidTests/<subfolder>/<filename>.yaml`.

**Parameters**: `filename`, `content` (full YAML string), `subfolder` (optional)

```
Use forge_save_test with filename: ts_add_to_wishlist.yaml, subfolder: shop, content: |
  testName: Add to wishlist
  ...
```

---

#### `forge_rename_test`

Renames a test file. Both paths relative to `androidTests/`.

**Parameters**: `from`, `to` (`.yaml` added automatically if missing)

```
Use forge_rename_test with from: shop/ts_add_to_cart, to: shop/ts_add_to_cart_v2
```

Fails if destination already exists.

---

#### `forge_duplicate_test`

Duplicates a test file as a starting point for a new variant. Returns the content of the new file.

**Parameters**: `source`, `dest`

```
Use forge_duplicate_test with source: shop/ts_checkout.yaml, dest: shop/ts_checkout_guest.yaml
```

---

#### `forge_run_test`

Runs a Forge YAML test via `mvn test` and returns structured per-step pass/fail results.

**Parameters**: `testFile`, `device` (optional — auto-detected)

Auto-recovers from UiAutomator2 disconnection: kills the server, waits 3s, retries once.

```
Use forge_run_test with testFile: ts_add_to_cart.yaml
```

**Output**:
```
❌ TEST FAILED

  ✅ Step 1: tap home_search_bar
  ✅ Step 2: enterText "shoes"
  ❌ Step 3: tap product_add_to_cart_button
       Error: Element not found: product_add_to_cart_button

── FAILED STEPS ──
  stepIndex: 3
  action:    tap product_add_to_cart_button
  element:   product_add_to_cart_button  ← likely broken locator
  error:     Element not found
```

Pass the `stepIndex`, `action`, and `element` directly to `forge_heal_step`.

---

#### `forge_heal_step`

Self-heals a failing step. Dumps the live screen, fuzzy-matches the broken element name against all visible elements, auto-registers the best match, and returns the corrected YAML step.

**Parameters**:

| Param | Required | Description |
|-------|----------|-------------|
| `brokenElement` | ✅ | The element key or locator that failed |
| `action` | ✅ | The action that was running |
| `stepYaml` | optional | Full YAML of the failing step for context |
| `udid` | optional | Auto-detected |

```
Use forge_heal_step with brokenElement: product_add_to_cart_button, action: tap
```

**Output**:
```
🔧 Healing: tap → product_add_to_cart_button
   Screen: pdp_screen   Device: 10BDCM0YJZ00043

── Best matches ──
Score: 87%
  ✅ REGISTERED → element: pdp_add_to_cart_button  (feature: shop)
     visible text: "Add to Cart"

── Fixed YAML step ──
- action: tap
  element: pdp_add_to_cart_button

⚡ Confidence: 87%  (tag match)
```

---

### 🗂 Element Repository

---

#### `forge_list_elements`

Lists all registered elements with their locators, grouped by file.

**Parameters**:

| Param | Description |
|-------|-------------|
| `file` | Filter to one file, e.g. `shop.yaml` |
| `search` | Keyword to find in key names, e.g. `cart` |

```
Use forge_list_elements
Use forge_list_elements with file: shop.yaml
Use forge_list_elements with search: checkout
```

**Output**:
```
📄 shop.yaml  (247 elements)
──────────────────────────────────────────────────
  cart_checkout_button
    android: cart_checkout_button
  cart_item_quantity_input
    android: cart_item_quantity_input
    ios:     cart_item_quantity_input
```

---

#### `forge_save_element`

Adds or updates an element in an elements YAML file. Keys are sorted alphabetically after saving.

**Parameters**:

| Param | Required | Description |
|-------|----------|-------------|
| `file` | ✅ | Target file, e.g. `shop.yaml` |
| `key` | ✅ | Snake_case element key |
| `androidValue` | ✅ | accessibilityId / testTag value |
| `androidType` | optional | default: `accessibilityId` |
| `iosValue` | optional | iOS locator (omit if same as Android) |
| `iosType` | optional | default: `accessibilityId` |

```
Use forge_save_element with file: shop.yaml, key: cart_promo_banner, androidValue: cart_promo_banner
```

---

#### `forge_delete_element`

Removes an element key from an elements YAML file.

Run `forge_check_elements` first to confirm the key is unused before deleting.

**Parameters**: `file`, `key`

```
Use forge_delete_element with file: common.yaml, key: old_unused_button
```

---

#### `forge_check_elements`

Full health report across all tests and element files.

**No parameters required.**

Reports:
- **Missing** — `element:` keys used in tests but not defined anywhere → need to add
- **Unused** — keys defined in elements but never referenced in any test → candidates to clean up

```
Use forge_check_elements
```

**Output**:
```
📊 Element Health Report
   Defined: 1312   Used: 89   Missing: 27   Unused: 1243

❌ MISSING (27):
  add_address_make_default_checkbox
    ← profile/add_address.yaml
  cart_item_decrease_button_${cart_index}
    ← shop/ts_shop_cart_price_verify.yaml

⚠️  UNUSED (1243):
  activate_lite_add_money_bottom_section_row  (common.yaml)
  ...
```

---

## Common Workflows

### Write a new test from scratch

```
1. forge_device_screenshot          → confirm which screen you're on
2. forge_device_tap / swipe         → navigate to the right screen
3. forge_get_hierarchy              → see all element keys available
4. forge_validate_test              → confirm all keys exist before running
5. forge_save_test                  → save to androidTests/
6. forge_run_test                   → run it
7. forge_heal_step (if needed)      → fix any broken locators
```

### Fix a broken test

```
1. forge_run_test                   → get failed step details
2. forge_device_screenshot          → confirm app is on the right screen
3. forge_heal_step                  → get the corrected YAML step
4. forge_read_test                  → read the full test
5. forge_save_test                  → save with the fix applied
6. forge_run_test                   → confirm it now passes
```

### Clean up the element registry

```
1. forge_check_elements             → see missing + unused
2. forge_save_element               → add any missing elements
3. forge_delete_element             → remove confirmed-unused elements
4. forge_check_elements             → verify clean state
```

### Explore elements on a screen

```
1. forge_device_screenshot          → visual check
2. forge_get_hierarchy              → full tier map of all elements
3. forge_list_elements search: X    → check if specific elements are registered
```

---

## How Element Tiers Work

When `forge_get_hierarchy` or `forge_heal_step` encounters a testTag, it goes through this resolution chain:

```
testTag on screen
       ↓
Is it in elements/*.yaml?
  YES → Tier 1: element: registered_key      ← use this
  NO  → auto-register it → Tier 2: element: new_key  ← available next run
       ↓
No testTag, but has visible text?
  → Tier 3: text: "Button Label"             ← works but fragile
       ↓
No tag, no text, but clickable?
  → Tier 4: x: 540  y: 1200                 ← last resort
     + suggestion: add Modifier.testTag() to the Composable
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `No ADB device connected` | Run `adb devices` — must show `device` status |
| Tools not appearing in Claude Code | Run `/mcp` to check; restart Claude Code after config changes |
| `File not found: shop.yaml` | Check `ELEMENTS_DIR` path in `index.js` matches actual repo location |
| `forge_run_test` times out | Test is hanging — check Appium session and device state |
| Tier 2 auto-registration fails | Check the feature file exists in `elements/` and is writable |
| `yaml` package not found | Run `npm install` in the `forge-mcp/` directory |
