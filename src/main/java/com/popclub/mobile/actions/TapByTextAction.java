package com.popclub.mobile.actions;

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
 * TapByTextAction — scrolls the screen until the given text is visible, then taps it.
 *
 * Works with Jetpack Compose via UIAutomator's accessibility/semantics tree.
 * Use after verifyCLP has stored all section + item texts in TestContext.
 *
 * YAML usage:
 *   - action: tapByText
 *     value: "Trending Now"       # exact or partial text visible on screen
 *
 *   - action: tapByText
 *     value: "Nike Air Max"       # item title inside a carousel
 */
public class TapByTextAction implements Action {

    private static final int  MAX_SCROLLS = 10;
    private static final long SCROLL_WAIT = 500;

    @Override
    public void perform(Step step) {
        if (step.value == null || step.value.isBlank()) {
            throw new RuntimeException("tapByText requires a non-empty value");
        }

        AppiumDriver driver = DriverManager.getDriver();
        String text = step.value.trim();

        System.out.println("  tapByText → looking for: \"" + text + "\"");

        WebElement element = findWithScroll(driver, text);
        if (element == null) {
            throw new RuntimeException(
                    "tapByText FAILED: \"" + text + "\" not found after "
                    + MAX_SCROLLS + " scrolls.");
        }

        element.click();
        System.out.println("  ✅ Tapped: \"" + text + "\"");
    }

    private WebElement findWithScroll(AppiumDriver driver, String text) {
        for (int scroll = 0; scroll <= MAX_SCROLLS; scroll++) {
            WebElement el = findVisible(driver, text);
            if (el != null) return el;
            if (scroll < MAX_SCROLLS) {
                scrollDown(driver);
                try { Thread.sleep(SCROLL_WAIT); } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }

    private WebElement findVisible(AppiumDriver driver, String text) {
        try {
            String escaped  = text.replace("\"", "\\\"");
            String selector = "new UiSelector().textContains(\"" + escaped + "\")";
            List<WebElement> found = driver.findElements(
                    AppiumBy.androidUIAutomator(selector));
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private void scrollDown(AppiumDriver driver) {
        try {
            Dimension size   = driver.manage().window().getSize();
            int startY  = (int) (size.height * 0.75);
            int endY    = (int) (size.height * 0.25);
            int centerX = size.width / 2;

            PointerInput finger   = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence     sequence = new Sequence(finger, 1);
            sequence.addAction(finger.createPointerMove(
                    Duration.ZERO, PointerInput.Origin.viewport(), centerX, startY));
            sequence.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            sequence.addAction(finger.createPointerMove(
                    Duration.ofMillis(400), PointerInput.Origin.viewport(), centerX, endY));
            sequence.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(List.of(sequence));
        } catch (Exception e) {
            System.out.println("  ⚠️  Scroll failed: " + e.getMessage());
        }
    }
}
