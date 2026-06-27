package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.core.WaitUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.driver.DriverFacade;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;

public class WaitForAction implements Action {

    @Override
    public void perform(Step step) {

        int timeout = step.timeout > 0 ? step.timeout : TestContext.getDefaultTimeout();

        // ForgeDriver: single HTTP call with timeout passed to UiAutomator on device
        // Appium: WaitUtil polling loop (one HTTP call per 400ms)
        DriverFacade.get().waitForElement(step.locators, timeout);

        System.out.println("Screen loaded — element visible: " + step.element);
    }
}
