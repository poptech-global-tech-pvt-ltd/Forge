package com.popclub.mobile.actions;

import com.popclub.core.WaitUtil;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

public class VerifyElementAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        WebElement element =
                WaitUtil.waitForElement(driver, step.locators);

        if (element == null) {
            throw new RuntimeException("Element not found: " + step.locator);
        }

        System.out.println("✅ Verified element: " + step.locator);
    }
}