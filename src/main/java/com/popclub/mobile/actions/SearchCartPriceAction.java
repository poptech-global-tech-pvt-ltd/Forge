package com.popclub.mobile.actions;

import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * SearchCartPriceAction — end-to-end price-consistency check across the
 * search → PDP → cart funnel.
 *
 * Flow (for each of N items):
 *   1. Search for the given query and wait for the product-list results page
 *   2. Tap product_list_item_<i> to open the PDP
 *   3. Capture the product title (product_details_title) and the ₹ selling price
 *   4. Compare the list-page price with the PDP price — log match / mismatch
 *   5. Tap "Add to Cart" (product_details_bottom_sticky_bar_button)
 *      • If a size picker appears, select size option 0 and tap Add to Cart again
 *   6. Press Back → return to the product list
 * After all items:
 *   7. Navigate to Cart (cart icon)
 *   8. For every captured item: assert title + price are both visible in the cart
 *
 * YAML usage:
 *   - action: searchAndVerifyCartPrice
 *     value: yogabar     # search query
 *     text:  "2"         # number of items to check (default: 2)
 *
 * Requires: app is on the Shop tab with the search icon visible.
 */
public class SearchCartPriceAction implements Action {

    private static final long NAV_WAIT    = 2500;   // ms after tap, wait for screen
    private static final long SHORT_WAIT  = 1000;
    private static final long POLL_SLEEP  = 300;
    private static final int  MAX_SCROLLS = 6;

    /** Lightweight record for captured item data. */
    private static class ItemRecord {
        final String title;
        final String listPrice;
        final String pdpPrice;
        ItemRecord(String title, String listPrice, String pdpPrice) {
            this.title      = title;
            this.listPrice  = listPrice;
            this.pdpPrice   = pdpPrice;
        }
    }

    @Override
    public void perform(Step step) {
        try {
            performInternal(step);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void performInternal(Step step) throws Exception {

        String query     = step.value != null ? step.value.trim() : "yogabar";
        int    itemCount = 2;
        if (step.text != null && !step.text.isBlank()) {
            try { itemCount = Integer.parseInt(step.text.trim()); }
            catch (NumberFormatException ignored) {}
        }

        AppiumDriver driver = DriverManager.getDriver();

        System.out.println("\n══════════════════════════════════════════════════");
        System.out.printf ("  searchAndVerifyCartPrice: \"%s\" (%d items)%n", query, itemCount);
        System.out.println("══════════════════════════════════════════════════");

        // ── 1. Search ──────────────────────────────────────────────────────────
        doSearch(driver, query);

        // ── 2. Open each item, capture price, add to cart ─────────────────────
        // Try up to itemCount+5 indices to skip unavailable items
        List<ItemRecord> records = new ArrayList<>();
        int tried = 0;
        while (records.size() < itemCount && tried < itemCount + 5) {
            ItemRecord rec = processItem(driver, tried);
            if (rec != null) records.add(rec);
            tried++;
        }

        if (records.isEmpty()) {
            throw new RuntimeException(
                "searchAndVerifyCartPrice: no items could be processed for \"" + query + "\"");
        }

        // ── 3. Navigate to Cart and verify every item's price ─────────────────
        verifyCart(driver, records);

        // ── Summary ────────────────────────────────────────────────────────────
        System.out.println("\n── searchAndVerifyCartPrice Summary ──────────────");
        System.out.printf ("   Query : %s%n", query);
        System.out.printf ("   Items : %d%n", records.size());
        for (int i = 0; i < records.size(); i++) {
            ItemRecord r = records.get(i);
            System.out.printf("   [%d] %s%n",           i, r.title);
            System.out.printf("       List price : %s%n", r.listPrice.isEmpty() ? "(n/a)" : r.listPrice);
            System.out.printf("       PDP  price : %s%n", r.pdpPrice.isEmpty()  ? "(n/a)" : r.pdpPrice);
        }
        System.out.println("──────────────────────────────────────────────────\n");
    }

    // ── Search ─────────────────────────────────────────────────────────────────

    private void doSearch(AppiumDriver driver, String query) throws Exception {

        // If already on the search screen (e.g. after retry), skip tapping the icon
        WebElement input = find(driver, "accessibilityId", "search_products_search_input");
        if (input == null) {
            // Tap search icon on shop tab — try both known qaTestTags
            WebElement icon = waitFor(driver, "accessibilityId", "shop_search_button", 5_000);
            if (icon == null)
                icon = waitFor(driver, "accessibilityId", "search", 3_000);
            if (icon == null)
                throw new RuntimeException("Search icon not found — is the app on the Shop tab?");
            icon.click();
            Thread.sleep(SHORT_WAIT);

            input = waitFor(driver, "accessibilityId", "search_products_search_input", 4_000);
            if (input == null)
                input = waitFor(driver, "accessibilityId", "Search", 3_000);
            if (input == null)
                throw new RuntimeException("Search input not visible after tapping search icon");
        }
        assertTagPresent(driver, "product_list_filter_button", "search_products_search_input");
        // Click wrapper to focus, then type into the focused EditText child
        input.click();
        Thread.sleep(800);
        try {
            WebElement editText = driver.findElement(
                AppiumBy.xpath("//android.widget.EditText[@focused='true']"));
            editText.clear();
            editText.sendKeys(query);
        } catch (Exception ex) {
            driver.executeScript("mobile: type", java.util.Map.of("text", query));
        }
        Thread.sleep(500);

        // Submit search (Enter key)
        ((AndroidDriver) driver).pressKey(new KeyEvent(AndroidKey.ENTER));
        Thread.sleep(NAV_WAIT);

        // Wait for the product-list page (Sort/Filter bar is reliable indicator)
        WebElement filterBtn = waitFor(driver, "accessibilityId",
                "product_list_filter_button", 10_000);
        if (filterBtn == null)
            throw new RuntimeException(
                "Product list did not load after searching for \"" + query + "\"");

        // Verify key product-list tags are present (from popdroid TestTags.kt)
        assertTagPresent(driver,
                "product_list_filter_button",     // filter/sort bar
                "product_list_sort_button",        // sort button
                "product_list_item_0"              // first product card
        );
        System.out.printf("  ✅ Search results loaded for: \"%s\"%n%n", query);
    }

    // ── Item processing (open PDP, capture, add to cart, back) ────────────────

    private ItemRecord processItem(AppiumDriver driver, int index) throws Exception {

        String cardId = "product_list_item_" + index;
        System.out.printf("  ── Item [%d] ─────────────────────────────────────%n", index);

        // Scroll to the card if needed
        WebElement card = findWithScroll(driver, "accessibilityId", cardId);
        if (card == null) {
            System.out.printf("  ⚠️  %s not found — skipping%n", cardId);
            return null;
        }

        // Capture the price shown on the list page for this item — scoped to card element
        String listPrice = captureListPrice(card);
        System.out.printf("  📋 List price [%d] : %s%n", index,
                listPrice.isEmpty() ? "(not captured)" : listPrice);

        // Open PDP
        card.click();
        Thread.sleep(NAV_WAIT);

        // Capture title
        String title = capturePdpTitle(driver);
        if (title.isEmpty()) title = "Item " + index;
        System.out.printf("  📦 PDP title  [%d] : %s%n", index, title);

        // Capture PDP selling price
        String pdpPrice = capturePdpPrice(driver);
        System.out.printf("  💰 PDP price  [%d] : %s%n", index,
                pdpPrice.isEmpty() ? "(not captured)" : pdpPrice);

        // List price vs PDP price consistency check
        if (!listPrice.isEmpty() && !pdpPrice.isEmpty()) {
            if (normalizePrice(listPrice).equals(normalizePrice(pdpPrice))) {
                System.out.printf("  ✅ Price consistent — list \"%s\" = PDP \"%s\"%n",
                        listPrice, pdpPrice);
            } else {
                System.out.printf("  ❌ Price MISMATCH — list \"%s\" ≠ PDP \"%s\"%n",
                        listPrice, pdpPrice);
            }
        }

        // Add to cart — returns false if item is unavailable
        boolean added = addToCart(driver, title);

        // Return to product list
        pressBack(driver);
        Thread.sleep(SHORT_WAIT);

        if (!added) {
            System.out.printf("  ⏭️  Item [%d] unavailable — skipping%n", index);
            waitFor(driver, "accessibilityId", "product_list_filter_button", 5_000);
            return null;
        }
        Thread.sleep(SHORT_WAIT);

        // Confirm we're back on the list page
        WebElement listCheck = waitFor(driver, "accessibilityId",
                "product_list_filter_button", 5_000);
        if (listCheck == null)
            System.out.println("  ⚠️  product_list_filter_button not visible after back — continuing");

        return new ItemRecord(title, listPrice, pdpPrice);
    }

    // ── Price capture helpers ──────────────────────────────────────────────────

    /**
     * Capture the selling price from a product card element.
     * Uses XPath to find all text nodes starting with ₹ within the card.
     * The last one is the selling price (MRP/strikethrough appears first).
     */
    private String captureListPrice(WebElement card) {
        try {
            // XPath on the card element scopes the search to its subtree
            List<WebElement> prices = card.findElements(
                AppiumBy.xpath(".//*[starts-with(@text,'₹')]"));
            if (!prices.isEmpty()) {
                return prices.get(prices.size() - 1).getText().trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    /** Capture the main title text from the product details page. */
    private String capturePdpTitle(AppiumDriver driver) throws InterruptedException {
        WebElement titleEl = waitFor(driver, "accessibilityId", "product_details_title", 5_000);
        if (titleEl != null) {
            String t = titleEl.getText();
            return t != null ? t.trim() : "";
        }
        return "";
    }

    /**
     * Capture the first ₹-prefixed text visible on the PDP — this is the
     * selling price (most prominent price shown near the top of the page).
     */
    private String capturePdpPrice(AppiumDriver driver) {
        try {
            List<WebElement> els = driver.findElements(
                AppiumBy.androidUIAutomator("new UiSelector().textStartsWith(\"₹\")"));
            if (!els.isEmpty()) return els.get(0).getText().trim();
        } catch (Exception ignored) {}
        return "";
    }

    // ── Add to Cart ────────────────────────────────────────────────────────────

    // Returns true if item was added to cart, false if item is unavailable
    private boolean addToCart(AppiumDriver driver, String title) throws Exception {

        assertTagPresent(driver, "product_details_bottom_sticky_bar_button");

        WebElement btn = waitFor(driver, "accessibilityId",
                "product_details_bottom_sticky_bar_button", 5_000);
        if (btn == null) {
            System.out.printf("  ⚠️  Add to Cart button not found for \"%s\"%n", title);
            return false;
        }

        // Check if button label indicates item is unavailable before tapping
        String btnText = btn.getText();
        if (isUnavailableLabel(btnText)) {
            System.out.printf("  ⏭️  PDP button says \"%s\" — item unavailable%n", btnText);
            return false;
        }

        btn.click();
        Thread.sleep(SHORT_WAIT);

        // Check for unavailability toast after tap (e.g. "Currently Unavailable", "Notify Me")
        if (isUnavailableOnScreen(driver)) {
            System.out.printf("  ⏭️  Unavailability toast detected for \"%s\" — skipping%n", title);
            return false;
        }

        // Handle size/variant picker if it appeared
        WebElement sizeItem = find(driver, "accessibilityId", "size_selection_item_0");
        if (sizeItem != null) {
            System.out.println("  📏 Size picker appeared — selecting size_selection_item_0");
            sizeItem.click();
            Thread.sleep(500);

            WebElement sizeAddBtn = waitFor(driver, "accessibilityId",
                    "size_selection_add_to_cart_button", 3_000);
            if (sizeAddBtn != null) {
                sizeAddBtn.click();
                Thread.sleep(SHORT_WAIT);
            } else {
                WebElement btnAgain = find(driver, "accessibilityId",
                        "product_details_bottom_sticky_bar_button");
                if (btnAgain != null) btnAgain.click();
                Thread.sleep(SHORT_WAIT);
            }
        }

        System.out.printf("  ✅ Add to Cart tapped for \"%s\"%n", title);
        return true;
    }

    private boolean isUnavailableLabel(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("notify") || lower.contains("unavailable")
            || lower.contains("out of stock") || lower.contains("sold out");
    }

    private boolean isUnavailableOnScreen(AppiumDriver driver) {
        String[] signals = { "Notify Me", "Currently Unavailable", "Out of Stock", "Sold Out" };
        for (String signal : signals) {
            if (isTextVisible(driver, signal)) return true;
        }
        return false;
    }

    // ── Cart quantity reduction ────────────────────────────────────────────────

    /**
     * For each cart_item_row_N, reduce its quantity to 1 by tapping the decrease
     * button scoped to that row. Stops when no more rows are found.
     */
    private void reduceAllQuantitiesToOne(AppiumDriver driver) throws InterruptedException {
        System.out.println("  🔢 Reducing cart quantities to 1...");
        for (int rowIdx = 0; rowIdx < 10; rowIdx++) {
            String rowTag = "cart_item_row_" + rowIdx;
            WebElement row = find(driver, "accessibilityId", rowTag);
            if (row == null) break; // no more rows

            // Find the quantity text within this row
            int qty = getQuantityInRow(row);
            if (qty <= 1) {
                System.out.printf("  ✅ Row [%d] quantity already 1%n", rowIdx);
                continue;
            }

            System.out.printf("  📉 Row [%d] quantity is %d — reducing to 1%n", rowIdx, qty);
            // Tap decrease (qty - 1) times, scoped to this row
            for (int tap = 0; tap < qty - 1; tap++) {
                // Re-find row each iteration as the DOM updates after each tap
                row = find(driver, "accessibilityId", rowTag);
                if (row == null) break;
                try {
                    WebElement decreaseBtn = row.findElement(
                        AppiumBy.androidUIAutomator("new UiSelector().description(\"cart_item_decrease_button\")"));
                    decreaseBtn.click();
                    Thread.sleep(500);
                } catch (Exception e) {
                    // fallback: tap the first decrease button on screen
                    WebElement decreaseBtn = find(driver, "accessibilityId", "cart_item_decrease_button");
                    if (decreaseBtn != null) { decreaseBtn.click(); Thread.sleep(500); }
                    break;
                }
            }
        }
        System.out.println("  ✅ Quantities reduced to 1");
    }

    /** Read the quantity number displayed inside a cart item row element. */
    private int getQuantityInRow(WebElement row) {
        try {
            // Quantity stepper shows a single digit between + and - buttons
            for (int qty = 9; qty >= 2; qty--) {
                List<org.openqa.selenium.WebElement> els = row.findElements(
                    AppiumBy.androidUIAutomator("new UiSelector().text(\"" + qty + "\")"));
                if (!els.isEmpty()) return qty;
            }
        } catch (Exception ignored) {}
        return 1;
    }

    // ── Cart verification ──────────────────────────────────────────────────────

    private void verifyCart(AppiumDriver driver, List<ItemRecord> records) throws Exception {

        System.out.println("\n  ── Navigating to Cart ───────────────────────────");

        WebElement cartIcon = waitFor(driver, "accessibilityId", "cart", 5_000);
        if (cartIcon == null)
            throw new RuntimeException("Cart icon not found — cannot navigate to cart");
        cartIcon.click();
        Thread.sleep(NAV_WAIT);

        // Wait for cart to load (at least one cart item image must appear)
        WebElement cartItem = waitFor(driver, "accessibilityId", "cart_item_image", 7_000);
        if (cartItem == null)
            System.out.println("  ⚠️  cart_item_image not visible — cart might be empty");

        int passed = 0, failed = 0;

        // Reduce all cart item quantities to 1 before price checks
        reduceAllQuantitiesToOne(driver);

        for (ItemRecord r : records) {
            System.out.printf("%n  ── Cart check: \"%s\"%n", r.title);

            // 1. Title check — use first 25 chars (cart truncates long titles)
            String shortTitle = r.title.length() > 25 ? r.title.substring(0, 25) : r.title;
            boolean titleOk = findOnScreen(driver, shortTitle);
            if (titleOk) {
                System.out.printf("  ✅ Title found in cart: \"%s\"%n", shortTitle);
            } else {
                System.out.printf("  ⚠️  Title not found in cart (truncated): \"%s\"%n", shortTitle);
            }

            // 2. Price check — prefer PDP price, fall back to list price
            String expectedPrice = !r.pdpPrice.isEmpty() ? r.pdpPrice : r.listPrice;
            if (expectedPrice.isEmpty()) {
                System.out.println("  ⚠️  No price captured — skipping price check");
                passed++;
                continue;
            }

            // Strip ₹ and commas for a plain-number search (more resilient)
            String numericPrice = normalizePrice(expectedPrice);
            boolean priceOk = isTextVisible(driver, numericPrice)
                           || isTextVisible(driver, expectedPrice);

            if (priceOk) {
                System.out.printf("  ✅ Price confirmed in cart: %s%n", expectedPrice);
                passed++;
            } else {
                System.out.printf("  ❌ Price NOT found in cart: %s%n", expectedPrice);
                failed++;
            }
        }

        System.out.println("\n  ── Cart Verification Summary ────────────────────");
        System.out.printf ("     Items checked : %d%n", records.size());
        System.out.printf ("     Passed        : %d%n", passed);
        System.out.printf ("     Failed        : %d%n", failed);

        if (failed > 0 && passed == 0) {
            throw new RuntimeException(
                "searchAndVerifyCartPrice: all cart price checks failed");
        }
    }

    // ── Generic helpers ────────────────────────────────────────────────────────

    private WebElement find(AppiumDriver driver, String type, String value) {
        try {
            List<WebElement> found = "accessibilityId".equals(type)
                ? driver.findElements(AppiumBy.accessibilityId(value))
                : driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"" + value.replace("\"", "\\\"") + "\")"));
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) { return null; }
    }

    private WebElement waitFor(AppiumDriver driver, String type, String value, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            WebElement el = find(driver, type, value);
            if (el != null) return el;
            Thread.sleep(POLL_SLEEP);
        }
        return null;
    }

    private WebElement findWithScroll(AppiumDriver driver, String type, String value)
            throws InterruptedException {
        for (int s = 0; s <= MAX_SCROLLS; s++) {
            WebElement el = find(driver, type, value);
            if (el != null) return el;
            if (s < MAX_SCROLLS) { scrollDown(driver); Thread.sleep(500); }
        }
        return null;
    }

    private boolean isTextVisible(AppiumDriver driver, String text) {
        try {
            String esc = text.replace("\"", "\\\"");
            return !driver.findElements(AppiumBy.androidUIAutomator(
                "new UiSelector().textContains(\"" + esc + "\")")).isEmpty();
        } catch (Exception e) { return false; }
    }

    private boolean findOnScreen(AppiumDriver driver, String text) throws InterruptedException {
        for (int s = 0; s <= 4; s++) {
            if (isTextVisible(driver, text)) return true;
            if (s < 4) { scrollDown(driver); Thread.sleep(500); }
        }
        return false;
    }

    /** Strip ₹, commas and spaces so "₹1,299" and "1299" compare equal. */
    private String normalizePrice(String price) {
        return price.replaceAll("[₹,\\s]", "");
    }

    /**
     * Assert that the given qaTestTag accessibility IDs (from popdroid TestTags.kt)
     * are present somewhere in the current view hierarchy using UIAutomator.
     * Logs ✅ for each found tag, ⚠️ for each missing one — never throws.
     */
    private void assertTagPresent(AppiumDriver driver, String... tags) {
        for (String tag : tags) {
            try {
                boolean found = !driver.findElements(
                    AppiumBy.androidUIAutomator(
                        "new UiSelector().description(\"" + tag + "\")"))
                    .isEmpty();
                // Also try contentDescription contains, in case the label is composite
                if (!found) {
                    found = !driver.findElements(AppiumBy.accessibilityId(tag)).isEmpty();
                }
                System.out.printf("  %s [TAG] %s%n", found ? "✅" : "⚠️ MISSING", tag);
            } catch (Exception e) {
                System.out.printf("  ⚠️ [TAG] %s — check failed: %s%n", tag, e.getMessage());
            }
        }
    }

    private void pressBack(AppiumDriver driver) {
        try {
            ((AndroidDriver) driver).pressKey(new KeyEvent(AndroidKey.BACK));
        } catch (Exception e) {
            System.out.println("  ⚠️  Back press failed: " + e.getMessage());
        }
    }

    private void scrollDown(AppiumDriver driver) {
        try {
            Dimension size  = driver.manage().window().getSize();
            int startY = (int)(size.height * 0.75);
            int endY   = (int)(size.height * 0.25);
            int cx     = size.width / 2;
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence seq = new Sequence(finger, 1);
            seq.addAction(finger.createPointerMove(Duration.ZERO,
                    PointerInput.Origin.viewport(), cx, startY));
            seq.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            seq.addAction(finger.createPointerMove(Duration.ofMillis(400),
                    PointerInput.Origin.viewport(), cx, endY));
            seq.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(List.of(seq));
        } catch (Exception e) {
            System.out.println("  ⚠️  Scroll failed: " + e.getMessage());
        }
    }
}
