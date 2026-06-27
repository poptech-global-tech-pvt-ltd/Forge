package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.driver.DriverFacade;
import com.popclub.model.Step;

public class VerifyElementAction implements Action {

    @Override
    public void perform(Step step) {
        DriverFacade facade = DriverFacade.get();

        // shouldExist = false → assert element is NOT present (check immediately)
        if (Boolean.FALSE.equals(step.shouldExist)) {
            if (facade.isPresent(step.locators)) {
                throw new RuntimeException(
                    "Element should NOT exist but was found: " + step.locator);
            }
            System.out.println("✅ Verified element absent: " + step.locator);
            return;
        }

        // shouldExist = true (default) → poll until visible
        int timeout = step.timeout > 0 ? step.timeout : TestContext.getDefaultTimeout();
        facade.waitForElement(step.locators, timeout);
        System.out.println("✅ Verified element: " + step.locator);
    }
}