package com.popclub.mobile.actions;

import com.popclub.core.WaitUtil;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

import java.util.Map;

public class EnterTextAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        WebElement element =
                WaitUtil.waitForElement(driver, step.locators);

        element.click();

        driver.executeScript("mobile: type", Map.of(
                "text", step.value
        ));
    }
}