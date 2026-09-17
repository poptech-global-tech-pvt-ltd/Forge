package com.popclub.android.driver;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DeviceKeepAlive — prevents the Android screen from locking during long tests.
 *
 * Sends KEYCODE_WAKEUP (224) every 25 seconds via mobile:shell.
 * KEYCODE_WAKEUP turns the screen on if it went dark — it does NOT
 * inject a tap, so it never interferes with the test under way.
 *
 * Usage:
 *   DeviceKeepAlive.start(driver);   // call once after launchApp
 *   DeviceKeepAlive.stop();          // call at end of test / on failure
 */
public class DeviceKeepAlive {

    private static final int INTERVAL_SECONDS = 25;

    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "device-keep-alive");
                t.setDaemon(true);   // does not block JVM shutdown
                return t;
            });

    private static final AtomicReference<ScheduledFuture<?>> task =
            new AtomicReference<>(null);

    /** Start (or restart) the keep-alive heartbeat for the given driver. */
    public static void start(AppiumDriver driver) {
        stop(); // cancel any previous task first

        ScheduledFuture<?> f = scheduler.scheduleAtFixedRate(() -> {
            try {
                AppiumDriver d = DriverManager.getDriver();
                if (d == null) return;

                // KEYCODE_WAKEUP = 224 — wakes screen, safe to send at any time
                d.executeScript("mobile: shell", Map.of(
                        "command", "input",
                        "args",    java.util.List.of("keyevent", "224")
                ));

            } catch (Exception ignored) {
                // Session may have ended — swallow silently
            }
        }, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);

        task.set(f);
        System.out.println("[KeepAlive] 🔆 Screen keep-alive started (every "
                + INTERVAL_SECONDS + "s).");
    }

    /** Stop the heartbeat. Call after the test completes or on failure. */
    public static void stop() {
        ScheduledFuture<?> f = task.getAndSet(null);
        if (f != null) {
            f.cancel(false);
            System.out.println("[KeepAlive] Screen keep-alive stopped.");
        }
    }
}
