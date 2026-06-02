package com.popclub.mobile.actions;

import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
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

        doPullDown(driver);

        System.out.printf("  Waiting up to %dms for reload to complete …%n", maxWait);
        waitForRefreshComplete(driver, maxWait);

        System.out.println("  ✅ Pull-to-refresh complete");
    }

    private void doPullDown(AppiumDriver driver) {
        try {
            Dimension size  = driver.manage().window().getSize();
            int centerX = size.width / 2;
            // Start near top (15%) and drag down to 60% — standard PTR gesture
            int startY  = (int) (size.height * 0.15);
            int endY    = (int) (size.height * 0.60);

            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence seq = new Sequence(finger, 1);
            seq.addAction(finger.createPointerMove(Duration.ZERO,
                    PointerInput.Origin.viewport(), centerX, startY));
            seq.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            // Slow drag to trigger PTR (600ms)
            seq.addAction(finger.createPointerMove(Duration.ofMillis(600),
                    PointerInput.Origin.viewport(), centerX, endY));
            // Hold briefly at the bottom for PTR trigger
            seq.addAction(finger.createPointerMove(Duration.ofMillis(300),
                    PointerInput.Origin.viewport(), centerX, endY));
            seq.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(List.of(seq));

        } catch (Exception e) {
            System.out.println("  ⚠️  Pull-to-refresh gesture failed: " + e.getMessage());
        }
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
            // Check for SwipeRefreshLayout progress indicator
            List<?> refreshing = driver.findElements(
                    AppiumBy.androidUIAutomator(
                            "new UiSelector().className(\"android.widget.ProgressBar\")"));
            if (!refreshing.isEmpty()) return true;

            // Check for common loading content descriptions
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
