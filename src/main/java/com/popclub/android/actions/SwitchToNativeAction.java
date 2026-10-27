package com.popclub.android.actions;

import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.android.AndroidDriver;

/**
 * switchToNative — switches the Appium session back to the NATIVE_APP
 * context, e.g. after finishing OTP entry inside the webview.
 *
 * YAML usage:
 *   - action: switchToNative
 */
public class SwitchToNativeAction implements Action {

    @Override
    public void perform(Step step) {
        AndroidDriver driver = (AndroidDriver) DriverManager.getDriver();
        driver.context("NATIVE_APP");
        System.out.println("[SwitchToNative] Switched back to NATIVE_APP context");
    }
}
