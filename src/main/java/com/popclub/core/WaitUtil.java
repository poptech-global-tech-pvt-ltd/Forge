package com.popclub.core;

import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.util.List;

public class WaitUtil {

    /**
     * Zero-wait intelligence — Maestro-style poll loop.
     *
     * Instead of a single blocking WebDriverWait, this method polls the UI
     * tree every {@code POLL_INTERVAL_MS} milliseconds and acts the instant
     * the element becomes visible.  This means:
     *
     *   • No unnecessary sleep when the UI is already ready (common case).
     *   • Tolerates transient StaleElement / render glitches automatically.
     *   • Timeout is configurable per step (via {@code step.timeout}) or
     *     falls back to the test-level {@code defaultTimeout} in TestCase YAML.
     *
     * Equivalent to Maestro's built-in "zero-wait" behaviour for every command.
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
        final int POLL_INTERVAL_MS = 500;
        long deadline = System.currentTimeMillis() + (long) timeoutSeconds * 1000;

        // Disable Appium's own implicit wait so our poll controls the timing
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));

        try {
            while (System.currentTimeMillis() < deadline) {
                for (Locator locator : locators) {
                    try {
                        By by = LocatorUtil.getLocator(locator);
                        List<WebElement> found = driver.findElements(by);
                        if (!found.isEmpty() && found.get(0).isDisplayed()) {
                            System.out.println("  ✓ found [" + locator.type + "] in "
                                    + (timeoutSeconds * 1000 - (deadline - System.currentTimeMillis()))
                                    / 1000.0 + "s");
                            return found.get(0);
                        }
                    } catch (Exception ignored) {
                        // StaleElement, NoSuchElement — keep polling
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
            // Restore a reasonable implicit wait for any Appium calls outside WaitUtil
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }

        throw new RuntimeException(
                "Element not found after polling " + timeoutSeconds + "s — locators: " + locators);
    }

    /**
     * Convenience overload — reads the effective timeout from {@link TestContext}.
     * Used by actions that receive a resolved step timeout via
     * {@code TestExecutor.resolveStepTimeout(step)}.
     */
    public static WebElement pollUntilVisible(AppiumDriver driver, List<Locator> locators) {
        return pollUntilVisible(driver, locators, TestContext.getDefaultTimeout());
    }

    /**
     * Legacy entry point — kept for backward compatibility and used by actions
     * that haven't been migrated yet.  Internally delegates to
     * {@link #pollUntilVisible} so all callers benefit from the poll loop.
     */
    public static WebElement waitForElement(AppiumDriver driver, List<Locator> locators) {
        return pollUntilVisible(driver, locators, TestContext.getDefaultTimeout());
    }

    /**
     * waitForElement with an explicit timeout — used by callers that already
     * compute the effective timeout (e.g. WaitForAction, TapAction).
     */
    public static WebElement waitForElement(AppiumDriver driver, List<Locator> locators,
                                            int timeoutSeconds) {
        return pollUntilVisible(driver, locators, timeoutSeconds);
    }

    /**
     * Quick non-blocking check — returns {@code null} if element is absent.
     * Used for {@code shouldExist: false} assertions and ifPresent probes.
     * Does NOT use the poll loop — returns immediately.
     */
    public static WebElement findElementQuick(AppiumDriver driver, List<Locator> locators) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0));
            for (Locator locator : locators) {
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
