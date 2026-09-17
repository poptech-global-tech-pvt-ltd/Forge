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
import java.util.List;

/** verifyWishlistFlow — searches, wishlists the first result, and asserts it appears in wishlist. */
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

        doSearch(driver, query);

        WebElement card = waitFor(driver, "accessibilityId", "product_list_item_0", 6_000);
        if (card == null)
            throw new RuntimeException(
                "verifyWishlistFlow: product_list_item_0 not found on search results");
        card.click();
        Thread.sleep(NAV_WAIT);

        String title = capturePdpTitle(driver);

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

        pressBack(driver);
        Thread.sleep(SHORT_WAIT);

        waitFor(driver, "accessibilityId", "product_list_filter_button", 5_000);

        navigateToWishlist(driver);

        if (title.isEmpty()) {
            WebElement itemCard = waitFor(driver, "accessibilityId",
                    "wishlist_item_card_compose_row", 5_000);
            if (itemCard == null) {
                throw new RuntimeException(
                    "verifyWishlistFlow: wishlist appears empty after adding item");
            }
        } else {
            boolean found = findOnScreen(driver, title);
            if (!found) {
                throw new RuntimeException(
                    "verifyWishlistFlow: \"" + title + "\" NOT found in Wishlist");
            }
        }
    }

    private void doSearch(AppiumDriver driver, String query) throws Exception {

        WebElement icon = waitFor(driver, "accessibilityId", "search", 5_000);
        if (icon == null)
            throw new RuntimeException(
                "verifyWishlistFlow: Search icon not found — is the app on the Shop tab?");
        icon.click();
        Thread.sleep(SHORT_WAIT);

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

        assertTagPresent(driver,
                "product_list_filter_button",
                "product_list_sort_button",
                "product_list_item_0");
    }

    private void navigateToWishlist(AppiumDriver driver) throws Exception {
        // accessibility ID is "wishlsit" — typo is intentional, matches app code
        WebElement wishlistIcon = find(driver, "accessibilityId", "wishlsit");
        if (wishlistIcon != null) {
            wishlistIcon.click();
            Thread.sleep(NAV_WAIT);
            return;
        }

        // fallback: look for a "Wishlist" text label
        WebElement wishlistLabel = find(driver, "text", "Wishlist");
        if (wishlistLabel != null) {
            wishlistLabel.click();
            Thread.sleep(NAV_WAIT);
            return;
        }

        throw new RuntimeException(
            "verifyWishlistFlow: Cannot navigate to Wishlist — icon and text label both missing");
    }

    private String capturePdpTitle(AppiumDriver driver) throws InterruptedException {
        WebElement titleEl = waitFor(driver, "accessibilityId", "product_details_title", 5_000);
        if (titleEl != null) {
            String t = titleEl.getText();
            return t != null ? t.trim() : "";
        }
        return "";
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
}
