package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.core.WaitUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;

public class WaitForAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        // Poll until visible — timeout from step override or test default.
        // This is Forge's zero-wait equivalent: no fixed sleep, acts immediately
        // when the element appears, gives up only after the timeout expires.
        int timeout = step.timeout > 0 ? step.timeout : TestContext.getDefaultTimeout();
        WaitUtil.pollUntilVisible(driver, step.locators, timeout);

        System.out.println("Screen loaded — element visible: " + step.element);
    }
}
