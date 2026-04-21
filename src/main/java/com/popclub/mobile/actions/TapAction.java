package com.popclub.mobile.actions;

import com.popclub.core.WaitUtil;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

public class TapAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        WebElement element =
                WaitUtil.waitForElement(driver, step.locators);

        element.click();
    }
}