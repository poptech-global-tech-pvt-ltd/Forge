package com.popclub.mobile.actions;

import com.popclub.core.WaitUtil;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

import java.util.Map;

public class EnterTextAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        boolean elementFound = false;

        // ── Try to locate the element (may not exist in pop-debug/non-qaDebug builds) ──
        if (step.locators != null && !step.locators.isEmpty()) {
            try {
                WebElement element = WaitUtil.waitForElement(driver, step.locators);

                // Tap the wrapper to open the keyboard and focus the underlying input
                element.click();

                // Give the keyboard time to open and the EditText to receive focus
                try { Thread.sleep(800); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }

                // Compose TextField: find the focused android.widget.EditText child.
                try {
                    WebElement editText = driver.findElement(
                            AppiumBy.xpath("//android.widget.EditText[@focused='true']"));
                    editText.clear();
                    editText.sendKeys(step.value);
                } catch (Exception ex) {
                    // No focused EditText found — dispatch key events to focused node.
                    driver.executeScript("mobile: type", Map.of("text", step.value));
                }

                elementFound = true;

            } catch (Exception e) {
                System.out.println("[enterText] Element not found via locator — "
                        + "assuming screen auto-focused the input, falling back to mobile:type");
            }
        }

        // ── Fallback: type into whatever is currently focused on screen ──────────
        // Used when the field has no accessibilityId in pop-debug (e.g. search input,
        // which only has a qaTestTag in qaDebug builds).
        if (!elementFound) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            driver.executeScript("mobile: type", Map.of("text", step.value));
        }

        // Dismiss the soft keyboard so it doesn't obscure the next element
        try {
            driver.executeScript("mobile: hideKeyboard");
        } catch (Exception ignored) {
            // Keyboard may already be hidden — safe to ignore
        }
    }
}
