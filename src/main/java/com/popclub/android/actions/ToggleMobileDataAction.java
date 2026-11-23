package com.popclub.android.actions;

import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;

import java.util.List;
import java.util.Map;

public class ToggleMobileDataAction implements Action {

    @Override
    public void perform(Step step) {
        AppiumDriver driver = DriverManager.getDriver();
        String value = (step.value == null) ? "" : step.value.trim().toLowerCase();

        if (value.equals("off_if_on") || value.isBlank()) {
            // Check current state and disable only if data is currently on
            if (isMobileDataOn(driver)) {
                setMobileData(driver, false);
                System.out.println("[toggleMobileData] Mobile data was ON — disabled.");
            } else {
                System.out.println("[toggleMobileData] Mobile data already OFF — no action.");
            }
            return;
        }

        if (!value.equals("on") && !value.equals("off")) {
            throw new RuntimeException("toggleMobileData value must be 'on', 'off', or 'off_if_on', got: '" + step.value + "'");
        }

        setMobileData(driver, value.equals("on"));
        System.out.println("[toggleMobileData] Mobile data " + (value.equals("on") ? "enabled" : "disabled"));
    }

    private boolean isMobileDataOn(AppiumDriver driver) {
        try {
            Object result = driver.executeScript("mobile: shell", Map.of(
                    "command", "settings",
                    "args", List.of("get", "global", "mobile_data")
            ));
            return "1".equals(String.valueOf(result).trim());
        } catch (Exception e) {
            System.out.println("[toggleMobileData] Could not read mobile_data state: " + e.getMessage());
            return false;
        }
    }

    private void setMobileData(AppiumDriver driver, boolean enable) {
        driver.executeScript("mobile: shell", Map.of(
                "command", "svc",
                "args", List.of("data", enable ? "enable" : "disable")
        ));
    }
}
