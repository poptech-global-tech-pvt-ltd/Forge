package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.core.WaitUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.driver.ForgeDriverContext;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;

public class WaitForAction implements Action {

    @Override
    public void perform(Step step) {
        if (ForgeDriverContext.isActive()) {
            String tag = ForgeDriverContext.forgeTag(step);
            int timeout = step.timeout > 0 ? step.timeout : TestContext.getDefaultTimeout();
            int[] coords = ForgeDriverContext.get().waitUntilPresent(tag, (long) timeout * 1000);
            if (coords == null) throw new RuntimeException("waitFor: element not found: " + tag);
            System.out.println("Screen loaded — element visible: " + step.element);
            return;
        }

        AppiumDriver driver = DriverManager.getDriver();
        int timeout = step.timeout > 0 ? step.timeout : TestContext.getDefaultTimeout();
        WaitUtil.pollUntilVisible(driver, step.locators, timeout);
        System.out.println("Screen loaded — element visible: " + step.element);
    }
}
