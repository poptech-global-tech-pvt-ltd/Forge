package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.core.WaitUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;

/**
 * ClipboardAction — copies text from an element or pastes clipboard into a field.
 *
 * YAML usage:
 *   - action: copyText
 *     element: referral_code_label
 *
 *   - action: pasteText
 *     element: coupon_input_field
 */
public class ClipboardAction implements Action {

    private final boolean isPaste;

    public ClipboardAction(boolean isPaste) { this.isPaste = isPaste; }

    @Override
    public void perform(Step step) {
        AppiumDriver driver = DriverManager.getDriver();

        if (isPaste) {
            String stored = TestContext.getScalarData("__clipboard__");
            if (!stored.isEmpty()) {
                driver.executeScript("mobile: shell", Map.of(
                    "command", "am",
                    "args", List.of("broadcast", "-a", "clipper.set", "-e", "text", stored)
                ));
            }
            if (step.locators != null && !step.locators.isEmpty()) {
                WebElement el = WaitUtil.pollUntilVisible(driver, step.locators, TestContext.getDefaultTimeout());
                el.click();
                try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            }
            driver.executeScript("mobile: shell", Map.of(
                "command", "input",
                "args", List.of("keyevent", "279")
            ));
            System.out.println("[pasteText] Pasted clipboard content");
        } else {
            if (step.locators == null || step.locators.isEmpty()) {
                throw new RuntimeException("copyText requires element/locator/text");
            }
            WebElement el = WaitUtil.pollUntilVisible(driver, step.locators, TestContext.getDefaultTimeout());
            String text = el.getText();
            if (text == null || text.isBlank()) {
                text = el.getAttribute("content-desc");
            }
            TestContext.setScalarData("__clipboard__", text != null ? text : "");
            if (step.variable != null && !step.variable.isBlank()) {
                TestContext.setScalarData(step.variable, text != null ? text : "");
            }
            System.out.println("[copyText] Copied: \"" + text + "\"");
        }
    }
}
