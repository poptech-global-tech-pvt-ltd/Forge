package com.popclub.android.actions;

import com.popclub.driver.DriverFacade;
import com.popclub.model.Step;

public class TapIfPresentAction implements Action {

    @Override
    public void perform(Step step) {
        DriverFacade facade = DriverFacade.get();

        if (facade.isPresent(step.locators)) {
            facade.tap(step.locators, 3);
            System.out.println("Dialog found and tapped: " + step.locator);
        } else {
            System.out.println("No dialog found — skipping");
        }
    }
}
