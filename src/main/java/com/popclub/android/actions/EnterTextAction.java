package com.popclub.android.actions;

import com.popclub.core.WaitUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

import java.util.Map;

public class EnterTextAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        // Accept text from either `value:` or `variable:` — whichever is set.
        // Both are already interpolated by TestExecutor before this action runs.
        String textToType = (step.value != null && !step.value.isBlank())
                ? step.value
                : (step.variable != null ? step.variable : "");

        boolean elementFound = false;

        // ── Try to locate the element (may not exist in pop-debug/non-qaDebug builds) ──
        if (step.locators != null && !step.locators.isEmpty()) {
            try {
                WebElement element = WaitUtil.waitForElement(driver, step.locators);

                // Tap the wrapper to open the keyboard and focus the underlying input
                element.click();

                // Poll until the keyboard opens and an EditText gains focus (up to 3s)
                // Replaces fixed Thread.sleep(800) — returns as soon as focus appears
                WebElement editText = null;
                long focusDeadline = System.currentTimeMillis() + 3000;
                while (System.currentTimeMillis() < focusDeadline) {
                    try {
                        editText = driver.findElement(
                                AppiumBy.xpath("//android.widget.EditText[@focused='true']"));
                        break;
                    } catch (Exception ignored) {
                        try { Thread.sleep(100); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                // Compose TextField: use the focused EditText if found, else mobile:type
                if (editText != null) {
                    editText.clear();
                    editText.sendKeys(textToType);
                } else {
                    // No focused EditText — dispatch key events to whatever is focused
                    driver.executeScript("mobile: type", Map.of("text", textToType));
                }

                elementFound = true;

            } catch (Exception e) {
                System.out.println("[enterText] Element not found via locator — "
                        + "assuming screen auto-focused the input, falling back to mobile:type");
            }
        }

        // ── Fallback: type into whatever is currently focused on screen ──────────
        // Used when the field has no accessibilityId in pop-debug (e.g. search input).
        // Poll for a focused EditText (up to 3s) instead of fixed Thread.sleep(1500)
        if (!elementFound) {
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    driver.findElement(
                            AppiumBy.xpath("//android.widget.EditText[@focused='true']"));
                    break; // field is ready
                } catch (Exception ignored) {
                    try { Thread.sleep(100); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            driver.executeScript("mobile: type", Map.of("text", textToType));
        }

        // Dismiss the soft keyboard so it doesn't obscure the next element
        try {
            driver.executeScript("mobile: hideKeyboard");
        } catch (Exception ignored) {
            // Keyboard may already be hidden — safe to ignore
        }
    }
}
