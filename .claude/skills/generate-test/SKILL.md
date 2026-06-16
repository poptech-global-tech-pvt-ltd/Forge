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

## Step 2 — load context (run ALL of these)

```bash
# All available element keys for the relevant feature(s)
cat src/test/resources/elements/<feature>.yaml
cat src/test/resources/elements/common.yaml

# Existing tests for patterns and proven step sequences
ls src/test/resources/testdata/<feature>/
cat src/test/resources/testdata/<feature>/<most_relevant_test>.yaml

# All available actions
grep -E "case \"" src/main/java/com/popclub/mobile/actions/ActionFactory.java
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

## Step 4 — write the file

Determine the output path:
- Shop/cart tests → `src/test/resources/testdata/shop/<name>.yaml`
- Login tests → `src/test/resources/testdata/login.yaml`
- Home tests → `src/test/resources/testdata/home/<name>.yaml`
- Use snake_case for file names

Write the file, then print:
```
✅ Generated: src/test/resources/testdata/shop/<name>.yaml
   Steps: <N>
   Elements used: <list of unique element keys>

To run:
   mvn test -DtestFile=<name>.yaml
```

## Step 5 — validate

After writing, grep the elements YAMLs to confirm every `element:` key
you used actually exists:

```bash
# Quick validation — any element key that isn't found will show 0 matches
grep -c "^<element_key>:" src/test/resources/elements/*.yaml
```

For any key that returns 0 matches, either:
- Replace with a real key from the YAML, or
- Change to `locator: <raw_tag>` with a TODO comment

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
