package com.popclub.mobile.actions;

import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;

import java.util.Map;

public class PressKeyAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();
        String key = step.value == null ? null : step.value.toLowerCase().trim();

        // "search" and "enter" are IME actions — must use performEditorAction so Compose
        // TextFields receive the onImeAction callback. Hardware keycodes (mobile: pressKey)
        // do NOT trigger Compose IME callbacks.
        if ("search".equals(key) || "enter".equals(key) || "done".equals(key)) {
            String action = "search".equals(key) ? "search" : "done";
            try {
                driver.executeScript("mobile: performEditorAction", Map.of("action", action));
                System.out.println("Performed IME action: " + action);
            } catch (Exception e) {
                // Fallback to hardware keycode if performEditorAction is not supported
                int keycode = "search".equals(key) ? 84 : 66;
                driver.executeScript("mobile: pressKey", Map.of("keycode", keycode));
                System.out.println("Pressed key (fallback): " + key + " (keycode " + keycode + ")");
            }
            return;
        }

        int keycode = resolveKeycode(key);
        driver.executeScript("mobile: pressKey", Map.of("keycode", keycode));
        System.out.println("Pressed key: " + step.value + " (keycode " + keycode + ")");
    }

    private int resolveKeycode(String key) {
        if (key == null) throw new RuntimeException("pressKey requires a value (e.g. search, enter, back)");
        switch (key) {
            case "back":    return 4;
            case "home":    return 3;
            case "tab":     return 61;
            case "delete":
            case "backspace": return 67;
            default:
                throw new RuntimeException("Unknown key: '" + key
                        + "'. Supported: search, enter, done, back, home, tab, delete");
        }
    }
}
