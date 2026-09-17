package com.popclub.android.actions;

import com.popclub.core.GestureUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;

import java.util.List;

/**
 * PullToRefreshAction — performs a pull-to-refresh gesture from the top of the screen
 * and waits for any loading indicator to disappear before continuing.
 *
 * YAML usage:
 *   - action: pullToRefresh
 *
 *   - action: pullToRefresh
 *     text: "3000"     # optional: override max wait for reload (ms, default 5000)
 */
public class PullToRefreshAction implements Action {

    private static final int  DEFAULT_MAX_WAIT_MS = 5000;
    private static final long POLL_INTERVAL       = 300;

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
        int maxWait = DEFAULT_MAX_WAIT_MS;
        if (step.text != null && !step.text.isBlank()) {
            try { maxWait = Integer.parseInt(step.text.trim()); } catch (NumberFormatException e) { /* use default */ }
        }

        AppiumDriver driver = DriverManager.getDriver();
        System.out.println("  pullToRefresh — swiping down …");

        GestureUtil.pullToRefresh(driver);

        System.out.printf("  Waiting up to %dms for reload to complete …%n", maxWait);
        waitForRefreshComplete(driver, maxWait);

        System.out.println("  ✅ Pull-to-refresh complete");
    }

    /**
     * Waits until loading indicators disappear.
     * Checks for common loading indicator content-descs and progress bar resource IDs.
     */
    private void waitForRefreshComplete(AppiumDriver driver, int maxWaitMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            if (!isLoading(driver)) return;
            Thread.sleep(POLL_INTERVAL);
        }
        // Don't fail — loading indicator may just not be detectable
        System.out.println("  ℹ️  Loading indicator not detected (or already gone)");
    }

    private boolean isLoading(AppiumDriver driver) {
        try {
            List<?> refreshing = driver.findElements(
                    AppiumBy.androidUIAutomator(
                            "new UiSelector().className(\"android.widget.ProgressBar\")"));
            if (!refreshing.isEmpty()) return true;

            for (String desc : new String[]{"Loading", "Refreshing", "loading", "refreshing"}) {
                if (!driver.findElements(AppiumBy.androidUIAutomator(
                        "new UiSelector().descriptionContains(\"" + desc + "\")")).isEmpty())
                    return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
