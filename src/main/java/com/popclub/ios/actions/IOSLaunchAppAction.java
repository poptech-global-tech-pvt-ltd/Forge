package com.popclub.ios.actions;

import com.popclub.android.actions.Action;
import com.popclub.android.driver.DriverManager;
import com.popclub.api.util.ConfigManager;
import com.popclub.core.TestContext;
import com.popclub.ios.driver.IOSDriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** launchApp for iOS — boots XCUITest session, dismisses system alerts, waits for app ready. */
public class IOSLaunchAppAction implements Action {

    private static final int APP_READY_TIMEOUT_SEC = 45;
    private static final int ALERT_DISMISS_ROUNDS  = 5;
    private static final String BUNDLE_ID = "com.popclub.popclubapp";

    // Reads from config/{env}.properties → ios.passcode, with system property override
    private static String getPasscode() {
        String sysProp = System.getProperty("ios.passcode");
        if (sysProp != null && !sysProp.isEmpty()) return sysProp;
        try { return ConfigManager.getIosPasscode(); } catch (Exception e) { return null; }
    }

    @Override
    public void perform(Step step) {
        AppiumDriver driver = IOSDriverManager.getDriver();
        DriverManager.setDriver(driver);

        unlockDeviceIfLocked(driver);

        if (TestContext.isResumeMode()) {
            ensureAppRunning(driver);
        } else {
            System.out.println("[IOSLaunch] Full run — force restarting app...");
            forceRestartApp(driver);
        }

        waitForAppReady(driver);

        if (TestContext.isLoginRequired()) {
            dismissSystemAlerts(driver);
        } else {
            System.out.println("[IOSLaunch] loginRequired=false — waiting for home tab...");
            waitForHomeTab(driver);
        }

        System.out.println("[IOSLaunch] App launch complete — proceeding with test steps.");
    }

    /**
     * Unlocks the device if it is locked.
     * 1. Checks isDeviceLocked() via Appium.
     * 2. Presses the home button to wake the screen.
     * 3. Swipes up from the bottom (Face ID / modern iPhones) to reach lock screen.
     * 4. If DEVICE_PASSCODE is set, taps each digit on the passcode pad.
     */
    private void unlockDeviceIfLocked(AppiumDriver driver) {
        try {
            boolean locked = (Boolean) driver.executeScript("mobile: isLocked");
            if (!locked) {
                System.out.println("[IOSLaunch] Device already unlocked.");
                return;
            }
            System.out.println("[IOSLaunch] Device is locked — unlocking...");
        } catch (Exception e) {
            System.out.println("[IOSLaunch] Could not check lock state: " + e.getMessage());
            return;
        }

        // Wake screen — press home / side button
        try {
            driver.executeScript("mobile: pressButton", Map.of("name", "home"));
        } catch (Exception e) {
            try { driver.executeScript("mobile: pressButton", Map.of("name", "volumeup")); }
            catch (Exception ignored) {}
        }
        sleep(800);

        // Swipe up from bottom to dismiss lock screen (Face ID devices)
        try {
            Map<String, Object> args = Map.of(
                    "direction", "up",
                    "velocity", 1000
            );
            driver.executeScript("mobile: swipe", args);
            sleep(600);
        } catch (Exception e) {
            System.out.println("[IOSLaunch] Swipe-up on lock screen failed: " + e.getMessage());
        }

        // Enter passcode if provided
        String passcode = getPasscode();
        if (passcode != null && !passcode.isEmpty()) {
            System.out.println("[IOSLaunch] Entering passcode...");
            for (char digit : passcode.toCharArray()) {
                try {
                    driver.findElement(AppiumBy.accessibilityId(String.valueOf(digit))).click();
                    sleep(150);
                } catch (Exception e) {
                    System.out.println("[IOSLaunch] Passcode digit '" + digit + "' not found: " + e.getMessage());
                }
            }
            sleep(1000);
        }

        System.out.println("[IOSLaunch] Device unlock sequence complete.");
    }

    private void ensureAppRunning(AppiumDriver driver) {
        try {
            driver.executeScript("mobile: activateApp", java.util.Map.of("bundleId", BUNDLE_ID));
            System.out.println("[IOSLaunch] App activated (resume mode).");
        } catch (Exception e) {
            System.out.println("[IOSLaunch] activateApp failed: " + e.getMessage());
        }
    }

    private void forceRestartApp(AppiumDriver driver) {
        try {
            driver.executeScript("mobile: terminateApp", java.util.Map.of("bundleId", BUNDLE_ID));
            System.out.println("[IOSLaunch] terminateApp succeeded");
        } catch (Exception e) {
            System.out.println("[IOSLaunch] terminateApp failed: " + e.getMessage());
        }
        try {
            driver.executeScript("mobile: activateApp", java.util.Map.of("bundleId", BUNDLE_ID));
            System.out.println("[IOSLaunch] activateApp succeeded");
        } catch (Exception e) {
            System.out.println("[IOSLaunch] activateApp failed: " + e.getMessage());
        }
    }

    private void waitForAppReady(AppiumDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(APP_READY_TIMEOUT_SEC))
                    .until(d -> !d.findElements(By.xpath("//*[@visible='true']")).isEmpty());
        } catch (Exception e) {
            System.out.println("[IOSLaunch] App-ready wait timed out: " + e.getMessage());
        }
    }

    private void waitForHomeTab(AppiumDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(APP_READY_TIMEOUT_SEC))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            AppiumBy.accessibilityId("Home")));
            System.out.println("[IOSLaunch] Home tab visible — app ready.");
        } catch (Exception e) {
            System.out.println("[IOSLaunch] Home tab not visible after " + APP_READY_TIMEOUT_SEC + "s");
        }
    }

    /**
     * Dismisses iOS system permission alerts and app-level dialogs.
     * iOS stacks alerts — run multiple rounds.
     */
    private void dismissSystemAlerts(AppiumDriver driver) {
        for (int round = 1; round <= ALERT_DISMISS_ROUNDS; round++) {
            boolean dismissed = false;

            // iOS system permission alerts — "Don't Allow" first to avoid granting unwanted permissions
            dismissed |= tapIfPresent(driver,
                    By.xpath("//XCUIElementTypeButton[@name='Allow While Using App']"),
                    "Location — Allow While Using App");

            dismissed |= tapIfPresent(driver,
                    By.xpath("//XCUIElementTypeButton[@name='Allow']"),
                    "System alert — Allow");

            dismissed |= tapIfPresent(driver,
                    By.xpath("//XCUIElementTypeButton[@name='OK']"),
                    "System alert — OK");

            dismissed |= tapIfPresent(driver,
                    By.xpath("//XCUIElementTypeButton[@name='Continue']"),
                    "App onboarding — Continue");

            // App-level update prompt
            dismissed |= tapIfPresent(driver,
                    AppiumBy.accessibilityId("app_update_later_button"),
                    "App update — Later");

            if (!dismissed) {
                System.out.println("[IOSLaunch] No alerts detected (round " + round + ") — app is ready.");
                break;
            }
            System.out.println("[IOSLaunch] Dismissed an alert (round " + round + "), checking again...");
            sleep(300);
        }
    }

    private boolean tapIfPresent(AppiumDriver driver, By locator, String description) {
        try {
            List elements = driver.findElements(locator);
            if (!elements.isEmpty()) {
                ((org.openqa.selenium.WebElement) elements.get(0)).click();
                System.out.println("[IOSLaunch] Dismissed: " + description);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
