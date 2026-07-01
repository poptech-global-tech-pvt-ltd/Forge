package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/** launchApp — starts the Appium session, wakes the device, and dismisses system dialogs. */
public class LaunchAppAction implements Action {

    /** How long to wait for the first app screen to appear after launch. */
    private static final int APP_READY_TIMEOUT_SEC = 30;

    /** How many rounds to scan for system dialogs (each round ≈ 1 s). */
    private static final int DIALOG_DISMISS_ROUNDS = 5;

    private static final String APP_PACKAGE = "com.popclub.android";

    @Override
    public void perform(Step step) {
        // 1. Boot Appium session (installs APK if needed)
        AppiumDriver driver = AppiumDriverManager.getDriver();
        DriverManager.setDriver(driver);

        // 2. Wake device if screen is off (defensive — keepScreenOn should prevent this)
        wakeDevice((AndroidDriver) driver);

        // 3. Close and relaunch so every test starts from a clean app state.
        //    - Resume mode (running from mid-step): keep app alive, just bring to foreground.
        //    - Full run from step 1: always force-restart, even if noReset=true in YAML.
        if (TestContext.isResumeMode()) {
            ensureAppRunning((AndroidDriver) driver);
        } else {
            System.out.println("[LaunchApp] Full run from step 1 — force restarting app…");
            forceRestartApp((AndroidDriver) driver);
        }

        // 4. Wait until *something* is visible (app loaded past splash screen)
        waitForAppReady(driver);

        if (TestContext.isLoginRequired()) {
            // 5a. Login flow — dismiss Google/GMS overlays that can block the login screen
            dismissSystemDialogs(driver);
        } else {
            // 5b. No login — app is already authenticated, skip GMS dialogs
            //     and wait directly for the home tab to be visible.
            System.out.println("[LaunchApp] loginRequired=false — skipping GMS dialogs, waiting for home tab…");
            waitForHomeTab(driver);
        }

        System.out.println("[LaunchApp] App launch complete — proceeding with test steps.");
    }

    private void wakeDevice(AndroidDriver driver) {
        try {
            if (driver.isDeviceLocked()) {
                driver.unlockDevice();
                System.out.println("[LaunchApp] Device was locked — unlocked.");
            }
        } catch (Exception e) {
            System.out.println("[LaunchApp] Wake/unlock skipped: " + e.getMessage());
        }

        try {
            driver.executeScript("mobile: shell", java.util.Map.of(
                    "command", "settings",
                    "args",    java.util.List.of("put", "system", "screen_off_timeout", "2147483647")
            ));
        } catch (Exception ignored) {}

        // stay_on_while_plugged_in = 7 (USB=1 + AC=2 + wireless=4)
        try {
            driver.executeScript("mobile: shell", java.util.Map.of(
                    "command", "svc",
                    "args",    java.util.List.of("power", "stayon", "true")
            ));
        } catch (Exception ignored) {}

        try {
            driver.executeScript("mobile: shell", java.util.Map.of(
                    "command", "settings",
                    "args",    java.util.List.of("put", "global", "stay_on_while_plugged_in", "7")
            ));
        } catch (Exception ignored) {}
    }

    /**
     * When noReset=true, leaves the app alone if already running — launches it if not.
     */
    private void ensureAppRunning(AndroidDriver driver) {
        try {
            io.appium.java_client.appmanagement.ApplicationState state = driver.queryAppState(APP_PACKAGE);
            System.out.println("[LaunchApp] noReset=true — app state: " + state);
            driver.activateApp(APP_PACKAGE);
            System.out.println("[LaunchApp] App activated.");
        } catch (Exception e) {
            System.out.println("[LaunchApp] queryAppState/activateApp failed: " + e.getMessage());
        }
    }

    /** Restarts the app; each strategy falls through to the next on failure. */
    private void forceRestartApp(AndroidDriver driver) {
        try {
            driver.executeScript("mobile: terminateApp", java.util.Map.of(
                    "appId",   APP_PACKAGE,
                    "timeout", 8_000
            ));
            System.out.println("[LaunchApp] terminateApp succeeded");
        } catch (Exception e) {
            System.out.println("[LaunchApp] terminateApp failed: " + e.getMessage());
        }
        try {
            driver.activateApp(APP_PACKAGE);
            System.out.println("[LaunchApp] activateApp succeeded — app launched");
        } catch (Exception e) {
            System.out.println("[LaunchApp] ⚠️  activateApp failed: " + e.getMessage());
        }
    }

    private void waitForHomeTab(AppiumDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(APP_READY_TIMEOUT_SEC))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            AppiumBy.accessibilityId("Home")));
            System.out.println("[LaunchApp] Home tab visible — app ready.");
        } catch (Exception e) {
            System.out.println("[LaunchApp] ⚠️  Home tab not visible after "
                    + APP_READY_TIMEOUT_SEC + "s: " + e.getMessage());
        }
    }

    private void waitForAppReady(AppiumDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(APP_READY_TIMEOUT_SEC))
                    .until(d -> !d.findElements(By.xpath("//*[@displayed='true']")).isEmpty());
        } catch (Exception e) {
            System.out.println("[LaunchApp] ⚠️  App-ready wait timed out: " + e.getMessage());
        }
    }

    /** Cycles through known system-dialog selectors; runs multiple rounds since dialogs can stack. */
    private void dismissSystemDialogs(AppiumDriver driver) {
        for (int round = 1; round <= DIALOG_DISMISS_ROUNDS; round++) {
            boolean dismissed = false;

            dismissed |= tapIfPresent(driver, By.id("com.google.android.gms:id/cancel"),
                    "Google account chooser — Cancel");

            dismissed |= tapIfPresent(driver, By.id("com.google.android.gms:id/decline_button"),
                    "Google sign-in — Decline");

            // Permission dialogs are handled automatically by Appium's autoGrantPermissions capability
            // (install-time) and PermissionWatcher (runtime mid-test). No need to deny them here.

            dismissed |= tapIfPresent(driver, By.id("android:id/button2"),
                    "System dialog — Cancel/Deny (button2)");

            // App-level update prompt ("Later" button via accessibility ID)
            dismissed |= tapIfPresent(driver,
                    AppiumBy.accessibilityId("app_update_later_button"),
                    "App update — Later");

            if (!dismissed) {
                // No dialogs this round — we're clear
                System.out.println("[LaunchApp] No system dialogs detected (round " + round + ") — app is ready.");
                break;
            }

            System.out.println("[LaunchApp] Dismissed a system dialog (round " + round + "), checking again…");
            sleep(200);
        }
    }

    /** Returns true if the element was found and tapped. */
    private boolean tapIfPresent(AppiumDriver driver, By locator, String description) {
        try {
            List<WebElement> elements = driver.findElements(locator);
            if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                elements.get(0).click();
                System.out.println("[LaunchApp] ✅ Dismissed: " + description);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}