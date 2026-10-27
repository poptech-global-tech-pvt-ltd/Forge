package com.popclub.android.actions;

import com.popclub.core.Locator;
import com.popclub.core.TestContext;
import com.popclub.core.WaitUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class TapAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        // x/y set directly — recorder absolute last resort
        if ((step.locators == null || step.locators.isEmpty()) && (step.x > 0 || step.y > 0)) {
            tapByCoordinates(driver, step.x, step.y);
            return;
        }

        // Scan locator list for point/bounds — handle before WaitUtil
        if (step.locators != null) {
            List<Locator> normalLocators = new ArrayList<>();
            for (Locator loc : step.locators) {
                if ("point".equalsIgnoreCase(loc.type)) {
                    String[] parts = loc.value.split(",");
                    tapByCoordinates(driver,
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()));
                    return;
                } else if ("bounds".equalsIgnoreCase(loc.type)) {
                    int[] b = parseBounds(loc.value);
                    if (b != null) {
                        tapByCoordinates(driver, (b[0] + b[2]) / 2, (b[1] + b[3]) / 2);
                        return;
                    }
                } else {
                    normalLocators.add(loc);
                }
            }
            // Try normal locators: accessibilityId first, then uiautomator text
            if (!normalLocators.isEmpty()) {
                WebElement element = WaitUtil.pollUntilVisible(driver, normalLocators, effectiveTimeout(step));
                element.click();
                return;
            }
        }

        WaitUtil.pollUntilVisible(driver, step.locators, effectiveTimeout(step)).click();
    }

    private static void tapByCoordinates(AppiumDriver driver, int x, int y) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO,
                        PointerInput.Origin.viewport(), x, y))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(List.of(tap));
        System.out.println("  Tapped coordinates (" + x + "," + y + ")");
    }

    /** Resolves the effective timeout: step override → test default → 30s fallback. */
    private static int effectiveTimeout(Step step) {
        return step.timeout > 0 ? step.timeout : TestContext.getDefaultTimeout();
    }

    private static int[] parseBounds(String bounds) {
        try {
            String[] p = bounds.replace("[", "").replace("]", ",").split(",");
            return new int[]{
                Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim())
            };
        } catch (Exception e) { return null; }
    }
}
