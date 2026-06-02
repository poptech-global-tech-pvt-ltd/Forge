package com.popclub.mobile.actions;

import com.popclub.core.TestContext;
import com.popclub.mobile.driver.DriverManager;
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
 *
 * YAML example:
 *
 *   # After navigating to cart, verify the PDP price is still shown
 *   - action: assertStoredText
 *     value: item0_pdp_price
 *
 *   # Verify the product title is visible in the cart
 *   - action: assertStoredText
 *     value: item0_title
 *
 * Fails with a clear message if:
 *   - the key was never stored (captureText not run first), OR
 *   - the stored text is not visible on the current screen after scrolling
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

        String captured = TestContext.getScalarData(storeKey);
        if (captured.isEmpty())
            throw new RuntimeException(
                "assertStoredText: no value stored for key '" + storeKey
                + "' — did captureText run first?");

        AppiumDriver driver = DriverManager.getDriver();

        // ── Tag presence check via UIAutomator (logs, does not fail) ──────────
        System.out.printf("  🔍 assertStoredText [%s] = \"%s\"%n", storeKey, captured);

        // ── Assert text is visible (scroll if needed) ─────────────────────────
        boolean found = findOnScreen(driver, captured);

        if (found) {
            System.out.printf("  ✅ assertStoredText PASS [%s]: \"%s\" visible on screen%n",
                    storeKey, captured);
        } else {
            throw new RuntimeException(
                "assertStoredText FAIL [" + storeKey + "]: \""
                + captured + "\" not found on screen");
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
