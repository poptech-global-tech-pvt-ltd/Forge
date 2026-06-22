package com.popclub.android.driver;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * PermissionWatcher — background thread that auto-accepts Android system
 * permission dialogs that appear during test execution.
 *
 * <p>{@code autoGrantPermissions: true} in Appium capabilities handles
 * permissions declared in AndroidManifest at install time.  This class
 * handles runtime dialogs that pop up mid-test (camera, microphone,
 * location, notifications, contacts, storage, etc.).
 *
 * <p>Usage (called automatically by AppiumDriverManager):
 * <pre>
 *   PermissionWatcher.start(driver);   // after driver creation
 *   PermissionWatcher.stop();          // in quitDriver()
 * </pre>
 *
 * <p>Recognized allow-button texts (case-insensitive):
 *   "Allow", "While using the app", "Only this time",
 *   "Allow all the time", "Allow anyway", "OK", "ALLOW"
 */
public class PermissionWatcher {

    /** Button texts that dismiss permission dialogs permissively. */
    private static final List<String> ALLOW_TEXTS = List.of(
        "Allow",
        "While using the app",
        "Only this time",
        "Allow all the time",
        "Allow anyway",
        "ALLOW",
        "OK"
    );

    /** Button texts that should be dismissed (deny / close) if no allow is present. */
    private static final List<String> DISMISS_TEXTS = List.of(
        "Don't allow",
        "Deny",
        "No thanks"
    );

    private static ScheduledExecutorService executor;
    private static ScheduledFuture<?>       task;

    /** Start watching for permission dialogs every 800 ms. */
    public static synchronized void start(AppiumDriver driver) {
        stop(); // clean up any leftover watcher from previous test
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PermissionWatcher");
            t.setDaemon(true);
            return t;
        });
        task = executor.scheduleWithFixedDelay(
            () -> tick(driver), 500, 800, TimeUnit.MILLISECONDS);
        System.out.println("[PermissionWatcher] Started — auto-accepting runtime permission dialogs");
    }

    /** Stop the watcher. Called from AppiumDriverManager.quitDriver(). */
    public static synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static void tick(AppiumDriver driver) {
        try {
            // Check for "Allow"-family buttons first (prefer permissive over deny)
            for (String text : ALLOW_TEXTS) {
                if (clickByText(driver, text)) {
                    System.out.println("[PermissionWatcher] ✅ Allowed: \"" + text + "\"");
                    return; // one click per tick
                }
            }
        } catch (Exception ignored) {
            // Driver may have gone away between tests — swallow silently
        }
    }

    private static boolean clickByText(AppiumDriver driver, String text) {
        try {
            String sel = "new UiSelector().text(\"" + text + "\")";
            var elements = driver.findElements(AppiumBy.androidUIAutomator(sel));
            if (!elements.isEmpty()) {
                elements.get(0).click();
                return true;
            }
            // Also try textContains for partial matches like "While using"
            if (text.length() > 6) {
                String partial = "new UiSelector().textContains(\"" + text.substring(0, 6) + "\")";
                var partials = driver.findElements(AppiumBy.androidUIAutomator(partial));
                for (var el : partials) {
                    String elText = el.getText();
                    if (elText != null && elText.toLowerCase().contains(text.toLowerCase())) {
                        el.click();
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
