package com.popclub.ios.actions;

import com.popclub.android.actions.Action;
import com.popclub.android.driver.DriverManager;
import com.popclub.core.TestContext;
import com.popclub.ios.driver.IOSDriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/** launchApp for iOS — boots XCUITest session, dismisses system alerts, waits for app ready. */
public class IOSLaunchAppAction implements Action {

    private static final int APP_READY_TIMEOUT_SEC = 45;
    private static final int ALERT_DISMISS_ROUNDS  = 5;
    private static final String BUNDLE_ID = "com.popclub.popclubapp";

    @Override
    public void perform(Step step) {
        AppiumDriver driver = IOSDriverManager.getDriver();
        DriverManager.setDriver(driver);

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
