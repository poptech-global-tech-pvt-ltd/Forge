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

        WebElement element = WaitUtil.waitForElement(driver, step.locators);

        // Tap the wrapper to open the keyboard and focus the underlying input
        element.click();

        // Give the keyboard time to open and the EditText to receive focus
        try { Thread.sleep(800); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // React Native wraps TextInput in a View — the actual editable element
        // is the focused android.widget.EditText child. Target it directly so
        // UiAutomator2's setText() hits a real editable node.
        try {
            WebElement editText = driver.findElement(
                    AppiumBy.xpath("//android.widget.EditText[@focused='true']"));
            editText.clear();
            editText.sendKeys(step.value);
        } catch (Exception ex) {
            // No focused EditText found — fall back to mobile: type which
            // dispatches key events to whatever is currently focused.
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
