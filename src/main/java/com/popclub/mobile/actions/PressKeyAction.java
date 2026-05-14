package com.popclub.mobile.actions;

import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;

import java.util.Map;

public class PressKeyAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        int keycode = resolveKeycode(step.value);

        driver.executeScript("mobile: pressKey", Map.of("keycode", keycode));

        System.out.println("Pressed key: " + step.value + " (keycode " + keycode + ")");
    }

    private int resolveKeycode(String key) {
        if (key == null) throw new RuntimeException("pressKey requires a value (e.g. search, enter, back)");
        switch (key.toLowerCase().trim()) {
            case "search":  return 84;
            case "enter":   return 66;
            case "back":    return 4;
            case "home":    return 3;
            case "tab":     return 61;
            case "delete":
            case "backspace": return 67;
            default:
                throw new RuntimeException("Unknown key: '" + key
                        + "'. Supported: search, enter, back, home, tab, delete");
        }
    }
}
