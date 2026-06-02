package com.popclub.mobile.actions;

import com.popclub.mobile.driver.AppiumDriverManager;
import com.popclub.mobile.driver.DriverManager;
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

/**
 * LaunchAppAction — gets/creates the Appium driver (which also installs and
 * launches the APK), then waits for the app to be interactive and dismisses
 * any system overlays that Android or Google Play Services may show on startup:
 *
 *   • Google account chooser  (com.google.android.gms:id/cancel)
 *   • "Allow" permission dialog (android:id/button1 / button2)
 *   • Play Services "Sign in" bottom sheet (com.google.android.gms:id/cancel)
 *   • App-update prompt        (app_update_later_button)
 *   • Any generic "OK / Got it" toast dialog
 *
 * After dismissal the driver is also registered in DriverManager so that
 * every subsequent action can call DriverManager.getDriver() without issue.
 */
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

        // 2. Wait until *something* is visible (app loaded past splash screen)
        waitForAppReady(driver);

        // 4. Dismiss system / Google overlays that can block the login screen
        dismissSystemDialogs(driver);

        System.out.println("[LaunchApp] App launch complete — proceeding with test steps.");
    }

    // ── app restart ───────────────────────────────────────────────────────────

    /**
     * Restarts the app so every test begins from the launcher / splash screen.
     *
     * Strategy (tried in order, each failure falls through to the next):
     *   1. mobile: startActivity with stopApp=true  — single atomic stop+launch via UiAutomator2
     *   2. terminateApp + activateApp               — Appium Java-client two-step
     *   3. activateApp alone                        — brings app to foreground (no clean state,
     *                                                  but loginIfNeeded handles whatever screen is visible)
     *
     * Every step is exception-safe — a failure here never aborts the test.
     */
    private void forceRestartApp(AndroidDriver driver) {
        // ── Step 1: terminateApp with extended timeout (default 500ms is too short for vivo) ──
        boolean stopped = false;
        try {
            driver.executeScript("mobile: terminateApp", java.util.Map.of(
                    "appId",   APP_PACKAGE,
                    "timeout", 8_000          // wait up to 8 s for the process to die
            ));
            sleep(800);
            stopped = true;
            System.out.println("[LaunchApp] terminateApp succeeded");
        } catch (Exception e) {
            System.out.println("[LaunchApp] terminateApp failed: " + e.getMessage()
                    + " — will attempt activateApp anyway");
        }

        if (stopped) {
            sleep(500);
        }

        // ── Step 2: Start fresh via activateApp ───────────────────────────────
        try {
            driver.activateApp(APP_PACKAGE);
            sleep(800);
            System.out.println("[LaunchApp] activateApp succeeded — app launched");
        } catch (Exception e) {
            System.out.println("[LaunchApp] ⚠️  activateApp failed: " + e.getMessage()
                    + " — proceeding anyway; loginIfNeeded will handle screen state");
        }
    }

    // ── wait for app ───────────────────────────────────────────────────────────

    private void waitForAppReady(AppiumDriver driver) {
        try {
            // Any visible element means the app rendered something
            new WebDriverWait(driver, Duration.ofSeconds(APP_READY_TIMEOUT_SEC))
                    .until(d -> !d.findElements(By.xpath("//*[@displayed='true']")).isEmpty());
        } catch (Exception e) {
            System.out.println("[LaunchApp] ⚠️  App-ready wait timed out: " + e.getMessage());
        }
    }

    // ── system dialog dismissal ────────────────────────────────────────────────

    /**
     * Cycles through known system-dialog selectors and taps dismissal buttons.
     * Runs multiple rounds because one dialog may reveal another underneath.
     */
    private void dismissSystemDialogs(AppiumDriver driver) {
        for (int round = 1; round <= DIALOG_DISMISS_ROUNDS; round++) {
            boolean dismissed = false;

            dismissed |= tapIfPresent(driver, By.id("com.google.android.gms:id/cancel"),
                    "Google account chooser — Cancel");

            dismissed |= tapIfPresent(driver, By.id("com.google.android.gms:id/decline_button"),
                    "Google sign-in — Decline");

            dismissed |= tapIfPresent(driver, By.id("com.android.permissioncontroller:id/permission_deny_button"),
                    "Permission dialog — Deny");

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
            sleep(800);
        }
    }

    /**
     * Attempts to find and tap an element using the given locator.
     *
     * @return true if the element was found and tapped
     */
    private boolean tapIfPresent(AppiumDriver driver, By locator, String description) {
        try {
            List<WebElement> elements = driver.findElements(locator);
            if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                elements.get(0).click();
                System.out.println("[LaunchApp] ✅ Dismissed: " + description);
                return true;
            }
        } catch (Exception ignored) {
            // Element not found or not interactable — skip
        }
        return false;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}