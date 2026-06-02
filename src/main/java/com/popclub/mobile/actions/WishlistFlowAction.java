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
 * WishlistFlowAction — verifies the add-to-wishlist → wishlist page flow.
 *
 * Flow:
 *   1. Search for the given query (value) — waits for the product list
 *   2. Tap product_list_item_0 to open its PDP
 *   3. Capture the product title
 *   4. Tap the wishlist heart button (product_details_wishlist_button)
 *   5. Press Back → return to the product list
 *   6. Navigate to the Wishlist screen via the wishlist icon
 *   7. Assert the captured product title is visible in the wishlist
 *
 * YAML usage:
 *   - action: verifyWishlistFlow
 *     value: yogabar    # search query
 *
 * Requires: app is on the Shop tab with the search icon visible.
 * State after: Wishlist screen.
 */
public class WishlistFlowAction implements Action {

    private static final long NAV_WAIT   = 2500;
    private static final long SHORT_WAIT = 1000;
    private static final long POLL_SLEEP = 300;
    private static final int  MAX_SCROLLS = 5;

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

        String query = step.value != null ? step.value.trim() : "yogabar";
        AppiumDriver driver = DriverManager.getDriver();

        System.out.println("\n══════════════════════════════════════════════════");
        System.out.printf ("  verifyWishlistFlow: \"%s\"%n", query);
        System.out.println("══════════════════════════════════════════════════");

        // ── 1. Search ──────────────────────────────────────────────────────────
        doSearch(driver, query);

        // ── 2. Tap first product to open PDP ─────────────────────────────────
        WebElement card = waitFor(driver, "accessibilityId", "product_list_item_0", 6_000);
        if (card == null)
            throw new RuntimeException(
                "verifyWishlistFlow: product_list_item_0 not found on search results");
        card.click();
        Thread.sleep(NAV_WAIT);

        // ── 3. Capture product title ───────────────────────────────────────────
        String title = capturePdpTitle(driver);
        System.out.printf("  📦 PDP title: \"%s\"%n", title.isEmpty() ? "(not captured)" : title);

        // ── 4. Tap the wishlist (heart) button ────────────────────────────────
        // Verify PDP tags are present before interacting (popdroid TestTags.kt)
        assertTagPresent(driver,
                "product_details_title",
                "product_details_wishlist_button",
                "product_details_bottom_sticky_bar_button");

        WebElement wishlistBtn = waitFor(driver, "accessibilityId",
                "product_details_wishlist_button", 5_000);
        if (wishlistBtn == null)
            throw new RuntimeException(
                "verifyWishlistFlow: product_details_wishlist_button not found on PDP");

        wishlistBtn.click();
        Thread.sleep(SHORT_WAIT);
        System.out.println("  ❤️  Wishlist button tapped");

        // ── 5. Press Back → return to product list ────────────────────────────
        pressBack(driver);
        Thread.sleep(SHORT_WAIT);

        // Confirm we're back on the product list
        WebElement filterBtn = waitFor(driver, "accessibilityId",
                "product_list_filter_button", 5_000);
        if (filterBtn == null)
            System.out.println("  ⚠️  product_list_filter_button not visible after back — continuing");

        // ── 6. Navigate to Wishlist screen ────────────────────────────────────
        navigateToWishlist(driver);

        // ── 7. Verify the product title is visible in the wishlist ─────────────
        if (title.isEmpty()) {
            // No title captured — just confirm the wishlist has at least one item
            WebElement itemCard = waitFor(driver, "accessibilityId",
                    "wishlist_item_card_compose_row", 5_000);
            if (itemCard != null) {
                System.out.println("  ✅ Wishlist has items (title not available for exact check)");
            } else {
                throw new RuntimeException(
                    "verifyWishlistFlow: wishlist appears empty after adding item");
            }
        } else {
            boolean found = findOnScreen(driver, title);
            if (found) {
                System.out.printf("  ✅ \"%s\" found in Wishlist%n", title);
            } else {
                throw new RuntimeException(
                    "verifyWishlistFlow: \"" + title + "\" NOT found in Wishlist");
            }
        }

        System.out.println("\n── verifyWishlistFlow completed ──────────────────\n");
    }

    // ── Search ─────────────────────────────────────────────────────────────────

    private void doSearch(AppiumDriver driver, String query) throws Exception {

        WebElement icon = waitFor(driver, "accessibilityId", "search", 5_000);
        if (icon == null)
            throw new RuntimeException(
                "verifyWishlistFlow: Search icon not found — is the app on the Shop tab?");
        icon.click();
        Thread.sleep(SHORT_WAIT);

        // Primary qaTestTag from popdroid: search_products_search_input
        WebElement input = waitFor(driver, "accessibilityId", "search_products_search_input", 4_000);
        if (input == null)
            input = waitFor(driver, "accessibilityId", "Search", 3_000);
        if (input == null)
            throw new RuntimeException("verifyWishlistFlow: Search input not visible");
        input.sendKeys(query);
        Thread.sleep(500);

        ((AndroidDriver) driver).pressKey(new KeyEvent(AndroidKey.ENTER));
        Thread.sleep(NAV_WAIT);

        WebElement filterBtn = waitFor(driver, "accessibilityId",
                "product_list_filter_button", 10_000);
        if (filterBtn == null)
            throw new RuntimeException(
                "verifyWishlistFlow: Product list did not load for \"" + query + "\"");

        // Verify product-list tags are present (popdroid TestTags.kt)
        assertTagPresent(driver,
                "product_list_filter_button",
                "product_list_sort_button",
                "product_list_item_0");
        System.out.printf("  ✅ Search results loaded for: \"%s\"%n%n", query);
    }

    // ── Navigate to Wishlist ───────────────────────────────────────────────────

    private void navigateToWishlist(AppiumDriver driver) throws Exception {

        System.out.println("  ── Navigating to Wishlist ──────────────────────");

        // Primary: tap wishlist icon (bottom nav or top bar)
        // Note: the accessibility ID in shop.yaml is "wishlsit" (typo retained as-is)
        WebElement wishlistIcon = find(driver, "accessibilityId", "wishlsit");
        if (wishlistIcon != null) {
            System.out.println("  🔖 Tapping wishlist icon");
            wishlistIcon.click();
            Thread.sleep(NAV_WAIT);
            return;
        }

        // Fallback: look for a "Wishlist" text label
        WebElement wishlistLabel = find(driver, "text", "Wishlist");
        if (wishlistLabel != null) {
            System.out.println("  🔖 Tapping 'Wishlist' text label");
            wishlistLabel.click();
            Thread.sleep(NAV_WAIT);
            return;
        }

        throw new RuntimeException(
            "verifyWishlistFlow: Cannot navigate to Wishlist — icon and text label both missing");
    }

    // ── PDP title capture ──────────────────────────────────────────────────────

    private String capturePdpTitle(AppiumDriver driver) throws InterruptedException {
        WebElement titleEl = waitFor(driver, "accessibilityId", "product_details_title", 5_000);
        if (titleEl != null) {
            String t = titleEl.getText();
            return t != null ? t.trim() : "";
        }
        return "";
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

    private boolean isTextVisible(AppiumDriver driver, String text) {
        try {
            String esc = text.replace("\"", "\\\"");
            return !driver.findElements(AppiumBy.androidUIAutomator(
                "new UiSelector().textContains(\"" + esc + "\")")).isEmpty();
        } catch (Exception e) { return false; }
    }

    private boolean findOnScreen(AppiumDriver driver, String text) throws InterruptedException {
        for (int s = 0; s <= MAX_SCROLLS; s++) {
            if (isTextVisible(driver, text)) return true;
            if (s < MAX_SCROLLS) { scrollDown(driver); Thread.sleep(500); }
        }
        return false;
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

    /**
     * Verify that the given qaTestTag accessibility IDs (from popdroid TestTags.kt)
     * are present in the current view hierarchy via UIAutomator contentDescription lookup.
     * Logs ✅ / ⚠️ per tag — never throws, so missing tags are logged but don't abort.
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
}
