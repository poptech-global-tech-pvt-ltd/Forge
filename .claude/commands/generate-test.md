Generate a Forge YAML test file from this description: $ARGUMENTS

## What you must do

### 1. Load context

Run all of these:
```bash
cat src/test/resources/elements/shop.yaml
cat src/test/resources/elements/common.yaml
cat src/test/resources/testdata/shop/ts_shop_checkout.yaml
ls src/test/resources/testdata/shop/
```

Also read any other elements YAML relevant to the description (cart, login, home, etc.).

### 2. Understand the description

From the description identify:
- Which feature area (shop / cart / login / home / rewards / etc.)
- What the test should verify
- Which screens will be visited
- Any specific element or toast/dialog to assert

### 3. Generate the YAML

Rules:

**Always start with:**
```yaml
- action: launchApp
- action: loginIfNeeded
  value: "1234561122"
  text: "560102"
- action: waitFor
  element: home_tab
```

**After every navigation:**
- Add `waitFor` for a landmark element on the destination screen
- Add `scanTags` to log available elements

**Search pattern (always this exact sequence):**
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

**Add-to-cart pattern:**
```yaml
- action: tap
  element: product_list_item_0
- action: waitFor
  element: product_details_title
- action: tap
  element: product_details_bottom_sticky_bar_button
- action: waitFor
  element: size_selection_item_0
- action: tap
  element: size_selection_item_0
- action: tap
  element: size_selection_add_to_cart_button
```

**To verify a toast or snackbar:**
Use `verifyElement` with the toast's element key, or `tapByText` if the toast text is known:
```yaml
- action: tapByText
  value: "Added to cart"
```
Or if there's a known tag:
```yaml
- action: verifyElement
  element: <toast_element_key>
```

**Available actions:** `launchApp` `loginIfNeeded` `tap` `tapIfPresent` `waitFor` `enterText` `pressKey` `verifyElement` `scanTags` `captureText` `assertStoredText` `scrollDown` `scrollUp` `pullToRefresh` `tapByText`

**File header:**
```yaml
testName: <descriptive name matching the description>
platform: android
noReset: true
loginRequired: true
features:
  - common
  - login
  - home
  - <feature>
tags:
  - smoke
  - <feature>
retry: 1
```

**Element key rules:**
- Only use keys that exist in the elements YAMLs (left-hand key name, not the raw accessibilityId value)
- For unknown elements use `locator: raw_tag_value  # TODO: add to elements/shop.yaml`
- For indexed items use `_0` suffix: `product_list_item_0`, `cart_item_row_0`

### 4. Determine output path

- Shop/cart/checkout → `src/test/resources/testdata/shop/`
- Login → `src/test/resources/testdata/`
- Home → `src/test/resources/testdata/home/`
- File name: snake_case description, e.g. `shop_add_to_cart_toast.yaml`

### 5. Validate every element key

```bash
# Check each element: key used in the test exists in elements YAMLs
grep -c "^<key>:" src/test/resources/elements/*.yaml
```

Any key returning 0 matches must be changed to `locator:` with a TODO comment.

### 6. Write the file and confirm

After writing, print:
```
✅ Generated: src/test/resources/testdata/shop/<name>.yaml
   Steps: <N>
   Run with: mvn test -DtestFile=<name>.yaml
```
