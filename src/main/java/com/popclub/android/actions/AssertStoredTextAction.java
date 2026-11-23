package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.List;

/**
 * AssertStoredTextAction — asserts that a value previously captured by
 * captureText is visible somewhere on the current screen.
 *
 * Scrolls up to 4 times to find the text before failing.
 *
 * Fields:
 *   value  — required; the storage key set by a prior captureText step
 *   text   — optional; set to "absent" to assert the text is NOT on screen
 *
 * YAML example:
 *
 *   # Verify the product title is visible in the cart
 *   - action: assertStoredText
 *     value: item0_title
 *
 *   # Verify the product title is gone after removal
 *   - action: assertStoredText
 *     value: item0_title
 *     text: absent
 *
 * Fails with a clear message if:
 *   - the key was never stored (captureText not run first), OR
 *   - (default) the stored text is not visible on the current screen after scrolling, OR
 *   - (absent mode) the stored text IS still visible on screen
 */
public class AssertStoredTextAction implements Action {

    private static final int  MAX_SCROLLS = 4;
    private static final long SCROLL_WAIT = 500;

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

        String storeKey = step.value;
        if (storeKey == null || storeKey.isBlank())
            throw new RuntimeException(
                "assertStoredText: 'value' (storage key) is required");

        // ── Variable-to-variable comparison mode ─────────────────────────────
        // When `variable:` is also set, compare the two stored values directly
        // without doing a screen search — useful for cross-page title/price checks.
        //
        //   - action: assertStoredText
        //     variable: pdp_product_title   # actual (captured from screen)
        //     value:    product_title       # expected (from API / earlier step)
        if (step.variable != null && !step.variable.isBlank()) {
            String actual   = TestContext.getScalarData(step.variable);
            String expected = TestContext.getScalarData(storeKey);
            System.out.printf("  🔍 assertStoredText [%s] vs [%s]%n  actual:   \"%s\"%n  expected: \"%s\"%n",
                    step.variable, storeKey, actual, expected);
            if (actual.equalsIgnoreCase(expected.trim())) {
                System.out.printf("  ✅ assertStoredText PASS: \"%s\" matches \"%s\"%n",
                        step.variable, storeKey);
            } else {
                throw new RuntimeException(
                    "assertStoredText FAIL: title mismatch"
                    + "\n  [" + step.variable + "] = \"" + actual + "\""
                    + "\n  [" + storeKey       + "] = \"" + expected + "\"");
            }
            return;
        }

        // ── Screen-search mode (default) ──────────────────────────────────────
        String captured = TestContext.getScalarData(storeKey);
        if (captured.isEmpty())
            throw new RuntimeException(
                "assertStoredText: no value stored for key '" + storeKey
                + "' — did captureText run first?");

        boolean absentMode = "absent".equalsIgnoreCase(step.text);
        AppiumDriver driver = DriverManager.getDriver();

        System.out.printf("  🔍 assertStoredText [%s] = \"%s\" (mode: %s)%n",
                storeKey, captured, absentMode ? "absent" : "present");

        boolean found = findOnScreen(driver, captured);

        if (absentMode) {
            if (!found) {
                System.out.printf("  ✅ assertStoredText PASS [%s]: \"%s\" not on screen%n",
                        storeKey, captured);
            } else {
                throw new RuntimeException(
                    "assertStoredText FAIL [" + storeKey + "]: \""
                    + captured + "\" still visible on screen after expected removal");
            }
        } else {
            if (found) {
                System.out.printf("  ✅ assertStoredText PASS [%s]: \"%s\" visible on screen%n",
                        storeKey, captured);
            } else {
                throw new RuntimeException(
                    "assertStoredText FAIL [" + storeKey + "]: \""
                    + captured + "\" not found on screen");
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean findOnScreen(AppiumDriver driver, String text) throws InterruptedException {
        for (int s = 0; s <= MAX_SCROLLS; s++) {
            if (isTextVisible(driver, text)) return true;
            // Also try with ₹/comma stripped (price resilience: "₹1,299" vs "1,299")
            if (text.startsWith("₹") && isTextVisible(driver, text.substring(1))) return true;
            if (s < MAX_SCROLLS) {
                scrollDown(driver);
                Thread.sleep(SCROLL_WAIT);
            }
        }
        return false;
    }

    private boolean isTextVisible(AppiumDriver driver, String text) {
        try {
            String esc = text.replace("\"", "\\\"");
            return !driver.findElements(AppiumBy.androidUIAutomator(
                "new UiSelector().textContains(\"" + esc + "\")")).isEmpty();
        } catch (Exception e) { return false; }
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
}
