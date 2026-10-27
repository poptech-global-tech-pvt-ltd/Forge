package com.popclub.android.actions;

import com.popclub.core.GestureUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.Dimension;

/**
 * SwipeAction — performs a directional swipe gesture.
 *
 * YAML usage:
 *   - action: swipe
 *     direction: up          # up | down | left | right (default: up)
 *
 *   - action: swipe
 *     direction: left
 *
 *   - action: swipe
 *     direction: up
 *     value: "800"           # swipe speed in ms (default: 400)
 */
public class SwipeAction implements Action {

    private static final int DEFAULT_DURATION_MS = 400;

    @Override
    public void perform(Step step) {
        AppiumDriver driver = DriverManager.getDriver();

        String direction = step.direction != null ? step.direction.trim().toLowerCase() : "up";
        int durationMs = DEFAULT_DURATION_MS;
        if (step.value != null && step.value.matches("\\d+")) {
            durationMs = Integer.parseInt(step.value.trim());
        }

        // Override with explicit from/to coordinates if provided: locator="x%,y%" text="x%,y%"
        if (step.locator != null && step.locator.contains("%") && step.text != null && step.text.contains("%")) {
            Dimension size = driver.manage().window().getSize();
            int[] from = parsePercent(step.locator, size.width, size.height);
            int[] to   = parsePercent(step.text,   size.width, size.height);
            if (from != null && to != null) {
                GestureUtil.swipeCoords(driver, from[0], from[1], to[0], to[1], durationMs);
                System.out.println("[swipe] (" + from[0] + "," + from[1] + ") → (" + to[0] + "," + to[1] + ") " + durationMs + "ms");
                return;
            }
        }

        GestureUtil.swipe(driver, direction, durationMs);
        System.out.println("[swipe] direction=" + direction + " " + durationMs + "ms");
    }

    private int[] parsePercent(String s, int w, int h) {
        try {
            String[] p = s.replace("%","").split(",");
            return new int[]{ (int)(Integer.parseInt(p[0].trim()) * w / 100.0),
                              (int)(Integer.parseInt(p[1].trim()) * h / 100.0) };
        } catch (Exception e) { return null; }
    }
}
