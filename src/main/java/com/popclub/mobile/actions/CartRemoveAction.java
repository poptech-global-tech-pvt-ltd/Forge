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
import java.util.List;

/**
 * CartRemoveAction — navigates to the cart, captures the first visible item
 * title, removes it via the remove button, and then asserts it is no longer
 * present on the cart screen.
 *
 * YAML usage:
 *   - action: cartRemoveItem
 *
 * Requires: at least one item is in the cart.
 * State after: cart screen, item removed.
 */
public class CartRemoveAction implements Action {

    private static final long NAV_WAIT   = 2500;
    private static final long SHORT_WAIT = 1000;
    private static final long POLL_SLEEP = 300;

    @Override
    public void perform(Step step) {
        try {
            performInternal();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void performInternal() throws Exception {

        AppiumDriver driver = DriverManager.getDriver();

        System.out.println("\n══════════════════════════════════════════════════");
        System.out.println("  cartRemoveItem");
        System.out.println("══════════════════════════════════════════════════");

        // ── 1. Navigate to cart ────────────────────────────────────────────────
        navigateToCart(driver);

        // ── 2. Confirm cart has at least one item ─────────────────────────────
        WebElement cartItemImg = waitFor(driver, "accessibilityId", "cart_item_image", 7_000);
        if (cartItemImg == null)
            throw new RuntimeException(
                "cartRemoveItem: cart appears empty — no cart_item_image found");

        // Verify cart screen tags are present (popdroid TestTags.kt)
        assertTagPresent(driver,
                "cart_item_image",
                "cart_remove_product_button",
                "cart_checkout_pop_shop_button");

        // ── 3. Capture the first item's title so we can verify it's gone later ─
        String removedTitle = captureFirstCartItemTitle(driver);
        System.out.printf("  📦 Item to remove: \"%s\"%n",
                removedTitle.isEmpty() ? "(title not captured)" : removedTitle);

        // ── 4. Decrease quantity to 1, then remove ────────────────────────────
        decreaseToOneAndRemove(driver);

        Thread.sleep(SHORT_WAIT);

        // ── 6. Verify the item is gone ────────────────────────────────────────
        if (!removedTitle.isEmpty()) {
            boolean stillVisible = isTextVisible(driver, removedTitle);
            if (stillVisible) {
                // Scroll up to be sure it's not just off-screen
                scrollToTop(driver);
                Thread.sleep(500);
                stillVisible = isTextVisible(driver, removedTitle);
            }
            if (stillVisible) {
                System.out.printf("   Item \"%s\" still visible after removal%n", removedTitle);
                throw new RuntimeException(
                    "cartRemoveItem: item \"" + removedTitle + "\" still visible after removal");
            }
            System.out.printf("   Item \"%s\" successfully removed from cart%n", removedTitle);
        } else {
            // No title captured — just verify the remove button is gone or cart is empty
            WebElement emptyIndicator = waitFor(driver, "accessibilityId",
                    "cart_shop_more_button", 3_000);
            if (emptyIndicator != null) {
                System.out.println("   Cart is now empty after removal");
            } else {
                System.out.println("   Remove tapped — item title not available for re-check");
            }
        }

        System.out.println("\n── cartRemoveItem completed ───────────────────────\n");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Decreases item quantity to 1, then taps the remove button.
     *
     * Strategy:
     *  1. Read the current quantity from the stepper counter text (looks for a
     *     standalone numeric TextView between the –/+ buttons).
     *  2. Tap the decrease button (qty–1) for each unit above 1, waiting 800 ms
     *     between taps so the counter animates.
     *  3. After reaching 1, tap the remove button (cart_remove_product_button)
     *     OR rely on the last decrease tap triggering a removal confirmation
     *     sheet — either way we confirm removal.
     *
     * Max 20 decrements as a safety net so the loop never spins forever.
     */
    private void decreaseToOneAndRemove(AppiumDriver driver) throws InterruptedException {

        final int MAX_DECREMENTS = 20;

        for (int attempt = 0; attempt < MAX_DECREMENTS; attempt++) {

            // Read quantity from the counter label between –/+ buttons
            int qty = readQuantity(driver);
            System.out.printf("  🔢 Current quantity: %s%n",
                    qty < 0 ? "(unknown)" : String.valueOf(qty));

            if (qty == 1 || qty < 0) {
                // Quantity is 1 (or unreadable) — proceed to removal
                break;
            }

            // qty > 1 — tap decrease
            WebElement decreaseBtn = find(driver, "accessibilityId", "cart_item_decrease_button");
            if (decreaseBtn == null) {
                System.out.println("  ⚠️  cart_item_decrease_button not found — proceeding to remove");
                break;
            }
            System.out.printf("  ➖ Decreasing quantity (was %d)…%n", qty);
            decreaseBtn.click();
            Thread.sleep(800);

            // If a remove confirmation sheet appeared (app showed it early), confirm & return
            WebElement earlyConfirm = find(driver, "accessibilityId", "cart_remove_product_button");
            if (earlyConfirm != null) {
                System.out.println("  📋 Remove confirmation appeared during decrease — confirming");
                earlyConfirm.click();
                Thread.sleep(SHORT_WAIT);
                return;
            }
        }

        // ── Now tap the remove button ──────────────────────────────────────────
        WebElement removeBtn = waitFor(driver, "accessibilityId",
                "cart_remove_product_button", 5_000);
        if (removeBtn == null)
            throw new RuntimeException(
                "cartRemoveItem: cart_remove_product_button not found after quantity decrease");

        System.out.println("  🗑️  Tapping cart_remove_product_button…");
        removeBtn.click();
        Thread.sleep(SHORT_WAIT);

        // ── Handle any confirmation dialog / bottom sheet ──────────────────────
        handleRemoveConfirmation(driver);
    }

    /**
     * Reads the quantity counter text from the cart stepper.
     * Looks for a standalone integer TextView between the –/+ stepper buttons.
     * Returns the quantity, or -1 if it cannot be determined.
     */
    private int readQuantity(AppiumDriver driver) {
        try {
            // The quantity stepper contains a TextView that shows just the count
            // (e.g. "1", "2", "3"). We look for all short numeric TextViews on
            // screen and return the first one that looks like a qty counter.
            List<WebElement> textEls = driver.findElements(
                AppiumBy.androidUIAutomator(
                    "new UiSelector().className(\"android.widget.TextView\")"));
            for (WebElement el : textEls) {
                String txt = el.getText();
                if (txt == null || txt.isBlank()) continue;
                txt = txt.trim();
                // Quantity counters are typically 1–2 digit integers
                if (txt.matches("^[1-9][0-9]?$")) {
                    // Skip text that looks like a section heading number
                    int val = Integer.parseInt(txt);
                    if (val <= 50) return val;  // sanity cap
                }
            }
        } catch (Exception ignored) {}
        return -1;  // unknown
    }

    private void navigateToCart(AppiumDriver driver) throws Exception {
        // If there's already a cart icon visible, use it; otherwise assume we're on cart
        WebElement cartIcon = find(driver, "accessibilityId", "cart");
        if (cartIcon != null) {
            System.out.println("  🛒 Tapping cart icon to open cart");
            cartIcon.click();
            Thread.sleep(NAV_WAIT);
        } else {
            System.out.println("  🛒 Assuming cart is already open");
        }
    }

    /**
     * Find the first product title text visible in the cart.
     * We look for text elements near cart_item_image — any nearby non-price
     * text is likely the product name.
     */
    private String captureFirstCartItemTitle(AppiumDriver driver) {
        try {
            // cart_item_image is the product image; the title is usually the
            // first sibling text element. We scan all text elements and skip
            // price-like ones (starting with ₹ or purely numeric).
            List<WebElement> textEls = driver.findElements(
                AppiumBy.androidUIAutomator(
                    "new UiSelector().className(\"android.widget.TextView\")"));
            for (WebElement el : textEls) {
                String txt = el.getText();
                if (txt == null || txt.isBlank()) continue;
                if (txt.startsWith("₹"))       continue;   // price text
                if (txt.matches("[0-9,. ]+"))  continue;   // numeric-only
                if (txt.length() < 4)          continue;   // too short
                return txt.trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * After tapping the remove button a confirmation bottom-sheet or dialog
     * may appear. Tap the most likely "confirm" button if one is found.
     */
    private void handleRemoveConfirmation(AppiumDriver driver) throws InterruptedException {
        // Look for a "Remove" or "Yes" or "Confirm" text button in a dialog
        String[] confirmLabels = {"Remove", "Yes", "Confirm", "DELETE", "OK"};
        for (String label : confirmLabels) {
            WebElement confirmBtn = find(driver, "text", label);
            if (confirmBtn != null) {
                System.out.printf("  📋 Confirmation dialog found — tapping \"%s\"%n", label);
                confirmBtn.click();
                Thread.sleep(SHORT_WAIT);
                return;
            }
        }
        // No dialog appeared — removal was immediate
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

    private boolean isTextVisible(AppiumDriver driver, String text) {
        try {
            String esc = text.replace("\"", "\\\"");
            return !driver.findElements(AppiumBy.androidUIAutomator(
                "new UiSelector().textContains(\"" + esc + "\")")).isEmpty();
        } catch (Exception e) { return false; }
    }

    /**
     * Verify that the given qaTestTag accessibility IDs (from popdroid TestTags.kt)
     * are present via UIAutomator contentDescription. Logs ✅/⚠️ — never throws.
     */
    private void assertTagPresent(AppiumDriver driver, String... tags) {
        for (String tag : tags) {
            try {
                boolean found = !driver.findElements(AppiumBy.accessibilityId(tag)).isEmpty();
                if (!found) {
                    found = !driver.findElements(AppiumBy.androidUIAutomator(
                        "new UiSelector().description(\"" + tag + "\")")).isEmpty();
                }
                System.out.printf("  %s [TAG] %s%n", found ? "✅" : "⚠️ MISSING", tag);
            } catch (Exception e) {
                System.out.printf("  ⚠️ [TAG] %s — check failed: %s%n", tag, e.getMessage());
            }
        }
    }

    private void scrollToTop(AppiumDriver driver) {
        try {
            Dimension size = driver.manage().window().getSize();
            int cx = size.width / 2;
            int startY = (int)(size.height * 0.25);
            int endY   = (int)(size.height * 0.75);
            for (int i = 0; i < 4; i++) {
                PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
                Sequence seq = new Sequence(finger, 1);
                seq.addAction(finger.createPointerMove(Duration.ZERO,
                        PointerInput.Origin.viewport(), cx, startY));
                seq.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
                seq.addAction(finger.createPointerMove(Duration.ofMillis(300),
                        PointerInput.Origin.viewport(), cx, endY));
                seq.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
                driver.perform(List.of(seq));
            }
        } catch (Exception e) {
            System.out.println("  ⚠️  Scroll to top failed: " + e.getMessage());
        }
    }
}
