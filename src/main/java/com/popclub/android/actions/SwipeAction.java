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
 *     direction: down        # up | down | left | right (default: down) — down reveals content further below
 *
 *   - action: swipe
 *     direction: left
 *
 *   - action: swipe
 *     direction: down
 *     value: "800"           # swipe speed in ms (default: 400)
 */
public class SwipeAction implements Action {

    private static final int DEFAULT_DURATION_MS = 400;

    @Override
    public void perform(Step step) {
        AppiumDriver driver = DriverManager.getDriver();

        String direction = step.direction != null ? step.direction.trim().toLowerCase() : "down";
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

        int repeat = step.times > 0 ? step.times : 1;
        for (int i = 1; i <= repeat; i++) {
            GestureUtil.swipe(driver, toGestureDirection(direction), durationMs);
            if (repeat > 1) {
                System.out.println("[swipe] " + i + "/" + repeat + " direction=" + direction + " " + durationMs + "ms");
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            } else {
                System.out.println("[swipe] direction=" + direction + " " + durationMs + "ms");
            }
        }
    }

    /**
     * GestureUtil.swipe()'s "down"/"up" name the finger's travel direction,
     * not the resulting scroll — its "up" gesture reveals content further
     * down the page, and vice versa. Translate the intuitive YAML direction
     * (down = reveal more below) here at the YAML boundary rather than
     * changing GestureUtil itself (~15 other actions call it directly with
     * hardcoded "up" expecting today's behavior).
     */
    private String toGestureDirection(String direction) {
        switch (direction) {
            case "down": return "up";
            case "up":   return "down";
            default:     return direction; // left / right unaffected
        }
    }

    private int[] parsePercent(String s, int w, int h) {
        try {
            String[] p = s.replace("%","").split(",");
            return new int[]{ (int)(Integer.parseInt(p[0].trim()) * w / 100.0),
                              (int)(Integer.parseInt(p[1].trim()) * h / 100.0) };
        } catch (Exception e) { return null; }
    }
}
