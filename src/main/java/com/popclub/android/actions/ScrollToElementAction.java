package com.popclub.android.actions;

import com.popclub.core.GestureUtil;
import com.popclub.core.WaitUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.core.Locator;
import com.popclub.core.LocatorUtil;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * ScrollToElementAction — scrolls the screen until the target element is visible,
 * then returns so subsequent steps can interact with it.
 *
 * Strategy (tried in order):
 *   1. UiScrollable scrollIntoView — for accessibilityId locators (fastest, native Android)
 *   2. Swipe-up loop — generic fallback: swipes upward up to MAX_SWIPES times, checking
 *      for the element after each swipe.
 *
 * YAML usage:
 *   - action: scrollTo
 *     element: product_list_item_${product_index}
 *
 *   - action: scrollTo
 *     locator: my_accessibility_id
 */
public class ScrollToElementAction implements Action {

    private static final int MAX_SWIPES        = 15;
    private static final int SWIPE_DURATION_MS = 400;

    @Override
    public void perform(Step step) {
        AppiumDriver driver = DriverManager.getDriver();

        if (step.locators == null || step.locators.isEmpty()) {
            throw new RuntimeException("[scrollTo] No locators — set 'element' or 'locator'");
        }

        // ── Strategy 1: UiScrollable for accessibilityId / content-desc ─────────
        for (Locator loc : step.locators) {
            if ("accessibilityId".equalsIgnoreCase(loc.type)) {
                if (tryUiScrollable(driver, loc.value)) {
                    System.out.println("[scrollTo] ✅ Scrolled to element via UiScrollable: " + loc.value);
                    return;
                }
                System.out.println("[scrollTo] UiScrollable did not find: " + loc.value + " — trying swipe loop");
                break;
            }
        }

        // ── Strategy 2: Manual swipe-up loop ─────────────────────────────────────
        System.out.println("[scrollTo] Starting swipe-up loop (max " + MAX_SWIPES + " swipes)…");
        for (int i = 1; i <= MAX_SWIPES; i++) {
            if (isElementVisible(driver, step.locators)) {
                System.out.println("[scrollTo] ✅ Element visible after " + (i - 1) + " swipe(s)");
                return;
            }
            swipeUp(driver);
            System.out.println("[scrollTo] Swipe " + i + "/" + MAX_SWIPES);
            try { Thread.sleep(300); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        // Final check after last swipe
        if (isElementVisible(driver, step.locators)) {
            System.out.println("[scrollTo] ✅ Element visible after final swipe");
            return;
        }

        // Element not found — let WaitUtil throw the standard timeout error
        System.out.println("[scrollTo] Element not found after " + MAX_SWIPES
                + " swipes — delegating to WaitUtil for timeout error");
        WaitUtil.waitForElement(driver, step.locators);
    }

    // ── UiScrollable ─────────────────────────────────────────────────────────────

    /**
     * Uses Android UiScrollable to natively scroll a scrollable container until
     * an element with the given content-desc (accessibilityId) is found.
     * Returns true if the element became visible, false if not found.
     */
    private boolean tryUiScrollable(AppiumDriver driver, String accessibilityId) {
        try {
            // Escape any double-quotes in the accessibility ID to avoid breaking the selector
            String safe = accessibilityId.replace("\"", "\\\"");
            String selector =
                    "new UiScrollable(new UiSelector().scrollable(true))" +
                    ".scrollIntoView(new UiSelector().description(\"" + safe + "\"))";
            driver.findElement(AppiumBy.androidUIAutomator(selector));
            return true;
        } catch (Exception e) {
            System.out.println("[scrollTo] UiScrollable failed: " + e.getMessage());
            return false;
        }
    }

    // ── Swipe up (scroll down the list) ──────────────────────────────────────────

    private void swipeUp(AppiumDriver driver) {
        try {
            GestureUtil.swipe(driver, "up", SWIPE_DURATION_MS);
        } catch (Exception e) {
            System.out.println("[scrollTo] swipeUp failed: " + e.getMessage());
        }
    }

    // ── Quick visibility check (no wait) ─────────────────────────────────────────

    private boolean isElementVisible(AppiumDriver driver, List<Locator> locators) {
        for (Locator loc : locators) {
            try {
                List<?> elements = driver.findElements(LocatorUtil.getLocator(loc));
                if (!elements.isEmpty()) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }
}
