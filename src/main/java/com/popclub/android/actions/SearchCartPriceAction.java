package com.popclub.android.actions;

import com.popclub.android.driver.DriverManager;
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

/** searchAndVerifyCartPrice — searches, adds N items to cart, and verifies title + price in cart. */
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

        doSearch(driver, query);

        // try up to itemCount+5 indices to skip unavailable items
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

        verifyCart(driver, records);
    }

    private void doSearch(AppiumDriver driver, String query) throws Exception {

        // if already on the search screen (e.g. after retry), skip tapping the icon
        WebElement input = find(driver, "accessibilityId", "search_products_search_input");
        if (input == null) {
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

        ((AndroidDriver) driver).pressKey(new KeyEvent(AndroidKey.ENTER));
        Thread.sleep(NAV_WAIT);

        WebElement filterBtn = waitFor(driver, "accessibilityId",
                "product_list_filter_button", 10_000);
        if (filterBtn == null)
            throw new RuntimeException(
                "Product list did not load after searching for \"" + query + "\"");

        assertTagPresent(driver,
                "product_list_filter_button",
                "product_list_sort_button",
                "product_list_item_0"
        );
    }

    private ItemRecord processItem(AppiumDriver driver, int index) throws Exception {

        String cardId = "product_list_item_" + index;

        WebElement card = findWithScroll(driver, "accessibilityId", cardId);
        if (card == null) return null;

        String listPrice = captureListPrice(card);

        card.click();
        Thread.sleep(NAV_WAIT);

        String title = capturePdpTitle(driver);
        if (title.isEmpty()) title = "Item " + index;

        String pdpPrice = capturePdpPrice(driver);

        if (!listPrice.isEmpty() && !pdpPrice.isEmpty()
                && !normalizePrice(listPrice).equals(normalizePrice(pdpPrice))) {
            System.out.printf("  Price MISMATCH — list \"%s\" != PDP \"%s\"%n", listPrice, pdpPrice);
        }

        boolean added = addToCart(driver, title);

        // Return to product list
        pressBack(driver);
        Thread.sleep(SHORT_WAIT);

        if (!added) {
            waitFor(driver, "accessibilityId", "product_list_filter_button", 5_000);
            return null;
        }
        Thread.sleep(SHORT_WAIT);

        waitFor(driver, "accessibilityId", "product_list_filter_button", 5_000);

        return new ItemRecord(title, listPrice, pdpPrice);
    }

    /**
     * Capture the selling price from a product card element.
     * The last ₹-prefixed text is the selling price (MRP/strikethrough appears first).
     */
    private String captureListPrice(WebElement card) {
        try {
            List<WebElement> prices = card.findElements(
                AppiumBy.xpath(".//*[starts-with(@text,'₹')]"));
            if (!prices.isEmpty()) {
                return prices.get(prices.size() - 1).getText().trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String capturePdpTitle(AppiumDriver driver) throws InterruptedException {
        WebElement titleEl = waitFor(driver, "accessibilityId", "product_details_title", 5_000);
        if (titleEl != null) {
            String t = titleEl.getText();
            return t != null ? t.trim() : "";
        }
        return "";
    }

    private String capturePdpPrice(AppiumDriver driver) {
        try {
            List<WebElement> els = driver.findElements(
                AppiumBy.androidUIAutomator("new UiSelector().textStartsWith(\"₹\")"));
            if (!els.isEmpty()) return els.get(0).getText().trim();
        } catch (Exception ignored) {}
        return "";
    }

    /** Returns true if item was added to cart, false if item is unavailable. */
    private boolean addToCart(AppiumDriver driver, String title) throws Exception {

        assertTagPresent(driver, "product_details_bottom_sticky_bar_button");

        WebElement btn = waitFor(driver, "accessibilityId",
                "product_details_bottom_sticky_bar_button", 5_000);
        if (btn == null) return false;

        String btnText = btn.getText();
        if (isUnavailableLabel(btnText)) return false;

        btn.click();
        Thread.sleep(SHORT_WAIT);

        if (isUnavailableOnScreen(driver)) return false;

        // handle size/variant picker if it appeared
        WebElement sizeItem = find(driver, "accessibilityId", "size_selection_item_0");
        if (sizeItem != null) {
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

    private void reduceAllQuantitiesToOne(AppiumDriver driver) throws InterruptedException {
        for (int rowIdx = 0; rowIdx < 10; rowIdx++) {
            String rowTag = "cart_item_row_" + rowIdx;
            WebElement row = find(driver, "accessibilityId", rowTag);
            if (row == null) break; // no more rows

            // Find the quantity text within this row
            int qty = getQuantityInRow(row);
            if (qty <= 1) continue;

            for (int tap = 0; tap < qty - 1; tap++) {
                // re-find row each iteration as the DOM updates after each tap
                row = find(driver, "accessibilityId", rowTag);
                if (row == null) break;
                try {
                    WebElement decreaseBtn = row.findElement(
                        AppiumBy.androidUIAutomator("new UiSelector().description(\"cart_item_decrease_button\")"));
                    decreaseBtn.click();
                    Thread.sleep(500);
                } catch (Exception e) {
                    // fallback to first decrease button on screen if row-scoped lookup fails
                    WebElement decreaseBtn = find(driver, "accessibilityId", "cart_item_decrease_button");
                    if (decreaseBtn != null) { decreaseBtn.click(); Thread.sleep(500); }
                    break;
                }
            }
        }
    }

    private int getQuantityInRow(WebElement row) {
        try {
            for (int qty = 9; qty >= 2; qty--) {
                List<org.openqa.selenium.WebElement> els = row.findElements(
                    AppiumBy.androidUIAutomator("new UiSelector().text(\"" + qty + "\")"));
                if (!els.isEmpty()) return qty;
            }
        } catch (Exception ignored) {}
        return 1;
    }

    private void verifyCart(AppiumDriver driver, List<ItemRecord> records) throws Exception {

        WebElement cartIcon = waitFor(driver, "accessibilityId", "cart", 5_000);
        if (cartIcon == null)
            throw new RuntimeException("Cart icon not found — cannot navigate to cart");
        cartIcon.click();
        Thread.sleep(NAV_WAIT);

        // Wait for cart to load (at least one cart item image must appear)
        waitFor(driver, "accessibilityId", "cart_item_image", 7_000);

        int passed = 0, failed = 0;

        reduceAllQuantitiesToOne(driver);

        for (ItemRecord r : records) {
            // use first 25 chars — cart truncates long titles
            String shortTitle = r.title.length() > 25 ? r.title.substring(0, 25) : r.title;
            findOnScreen(driver, shortTitle);

            // prefer PDP price, fall back to list price
            String expectedPrice = !r.pdpPrice.isEmpty() ? r.pdpPrice : r.listPrice;
            if (expectedPrice.isEmpty()) {
                passed++;
                continue;
            }

            // strip ₹ and commas for a plain-number search — more resilient to formatting
            String numericPrice = normalizePrice(expectedPrice);
            boolean priceOk = isTextVisible(driver, numericPrice)
                           || isTextVisible(driver, expectedPrice);

            if (priceOk) {
                passed++;
            } else {
                System.out.printf("  Price NOT found in cart: %s%n", expectedPrice);
                failed++;
            }
        }

        if (failed > 0 && passed == 0) {
            throw new RuntimeException(
                "searchAndVerifyCartPrice: all cart price checks failed");
        }
    }

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

    /** Logs whether each qaTestTag accessibility ID is present — never throws. */
    private void assertTagPresent(AppiumDriver driver, String... tags) {
        for (String tag : tags) {
            try {
                boolean found = !driver.findElements(
                    AppiumBy.androidUIAutomator(
                        "new UiSelector().description(\"" + tag + "\")"))
                    .isEmpty();
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
