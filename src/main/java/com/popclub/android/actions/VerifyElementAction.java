package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.core.WaitUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

public class VerifyElementAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        // shouldExist = false → assert element is NOT present (no poll — check immediately)
        if (Boolean.FALSE.equals(step.shouldExist)) {
            WebElement element = WaitUtil.findElementQuick(driver, step.locators);
            if (element != null) {
                throw new RuntimeException(
                    "Element should NOT exist but was found: " + step.locator);
            }
            System.out.println("✅ Verified element absent: " + step.locator);
            return;
        }

        // shouldExist = true (default) → poll until visible, then assert
        int timeout = step.timeout > 0 ? step.timeout : TestContext.getDefaultTimeout();
        WebElement element = WaitUtil.pollUntilVisible(driver, step.locators, timeout);
        if (element == null) {
            throw new RuntimeException("Element not found: " + step.locator);
        }
        System.out.println("✅ Verified element: " + step.locator);
    }
}