package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.core.WaitUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;

/**
 * WaitForGoneAction — polls until the element disappears from screen.
 * Useful for waiting out loading spinners and skeleton screens.
 *
 * YAML usage:
 *   - action: waitForGone
 *     element: shop_loading_spinner
 *     timeout: 15
 *
 *   - action: waitForGone
 *     text: "Loading..."
 */
public class WaitForGoneAction implements Action {

    private static final int POLL_INTERVAL_MS = 500;

    @Override
    public void perform(Step step) {
        AppiumDriver driver = DriverManager.getDriver();
        int timeout = step.timeout > 0 ? step.timeout : TestContext.getDefaultTimeout();
        long deadline = System.currentTimeMillis() + (long) timeout * 1000;

        System.out.println("[waitForGone] Waiting up to " + timeout + "s for element to disappear");

        while (System.currentTimeMillis() < deadline) {
            if (WaitUtil.findElementQuick(driver, step.locators) == null) {
                System.out.println("[waitForGone] Element gone");
                return;
            }
            try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }

        System.out.println("[waitForGone] Element still present after " + timeout + "s — continuing");
    }
}
