package com.popclub.android.actions;

import com.popclub.core.GestureUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

/** cartRemoveItem — removes the first cart item. */
public class CartRemoveAction implements Action {

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

        navigateToCart(driver);

        WebElement cartItemImg = waitFor(driver, "accessibilityId", "cart_item_image", 7_000);
        if (cartItemImg == null)
            throw new RuntimeException(
                "cartRemoveItem: cart appears empty — no cart_item_image found");

        // Verify cart screen tags are present (popdroid TestTags.kt)
        assertTagPresent(driver,
                "cart_item_image",
                "cart_remove_product_button",
                "cart_checkout_pop_shop_button");

        String removedTitle = captureFirstCartItemTitle(driver);
        System.out.println("Removing: " + (removedTitle.isEmpty() ? "(title not captured)" : removedTitle));

        decreaseToOneAndRemove(driver);

        // Poll until remove_product_button disappears (item removed) — replaces fixed sleep
        waitFor(driver, "accessibilityId", "cart_item_image", SHORT_WAIT);

        if (!removedTitle.isEmpty()) {
            boolean stillVisible = isTextVisible(driver, removedTitle);
            if (stillVisible) {
                // Scroll up to be sure it's not just off-screen
                scrollToTop(driver);
                // Poll briefly for stability instead of fixed 500ms
                long settle = System.currentTimeMillis() + 500;
                while (System.currentTimeMillis() < settle) {
                    try { Thread.sleep(100); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); break;
                    }
                }
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
            if (emptyIndicator == null) {
                System.out.println("   Remove tapped — item title not available for re-check");
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Decreases quantity to 1 then taps the remove button. Max 20 decrements as a safety cap. */
    private void decreaseToOneAndRemove(AppiumDriver driver) throws InterruptedException {

        final int MAX_DECREMENTS = 20;

        for (int attempt = 0; attempt < MAX_DECREMENTS; attempt++) {

            int qty = readQuantity(driver);

            if (qty == 1 || qty < 0) {
                // Tap decrease one more time to trigger remove confirmation (handles out-of-stock items)
                WebElement decBtn = find(driver, "accessibilityId", "cart_item_decrease_button");
                if (decBtn != null) {
                    decBtn.click();
                    long confirmDeadline = System.currentTimeMillis() + 2000;
                    while (System.currentTimeMillis() < confirmDeadline) {
                        WebElement confirm = find(driver, "accessibilityId", "cart_remove_product_button");
                        if (confirm == null) confirm = find(driver, "accessibilityId", "cart_unavailable_remove_button");
                        if (confirm != null) { confirm.click(); handleRemoveConfirmation(driver); return; }
                        try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    }
                }
                break;
            }

            // qty > 1 — tap decrease
            WebElement decreaseBtn = find(driver, "accessibilityId", "cart_item_decrease_button");
            if (decreaseBtn == null) break;
            decreaseBtn.click();

            // Poll for either: quantity update or early confirmation sheet (up to 2s)
            // Replaces fixed Thread.sleep(800)
            long decrementDeadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < decrementDeadline) {
                WebElement earlyConfirm = find(driver, "accessibilityId", "cart_remove_product_button");
                if (earlyConfirm != null) {
                    earlyConfirm.click();
                    waitFor(driver, "accessibilityId", "cart_item_image", SHORT_WAIT);
                    return;
                }
                try { Thread.sleep(100); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); break;
                }
            }
        }

        WebElement removeBtn = waitFor(driver, "accessibilityId",
                "cart_remove_product_button", 5_000);
        if (removeBtn == null) {
            // Fallback: out-of-stock items expose a different remove button
            removeBtn = find(driver, "accessibilityId", "cart_unavailable_remove_button");
        }
        if (removeBtn == null)
            throw new RuntimeException(
                "cartRemoveItem: cart_remove_product_button not found after quantity decrease");

        removeBtn.click();
        handleRemoveConfirmation(driver);
    }

    /** Returns the current cart item quantity from the stepper, or -1 if unreadable. */
    private int readQuantity(AppiumDriver driver) {
        try {
            List<WebElement> textEls = driver.findElements(
                AppiumBy.androidUIAutomator(
                    "new UiSelector().className(\"android.widget.TextView\")"));
            for (WebElement el : textEls) {
                String txt = el.getText();
                if (txt == null || txt.isBlank()) continue;
                txt = txt.trim();
                // Quantity counters are typically 1–2 digit integers
                if (txt.matches("^[1-9][0-9]?$")) {
                    int val = Integer.parseInt(txt);
                    if (val <= 50) return val;  // sanity cap
                }
            }
        } catch (Exception ignored) {}
        return -1;  // unknown
    }

    private void navigateToCart(AppiumDriver driver) throws Exception {
        WebElement cartIcon = find(driver, "accessibilityId", "cart");
        if (cartIcon != null) {
            cartIcon.click();
            // Poll until cart_item_image appears instead of fixed 2500ms sleep
            waitFor(driver, "accessibilityId", "cart_item_image", 5_000);
        }
        // if no cart icon found, assume we're already on the cart screen
    }

    private String captureFirstCartItemTitle(AppiumDriver driver) {
        try {
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

    private void handleRemoveConfirmation(AppiumDriver driver) throws InterruptedException {
        String[] confirmLabels = {"Remove", "Yes", "Confirm", "DELETE", "OK"};
        for (String label : confirmLabels) {
            WebElement confirmBtn = find(driver, "text", label);
            if (confirmBtn != null) {
                confirmBtn.click();
                // Poll until confirmation dismisses instead of fixed sleep
                waitFor(driver, "accessibilityId", "cart_item_image", SHORT_WAIT);
                return;
            }
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

    private boolean isTextVisible(AppiumDriver driver, String text) {
        try {
            String esc = text.replace("\"", "\\\"");
            return !driver.findElements(AppiumBy.androidUIAutomator(
                "new UiSelector().textContains(\"" + esc + "\")")).isEmpty();
        } catch (Exception e) { return false; }
    }

    /** Logs whether each qaTestTag accessibility ID is present — never throws. */
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
        GestureUtil.scrollToTop(driver, 4);
    }
}
