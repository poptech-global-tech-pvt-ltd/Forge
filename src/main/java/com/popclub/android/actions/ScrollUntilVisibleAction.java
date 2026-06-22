package com.popclub.android.actions;

import com.popclub.core.Locator;
import com.popclub.core.LocatorUtil;
import com.popclub.core.TestContext;
import com.popclub.core.WaitUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import com.popclub.core.GestureUtil;

import java.time.Duration;
import java.util.List;

/**
 * scrollUntilVisible — Maestro-style intelligent scroll.
 *
 * Scrolls in the specified direction, swipe by swipe, and stops the moment
 * the target element becomes visible on screen.  Combines Maestro's two
 * hallmark behaviours in a single action:
 *
 *   • Zero-wait intelligence  — polls after each swipe; acts immediately
 *                               when the element appears.
 *   • Built-in tolerance      — no fixed sleeps; handles render lag and
 *                               overshooting by checking visibility before
 *                               every swipe attempt.
 *
 * YAML usage:
 * ──────────────────────────────────────────────────────────────────────────
 *   # Minimal — scroll down until element found (all defaults)
 *   - action: scrollUntilVisible
 *     element: checkout_button
 *
 *   # Full control
 *   - action: scrollUntilVisible
 *     element: checkout_button
 *     direction: up          # down (default) | up | left | right
 *     maxScrolls: 20         # give up after N swipes  (default: 15)
 *     timeout: 60            # poll timeout per swipe check (default: test defaultTimeout)
 *
 *   # Horizontal list — swipe left until element visible
 *   - action: scrollUntilVisible
 *     element: product_carousel_item_5
 *     direction: left
 *     maxScrolls: 8
 * ──────────────────────────────────────────────────────────────────────────
 *
 * Strategy (tried in order):
 *   1. UiScrollable scrollIntoView — for accessibilityId locators.
 *      This is Android's native scroll; instant and reliable for vertical lists.
 *      Skipped for left/right direction (UiScrollable only does vertical).
 *   2. Directional swipe loop — generic fallback that works for any element
 *      type, direction, and scrollable container.
 */
public class ScrollUntilVisibleAction implements Action {

    private static final int DEFAULT_MAX_SCROLLS  = 15;
    private static final int SWIPE_DURATION_MS    = 400;
    private static final int POST_SWIPE_SETTLE_MS = 300;   // let UI settle after each swipe

    @Override
    public void perform(Step step) {
        AppiumDriver driver = DriverManager.getDriver();

        // value: "${var}" or value: "literal text" — treat as a text locator so
        // scrollUntilVisible works consistently with tapByText, captureText etc.
        if ((step.locators == null || step.locators.isEmpty())
                && step.value != null && !step.value.isBlank()) {
            com.popclub.core.Locator loc = new com.popclub.core.Locator();
            loc.type  = "text";
            loc.value = step.value.trim();
            step.locators = java.util.List.of(loc);
            System.out.println("[scrollUntilVisible] using value as text locator: \"" + loc.value + "\"");
        }

        if (step.locators == null || step.locators.isEmpty()) {
            throw new RuntimeException("[scrollUntilVisible] No locators — set 'element', 'locator', 'text', or 'value'");
        }

        // Resolve direction (default: down)
        String direction = (step.direction != null && !step.direction.isBlank())
                ? step.direction.trim().toLowerCase()
                : "down";

        // Resolve maxScrolls
        int maxScrolls = step.maxScrolls > 0 ? step.maxScrolls : DEFAULT_MAX_SCROLLS;

        // Resolve timeout for the final blocking wait (if all swipes fail)
        int timeout = step.timeout > 0 ? step.timeout : TestContext.getDefaultTimeout();

        System.out.printf("[scrollUntilVisible] direction=%s  maxScrolls=%d  timeout=%ds  element=%s%n",
                direction, maxScrolls, timeout, step.element);

        // ── Strategy 1: UiScrollable (vertical only, accessibilityId + text) ─────
        if ("down".equals(direction) || "up".equals(direction)) {
            for (Locator loc : step.locators) {
                if ("accessibilityId".equalsIgnoreCase(loc.type)) {
                    if (tryUiScrollableByTag(driver, loc.value, direction)) {
                        System.out.println("[scrollUntilVisible] ✅ Found via UiScrollable (id): " + loc.value);
                        return;
                    }
                    System.out.println("[scrollUntilVisible] UiScrollable missed — falling back to swipe loop");
                    break;
                } else if ("text".equalsIgnoreCase(loc.type) || "uiautomator".equalsIgnoreCase(loc.type)) {
                    if (tryUiScrollableByText(driver, loc.value, direction)) {
                        System.out.println("[scrollUntilVisible] ✅ Found via UiScrollable (text): " + loc.value);
                        return;
                    }
                    System.out.println("[scrollUntilVisible] UiScrollable text missed — falling back to swipe loop");
                    break;
                }
            }
        }

        // ── Strategy 2: Directional swipe loop ───────────────────────────────────
        // Check before first swipe — element may already be on screen
        if (isVisible(driver, step.locators)) {
            System.out.println("[scrollUntilVisible] ✅ Element already visible — no scroll needed");
            return;
        }

        for (int i = 1; i <= maxScrolls; i++) {
            swipe(driver, direction);

            try { Thread.sleep(POST_SWIPE_SETTLE_MS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            if (isVisible(driver, step.locators)) {
                System.out.printf("[scrollUntilVisible] ✅ Element visible after %d swipe(s)%n", i);
                return;
            }

            System.out.printf("[scrollUntilVisible] Swipe %d/%d — element not yet visible%n", i, maxScrolls);
        }

        // All swipes exhausted — do a final blocking poll in case the element is
        // loading slowly after the last swipe (network, lazy rendering, etc.)
        System.out.println("[scrollUntilVisible] Max swipes reached — waiting up to "
                + timeout + "s for element to appear");
        WaitUtil.pollUntilVisible(driver, step.locators, timeout);
        System.out.println("[scrollUntilVisible] ✅ Element appeared after final wait");
    }

    // ── UiScrollable (Android-native, vertical only) ─────────────────────────────

    /** Scroll by accessibilityId / content-desc (qaTestTag). */
    private boolean tryUiScrollableByTag(AppiumDriver driver, String accessibilityId, String direction) {
        try {
            String safe = accessibilityId.replace("\"", "\\\"");
            String selector = buildUiScrollable(direction,
                    "new UiSelector().description(\"" + safe + "\")");
            driver.findElement(AppiumBy.androidUIAutomator(selector));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Scroll by visible text — uses textContains so partial matches work.
     * e.g. text: "Add to Cart" matches a button whose full label is "Add to Cart (3)".
     */
    private boolean tryUiScrollableByText(AppiumDriver driver, String text, String direction) {
        try {
            String safe = text.replace("\"", "\\\"");
            // Try exact text first, then textContains as fallback
            String[] selectors = {
                "new UiSelector().text(\"" + safe + "\")",
                "new UiSelector().textContains(\"" + safe + "\")"
            };
            for (String inner : selectors) {
                try {
                    driver.findElement(AppiumBy.androidUIAutomator(
                            buildUiScrollable(direction, inner)));
                    return true;
                } catch (Exception ignored) {}
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Builds a UiScrollable scrollIntoView selector for the given direction. */
    private String buildUiScrollable(String direction, String innerSelector) {
        String base = "new UiScrollable(new UiSelector().scrollable(true))";
        if ("up".equals(direction)) {
            // Reverse the scroll direction so UiScrollable searches upward
            base += ".setMaxSearchSwipes(20).scrollBackward()";
        }
        return base + ".scrollIntoView(" + innerSelector + ")";
    }

    // ── Generic directional swipe ─────────────────────────────────────────────────

    private void swipe(AppiumDriver driver, String direction) {
        try {
            GestureUtil.swipe(driver, direction, SWIPE_DURATION_MS);
        } catch (Exception e) {
            System.out.println("[scrollUntilVisible] swipe failed: " + e.getMessage());
        }
    }

    // ── Instant visibility check (no poll — check right now) ─────────────────────

    private boolean isVisible(AppiumDriver driver, List<Locator> locators) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            for (Locator loc : locators) {
                try {
                    var found = driver.findElements(LocatorUtil.getLocator(loc));
                    if (!found.isEmpty() && found.get(0).isDisplayed()) return true;
                } catch (Exception ignored) {}
            }
            return false;
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }
}
