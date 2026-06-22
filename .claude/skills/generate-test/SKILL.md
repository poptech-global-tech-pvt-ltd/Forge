---
name: generate-test
description: >
  Generate a Forge YAML test from a plain-English description.
  Reads the elements YAMLs and existing tests to know what tags exist,
  then writes a ready-to-run test file.
  Invoke with /generate-test "description of what to test".
---

# Forge Test Generator

You generate Forge YAML test files from a plain-English description.
You have zero tolerance for hallucinating element names — every element key
you use MUST exist in the elements YAML files or be clearly visible in an
existing test.

## When to run

User invokes `/generate-test "<description>"`, e.g.:
- `/generate-test "test shop entire flow"`
- `/generate-test "test login with wrong OTP"`
- `/generate-test "verify wishlist add and remove"`

## Step 1 — understand the description

Parse what the user wants to test. Identify:
- **Feature area**: shop / login / cart / home / profile / rewards / upi / etc.
- **Happy path or edge case**: full flow, error state, empty state, etc.
- **Scope**: which screens are involved

**If the description is vague** (e.g. "test now", "test this", "test screen", "test it") —
do NOT ask for clarification. Instead call `forge_get_hierarchy` immediately to scan
the live device screen, then generate a test for whatever flow is visible on screen.
Let the screen tell you what to test.

**If the user is asking to edit/change an existing test** (e.g. "change X to Y",
"update the search term", "add a step after", "replace X with Y") —
NEVER describe the change in text. NEVER create a new file. ALWAYS:
1. Call `forge_read_test` — get the EXACT current file content (do not reconstruct from memory)
2. Apply ONLY the requested change to that content — every other line stays identical
3. Call `forge_save_test` with:
   - The COMPLETE modified YAML (not just the changed lines)
   - The EXACT SAME `filename` as the original file
   - The EXACT SAME `subfolder` as the original file
4. Then go to **Step 5** (run → heal → verify)

If you are unsure of the original filename or subfolder, call `forge_read_test` with
your best guess — it will search all subfolders automatically.

## ⛔ Hard rules (never break these)

- **NEVER use the Write or Edit tool to save test files** — always use `forge_save_test`
- **NEVER generate a test that already exists** — check first (Step 1.5)
- The correct test root is `src/test/java/com/popclub/androidTests/` — NOT `src/test/resources/testdata/`
- **NEVER ask which test the user means if one was already mentioned in this conversation** — use that test
- **NEVER ask for clarification you can infer from conversation history** — act on context, ask only when genuinely ambiguous
- **NEVER describe a file change in text** — always call `forge_read_test` then `forge_save_test`
- **NEVER save an edited test under a new filename** — always use the original filename
- **ALWAYS run the test after saving** — call `forge_run_test` every time, no exceptions

## Step 1.5 — check for existing tests first

Before generating anything, search for existing tests that cover the same scenario:

```bash
# Search by keyword in test names + content
find src/test/java/com/popclub/androidTests -name "*.yaml" | xargs grep -li "<keyword>" 2>/dev/null
ls src/test/java/com/popclub/androidTests/<feature>/
```

If a matching test already exists:
- Show its path and a summary of what it covers
- Ask the user if they want to (a) run it, (b) extend it, or (c) create a new separate test
- Do NOT generate a duplicate

## Step 2 — load context (run ALL of these)

```bash
# All available element keys for the relevant feature(s)
cat src/test/resources/testdata/elements/<feature>.yaml
cat src/test/resources/testdata/elements/common.yaml

# Existing tests for patterns and proven step sequences
ls src/test/java/com/popclub/androidTests/<feature>/
cat src/test/java/com/popclub/androidTests/<feature>/<most_relevant_test>.yaml

# All available actions
grep -E "case \"" src/main/java/com/popclub/android/actions/ActionFactory.java
```

Read at minimum: the feature elements YAML + common.yaml + one existing test
in the same area. For shop/cart/checkout tests also read shop.yaml elements.

## Step 3 — generate the YAML

Rules you MUST follow:

### Structure
```yaml
testName: <descriptive name>
platform: android
noReset: true          # always true unless test specifically needs fresh install
loginRequired: true    # false only if test does not need a logged-in user

features:
  - common
  - <feature>          # adds elements from that YAML to the resolver

tags:
  - smoke              # always include smoke; add feature tag too
  - <feature>

retry: 1              # default

steps:
  - action: launchApp

  - action: loginIfNeeded
    value: "1234561122"   # phone number
    text: "560102"        # OTP

  - action: waitFor
    element: home_tab     # always wait for home before navigating

  # ... rest of steps
```

### Action reference
| Action | When to use |
|---|---|
| `launchApp` | Always first step |
| `loginIfNeeded` | Always second step (no-op if already logged in) |
| `waitFor` | After every navigation — wait for a landmark element |
| `tap` | Tap a known element |
| `tapIfPresent` | Optional elements (dialogs, tooltips, banners) |
| `enterText` | Type into an input field — use `element:` to target it |
| `pressKey` | `value: search` after enterText in search inputs |
| `verifyElement` | Assert element is visible |
| `scanTags` | Add after reaching a new screen (shows available tags in logs) |
| `captureText` | Capture visible text into a named variable |
| `assertStoredText` | Assert captured text is still visible (or `text: absent`) |
| `scrollDown` / `scrollUp` | When content is below the fold |
| `pullToRefresh` | Refresh a feed/list |

### Element naming rules
- Use the exact key name from the elements YAML (left-hand side, e.g. `shop_search_button`)
- Never use the raw accessibilityId value (right-hand side) as the element name
- If you need an element that is not in the YAML, add a `# TODO: add to elements/shop.yaml` comment and use `locator:` with the raw tag value as fallback:
  ```yaml
  - action: tap
    locator: shop_new_button   # TODO: add to elements/shop.yaml
  ```
- For indexed items (product list, cart items) use `_0` suffix: `product_list_item_0`

### Screen navigation pattern
Every screen transition follows this pattern:
```yaml
- action: tap
  element: <button_that_navigates>

- action: waitFor
  element: <landmark_on_destination_screen>

- action: scanTags        # optional but recommended for new screens
```

### Search pattern (always this exact sequence)
```yaml
- action: tap
  element: shop_search_button

- action: waitFor
  element: search_products_search_input

- action: enterText
  element: search_products_search_input
  value: "search term"

- action: pressKey
  value: search

- action: waitFor
  element: product_list_item_0
```

### Add-to-cart pattern
```yaml
- action: tap
  element: product_list_item_0

- action: waitFor
  element: product_details_title

- action: tap
  element: product_details_bottom_sticky_bar_button   # "Add to cart" CTA

- action: waitFor
  element: size_selection_item_0

- action: tap
  element: size_selection_item_0

- action: tap
  element: size_selection_add_to_cart_button

- action: waitFor
  element: product_details_go_to_cart_button

- action: tap
  element: product_details_go_to_cart_button
```

### Cart verification pattern
```yaml
- action: waitFor
  element: cart_item_row_0

- action: verifyElement
  element: cart_delivery_address_card

- action: verifyElement
  element: cart_to_pay_row

- action: verifyElement
  element: cart_s2_s_payment_button_button   # the Pay CTA
```

## Step 4 — save the file

Use the `forge_save_test` MCP tool — NEVER write the file directly.

```
forge_save_test(
  filename  = "ts_<snake_case_name>.yaml",
  content   = <full YAML string>,
  subfolder = "<feature>"   # shop | login | home | profile | rewards | upi — omit if unsure
)
```

Subfolder mapping:
- shop / cart / checkout / pdp tests → `shop`
- login / otp / auth tests           → `login`
- home / clp / banner tests          → `home`
- profile / account tests            → `profile`
- rewards / cashback tests           → `rewards`
- upi / payment tests                → `upi`
- cross-feature or unsure            → omit subfolder (saves to androidTests root)

The tool returns the saved path. Show it to the user.

## Step 5 — run the test

After saving, immediately run it using `forge_run_test`:

```
forge_run_test(testFile = "<filename>.yaml")
```

## Step 6 — heal failing steps (repeat until pass or 3 attempts)

If `forge_run_test` reports a failed step:

1. Call `forge_heal_step` with the broken element and action:
   ```
   forge_heal_step(
     brokenElement = "<element key that failed>",
     action        = "<action>",
     stepYaml      = "<the raw YAML of the failing step>"
   )
   ```

2. Apply the fixed step returned by `forge_heal_step` into the YAML.

3. Call `forge_save_test` again with the updated content (same filename + subfolder).

4. Call `forge_run_test` again.

5. Repeat up to **3 heal attempts**. If still failing after 3, stop and tell the
   user which step is broken and what `forge_heal_step` suggested — they may need
   to navigate the app to the right screen first.

## Step 7 — done

Once the test passes, summarise:
```
✅ Test passing: <filename>.yaml
   Saved: src/test/java/com/popclub/androidTests/<subfolder>/<filename>.yaml
   Steps: <N>  |  Healed: <M> steps
   Run: mvn test -DtestFile=<filename>.yaml
```

## Maestro → Forge converter (when user pastes Maestro YAML)

If the user pastes a Maestro YAML block, convert it using this mapping:

| Maestro | Forge |
|---|---|
| `- launchApp` | `- action: launchApp` |
| `- tapOn:\n    text: "X"` | Look up text "X" in elements YAML → `- action: tap\n  element: <key>` |
| `- tapOn:\n    id: "x_tag"` | Find element key where accessibilityId value = `x_tag` → use that key |
| `- inputText: "X"` | `- action: enterText\n  value: "X"` |
| `- pressKey: Enter` | `- action: pressKey\n  value: search` |
| `- assertVisible:\n    text: "X"` | `- action: verifyElement` using matching element key |
| `- scroll` | `- action: scrollDown` |
| `- back` | `- action: pressKey\n  value: back` |
| `- waitForAnimationToEnd` | omit (Forge handles this in waitFor) |

When converting `tapOn: text`, search all elements YAMLs for a key whose
accessibilityId value contains the text (case-insensitive). If no match,
use `tapByText: "X"` in Forge.

## Common mistakes to avoid

- Do NOT skip `waitFor` after navigation — flaky tests always trace back to this
- Do NOT use `loginRequired: false` unless the test is specifically for logged-out state
- Do NOT hardcode phone/OTP differently — always use `"1234561122"` / `"560102"`
- Do NOT use `noReset: false` — it reinstalls the APK and clears login state
- Do NOT add `scanTags` before `launchApp` or `loginIfNeeded`
- Do NOT invent element keys — only use keys that exist in the elements YAMLs
