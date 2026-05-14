package com.popclub.mobile.actions;

import com.popclub.core.WaitUtil;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;

public class WaitForAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        // Waits up to 20 s for the element to be visible — confirms screen has loaded
        WaitUtil.waitForElement(driver, step.locators);

        System.out.println("Screen loaded — element visible: " + step.element);
    }
}
