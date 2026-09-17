package com.popclub.android.actions;

import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;

import java.util.Map;

/** deeplink — opens the given URL as an app deep link via Appium's "mobile: deepLink" command. */
public class DeeplinkAction implements Action {

    private static final String APP_PACKAGE = "com.popclub.android";

    @Override
    public void perform(Step step) {
        if (step.value == null || step.value.isBlank()) {
            throw new RuntimeException("deeplink: 'value' (the deep link URL) is required");
        }

        AppiumDriver driver = DriverManager.getDriver();
        driver.executeScript("mobile: deepLink", Map.of(
                "url", step.value,
                "package", APP_PACKAGE
        ));

        System.out.println("[Deeplink] Opened: " + step.value);
    }
}
