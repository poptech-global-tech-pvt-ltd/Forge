package com.popclub.core;

import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * GestureUtil — single place for all touch gestures.
 *
 * Replaces scattered PointerInput + Sequence blocks across action classes.
 * Uses Appium's `mobile:` commands where available (faster, no coordinate math),
 * falls back to W3C Actions for cases that need precise control.
 */
public class GestureUtil {

    // Default durations
    private static final int SWIPE_MS        = 400;
    private static final int FAST_SWIPE_MS   = 300;
    private static final int PULL_PRESS_MS   = 600;
    private static final int PULL_HOLD_MS    = 300;

    // Scroll gesture ratios
    private static final double SCROLL_START_RATIO = 0.70;
    private static final double SCROLL_END_RATIO   = 0.30;
    private static final double PULL_START_RATIO   = 0.25;
    private static final double PULL_END_RATIO     = 0.75;

    // ── High-level directional scroll (uses Appium mobile: scroll) ────────────

    /** Scroll the screen in a direction. direction: "up" | "down" | "left" | "right" */
    public static void scroll(AppiumDriver driver, String direction) {
        try {
            driver.executeScript("mobile: scroll", Map.of("direction", direction));
        } catch (Exception e) {
            // Fallback to swipe if mobile:scroll not supported
            swipe(driver, direction, SWIPE_MS);
        }
    }

    // ── Pull to refresh ───────────────────────────────────────────────────────

    /** Performs a pull-to-refresh gesture from the top of the screen. */
    public static void pullToRefresh(AppiumDriver driver) {
        try {
            driver.executeScript("mobile: pullToRefresh");
        } catch (Exception e) {
            // Fallback: manual slow swipe down from top
            Dimension size = driver.manage().window().getSize();
            int centerX = size.width / 2;
            int startY  = (int) (size.height * PULL_START_RATIO);
            int endY    = (int) (size.height * PULL_END_RATIO);
            swipeCoords(driver, centerX, startY, centerX, endY, PULL_PRESS_MS + PULL_HOLD_MS);
        }
    }

    // ── Directional swipe (coordinate-based) ─────────────────────────────────

    /** Swipe in a cardinal direction using screen percentage anchors. */
    public static void swipe(AppiumDriver driver, String direction, int durationMs) {
        Dimension size = driver.manage().window().getSize();
        int w = size.width, h = size.height;
        int cx = w / 2, cy = h / 2;

        int sx, sy, ex, ey;
        switch (direction.toLowerCase()) {
            case "up":
                sx = cx; sy = (int)(h * SCROLL_START_RATIO);
                ex = cx; ey = (int)(h * SCROLL_END_RATIO);
                break;
            case "down":
                sx = cx; sy = (int)(h * SCROLL_END_RATIO);
                ex = cx; ey = (int)(h * SCROLL_START_RATIO);
                break;
            case "left":
                sx = (int)(w * SCROLL_START_RATIO); sy = cy;
                ex = (int)(w * SCROLL_END_RATIO);   ey = cy;
                break;
            case "right":
                sx = (int)(w * SCROLL_END_RATIO);   sy = cy;
                ex = (int)(w * SCROLL_START_RATIO); ey = cy;
                break;
            default:
                throw new IllegalArgumentException("Unknown swipe direction: " + direction);
        }
        swipeCoords(driver, sx, sy, ex, ey, durationMs);
    }

    public static void swipe(AppiumDriver driver, String direction) {
        swipe(driver, direction, SWIPE_MS);
    }

    public static void swipeFast(AppiumDriver driver, String direction) {
        swipe(driver, direction, FAST_SWIPE_MS);
    }

    /** Swipe between two explicit coordinates. */
    public static void swipeCoords(AppiumDriver driver,
                                   int sx, int sy, int ex, int ey, int durationMs) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence seq = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO,
                        PointerInput.Origin.viewport(), sx, sy))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerMove(Duration.ofMillis(durationMs),
                        PointerInput.Origin.viewport(), ex, ey))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(List.of(seq));
    }

    /** Scroll to top by swiping down repeatedly. */
    public static void scrollToTop(AppiumDriver driver, int times) {
        for (int i = 0; i < times; i++) {
            swipe(driver, "down", FAST_SWIPE_MS);
        }
    }

    public static void scrollToTop(AppiumDriver driver) {
        scrollToTop(driver, 4);
    }
}
