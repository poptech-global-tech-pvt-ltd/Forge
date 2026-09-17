package com.popclub.core;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;

public class WaitUtil {

    /**
     * Batch poll — Maestro-style "zero-wait" using a single UI-tree dump per poll.
     *
     * Instead of firing one HTTP request per locator per poll interval, we fetch
     * the full page source ONCE per interval and search it in-memory for all
     * locators. This cuts HTTP calls by up to N× (where N = number of fallback
     * locators) and eliminates the extra isDisplayed() round-trip.
     *
     * HTTP calls per poll:
     *   Before: N calls (one findElements per locator) + isDisplayed call
     *   After:  1 call  (GET /source) + in-memory XML search
     *
     * Falls back to individual findElements if page source is unavailable.
     *
     * @param driver          Appium driver
     * @param locators        Ordered list of locators to try (accessibilityId first)
     * @param timeoutSeconds  How long to poll before giving up
     * @return the first visible WebElement found
     * @throws RuntimeException if no element is found within the timeout
     */
    public static WebElement pollUntilVisible(AppiumDriver driver,
                                              List<Locator> locators,
                                              int timeoutSeconds) {
        final int POLL_INTERVAL_MS = 100;
        long deadline = System.currentTimeMillis() + (long) timeoutSeconds * 1000;

        // Disable Appium's own implicit wait so our poll controls the timing
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));

        try {
            while (System.currentTimeMillis() < deadline) {
                // ── Single HTTP call: fetch the entire UI tree ────────────────
                String pageSource = null;
                try {
                    pageSource = driver.getPageSource();
                } catch (Exception ignored) {
                    // page source unavailable — fall through to per-locator fallback
                }

                if (pageSource != null) {
                    // Runtime permission dialogs (e.g. notifications) can pop up mid-test
                    // on a reused (noReset:true) app — dismiss them so polling can continue.
                    dismissPermissionDialogIfPresent(driver, pageSource);

                    // Search in-memory — no HTTP calls
                    for (Locator locator : locators) {
                        if (isPresentInSource(pageSource, locator)) {
                            // Confirm with a quick findElement to get the WebElement reference
                            try {
                                By by = LocatorUtil.getLocator(locator);
                                List<WebElement> found = driver.findElements(by);
                                if (!found.isEmpty()) {
                                    long elapsed = System.currentTimeMillis() - (deadline - (long) timeoutSeconds * 1000);
                                    System.out.println("  ✓ found [" + locator.type + "] in "
                                            + elapsed / 1000.0 + "s (batch)");
                                    return found.get(0);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                } else {
                    // Fallback: individual findElements per locator (original behaviour)
                    for (Locator locator : locators) {
                        try {
                            By by = LocatorUtil.getLocator(locator);
                            List<WebElement> found = driver.findElements(by);
                            if (!found.isEmpty() && found.get(0).isDisplayed()) {
                                long elapsed = System.currentTimeMillis() - (deadline - (long) timeoutSeconds * 1000);
                                System.out.println("  ✓ found [" + locator.type + "] in "
                                        + elapsed / 1000.0 + "s (fallback)");
                                return found.get(0);
                            }
                        } catch (Exception ignored) {}
                    }
                }

                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        throw new RuntimeException(
                "Element not found after polling " + timeoutSeconds + "s — locators: " + locators);
    }

    /** Known system-dialog dismiss buttons that can pop up mid-test on a reused (noReset:true) app. */
    private static final String[] BLOCKING_DIALOG_BUTTON_IDS = {
            "com.android.permissioncontroller:id/permission_deny_button", // "Allow notifications?" — Don't allow
            "com.google.android.gms:id/cancel",                          // Google phone-number chooser — Cancel
    };

    /**
     * Dismisses known system dialogs (permission prompts, Google account/phone choosers) if
     * one is currently on screen, so it doesn't block subsequent element polling.
     */
    private static void dismissPermissionDialogIfPresent(AppiumDriver driver, String pageSource) {
        for (String buttonId : BLOCKING_DIALOG_BUTTON_IDS) {
            if (!pageSource.contains(buttonId)) continue;
            try {
                // By.id() only searches the foreground app's window — these dialogs are separate
                // system overlay windows, so they need a UiSelector-based lookup instead, which
                // searches across all windows (same engine `adb shell uiautomator dump` uses).
                List<WebElement> buttons = driver.findElements(AppiumBy.androidUIAutomator(
                        "new UiSelector().resourceId(\"" + buttonId + "\")"));
                if (!buttons.isEmpty()) {
                    buttons.get(0).click();
                    System.out.println("  ⚠️  Dismissed blocking dialog mid-poll: " + buttonId);
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Fast in-memory check — does the page source XML contain this locator?
     * No HTTP call — searches the already-fetched tree string.
     */
    private static boolean isPresentInSource(String pageSource, Locator locator) {
        if (pageSource == null || locator == null || locator.value == null) return false;
        String val = locator.value;
        switch (locator.type == null ? "" : locator.type.toLowerCase()) {
            case "accessibilityid":
                // <* content-desc="val" ...> or resource-id contains val
                return pageSource.contains("content-desc=\"" + val + "\"")
                    || pageSource.contains("resource-id=\"" + val + "\"");
            case "id":
                return pageSource.contains("resource-id=\"" + val + "\"");
            case "xpath":
                // Can't evaluate XPath on a string — fall back to contains heuristic
                return pageSource.contains(val);
            case "text":
            case "uiautomator":
                return pageSource.contains(val);
            default:
                return pageSource.contains(val);
        }
    }

    /**
     * Convenience overload — reads the effective timeout from {@link TestContext}.
     */
    public static WebElement pollUntilVisible(AppiumDriver driver, List<Locator> locators) {
        return pollUntilVisible(driver, locators, TestContext.getDefaultTimeout());
    }

    /**
     * Legacy entry point — kept for backward compatibility.
     */
    public static WebElement waitForElement(AppiumDriver driver, List<Locator> locators) {
        return pollUntilVisible(driver, locators, TestContext.getDefaultTimeout());
    }

    /**
     * waitForElement with an explicit timeout.
     */
    public static WebElement waitForElement(AppiumDriver driver, List<Locator> locators,
                                            int timeoutSeconds) {
        return pollUntilVisible(driver, locators, timeoutSeconds);
    }

    /**
     * Quick non-blocking check — returns {@code null} if element is absent.
     * Used for {@code shouldExist: false} assertions and ifPresent probes.
     */
    public static WebElement findElementQuick(AppiumDriver driver, List<Locator> locators) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));

            // Try batch source check first
            String pageSource = null;
            try { pageSource = driver.getPageSource(); } catch (Exception ignored) {}

            for (Locator locator : locators) {
                if (pageSource != null && !isPresentInSource(pageSource, locator)) continue;
                try {
                    By by = LocatorUtil.getLocator(locator);
                    List<WebElement> found = driver.findElements(by);
                    if (!found.isEmpty()) return found.get(0);
                } catch (Exception ignored) {}
            }
            return null;
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }
}
