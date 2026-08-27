package com.popclub.android.driver;

import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;

import java.time.Duration;
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
 * <p>{@code grantAllPermissions()} in AppiumDriverManager handles install-time
 * grants via ADB. This class handles runtime dialogs that appear mid-test.
 *
 * <p>Strategy: resource IDs first (fast, zero implicit-wait overhead),
 * then exact-text fallback for older Android / custom ROMs.
 * Implicit wait is zeroed for the duration of each tick so no lookup
 * ever stalls the test thread with a 10 s timeout.
 */
public class PermissionWatcher {

    /**
     * Permission-controller resource IDs for "allow"-family buttons.
     * Checked in preference order — most specific first.
     */
    private static final String[] ALLOW_IDS = {
        "com.android.permissioncontroller:id/permission_allow_button",
        "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
        "com.android.permissioncontroller:id/permission_allow_one_time_button",
        "com.android.permissioncontroller:id/permission_allow_always_button",
        // Android 10 and some OEM variants use this ID
        "com.android.packageinstaller:id/permission_allow_button",
    };

    /**
     * Exact button texts used as fallback when resource IDs don't match
     * (older Android builds, custom ROMs, vendor-modified dialogs).
     * These MUST be exact strings — no partial matching.
     */
    private static final String[] ALLOW_TEXTS = {
        "Allow",
        "ALLOW",
        "While using the app",
        "Only this time",
        "Allow all the time",
        "Allow anyway",
    };

    private static ScheduledExecutorService executor;
    private static ScheduledFuture<?>       task;

    /** Start watching for permission dialogs every 3 s. */
    public static synchronized void start(AppiumDriver driver) {
        stop(); // clean up any leftover watcher from previous test
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PermissionWatcher");
            t.setDaemon(true);
            return t;
        });
        task = executor.scheduleWithFixedDelay(
            () -> tick(driver), 1000, 3000, TimeUnit.MILLISECONDS);
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
        // Zero implicit wait for the entire tick so every findElements() returns
        // immediately when the element is absent — no 10 s stall per lookup.
        Duration saved;
        try {
            saved = driver.manage().timeouts().getImplicitWaitTimeout();
        } catch (Exception ignored) {
            return; // driver gone
        }

        try {
            driver.manage().timeouts().implicitlyWait(Duration.ZERO);

            // 1. Fast path — resource IDs (deterministic, no text parsing)
            for (String resId : ALLOW_IDS) {
                if (clickById(driver, resId)) return;
            }

            // 2. Fallback — exact text match (older Android / custom ROMs)
            for (String text : ALLOW_TEXTS) {
                if (clickByExactText(driver, text)) return;
            }

        } catch (Exception ignored) {
            // Driver may have gone away between tests — swallow silently
        } finally {
            try {
                driver.manage().timeouts().implicitlyWait(saved);
            } catch (Exception ignored) {}
        }
    }

    private static boolean clickById(AppiumDriver driver, String resourceId) {
        try {
            var elements = driver.findElements(By.id(resourceId));
            if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                elements.get(0).click();
                System.out.println("[PermissionWatcher] ✅ Allowed via id: " + resourceId);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean clickByExactText(AppiumDriver driver, String text) {
        try {
            // Use xpath for exact text match — avoids UIAutomator's built-in wait loop
            var elements = driver.findElements(
                By.xpath("//*[@text='" + text + "']"));
            if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                elements.get(0).click();
                System.out.println("[PermissionWatcher] ✅ Allowed via text: \"" + text + "\"");
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
