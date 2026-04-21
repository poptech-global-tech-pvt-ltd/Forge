package com.popclub.mobile.actions;

import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import com.popclub.mobile.driver.AppiumDriverManager;
public class LaunchAppAction implements Action {

    @Override
    public void perform(Step step) {
        AppiumDriver driver = AppiumDriverManager.getDriver();

        //  THIS LINE WAS MISSING
        DriverManager.setDriver(driver);

    }
}