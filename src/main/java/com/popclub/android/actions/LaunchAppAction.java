package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.android.driver.DriverManager;
import com.popclub.android.cloud.CloudConfig;
import com.popclub.driver.ForgeDriverContext;
import com.popclub.driver.ForgeDriverManager;
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

/** launchApp — starts the Appium session (or ForgeDriver), wakes the device, and dismisses system dialogs. */
public class LaunchAppAction implements Action {

    /** How long to wait for the first app screen to appear after launch. */
    private static final int APP_READY_TIMEOUT_SEC = 30;

    /** How many rounds to scan for system dialogs (each round ≈ 1 s). */
    private static final int DIALOG_DISMISS_ROUNDS = 5;

    private static final String APP_PACKAGE = "com.popclub.android";

    @Override
    public void perform(Step step) {
        // ── Forge mode: skip Appium entirely ──────────────────────────────────
        if ("forge".equalsIgnoreCase(System.getProperty("driver"))) {
            launchWithForge();
            return;
        }

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

        // Re-unlock in case the screen locked again during app restart
        wakeDevice((AndroidDriver) driver);

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

    private void launchWithForge() {
        String serial = CloudConfig.getDeviceSerial();
        if (serial == null || serial.isBlank()) {
            serial = System.getProperty("deviceSerial", "");
        }
        String adbHost = null;

        // Cloud/STF mode: acquire device from STF to get serial + adbHost
        if (CloudConfig.isCloudEnabled() && (serial == null || serial.isBlank())) {
            System.out.println("[LaunchApp/Forge] Cloud mode — acquiring device from STF…");
            com.popclub.android.driver.DeviceInfo acquired =
                    com.popclub.android.driver.DeviceManager.getDevice();
            serial  = acquired.udid;
            adbHost = acquired.adbHost;
            AppiumDriverManager.deviceInfo.set(acquired);
            System.out.println("[LaunchApp/Forge] STF device acquired: " + serial
                    + " via ADB server " + adbHost);
        } else {
            // Local mode — auto-detect serial if not specified
            if (serial == null || serial.isBlank()) {
                serial = detectLocalSerial();
                System.out.println("[LaunchApp/Forge] Auto-detected local device: " + serial);
            }
            // Pick up adbHost if already set (e.g. from a prior Appium session)
            try {
                com.popclub.android.driver.DeviceInfo info = AppiumDriverManager.getDeviceInfo();
                if (info != null && info.adbHost != null && !info.adbHost.isBlank()) {
                    adbHost = info.adbHost;
                }
            } catch (Exception ignored) {}
        }

        try {
            com.popclub.driver.ForgeDriverClient forge = ForgeDriverManager.start(serial, adbHost);
            ForgeDriverContext.set(forge, serial);
            System.out.println("[LaunchApp/Forge] ForgeDriver started on " + serial
                    + (adbHost != null ? " via ADB server " + adbHost : ""));

            String[] adbPrefix = buildAdbPrefix(serial, adbHost);

            // Auto-install the app if not present on the device
            ensureAppInstalled(adbPrefix, serial);

            // Wake screen and dismiss lock screen (swipe/insecure keyguard only)
            adbShell(adbPrefix, "input", "keyevent", "KEYCODE_WAKEUP")
                    .waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            adbShell(adbPrefix, "wm", "dismiss-keyguard")
                    .waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            Thread.sleep(500);

            if (TestContext.isResumeMode()) {
                // Run-from-step: keep app alive, just bring to foreground
                System.out.println("[LaunchApp/Forge] Resume mode — bringing app to foreground, not restarting");
                adbShell(adbPrefix, "am", "start", "-n", APP_PACKAGE + "/.LauncherFresh")
                        .waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            } else {
                // Full run from step 1 — force-stop then relaunch
                adbShell(adbPrefix, "am", "force-stop", APP_PACKAGE)
                        .waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                Thread.sleep(500);
                adbShell(adbPrefix, "am", "start", "-n", APP_PACKAGE + "/.LauncherFresh")
                        .waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            }
            System.out.println("[LaunchApp/Forge] App launched — loginIfNeeded will handle login/navigation");
        } catch (Exception e) {
            throw new RuntimeException("[LaunchApp/Forge] Failed to start ForgeDriver: " + e.getMessage(), e);
        }
    }

    /** Installs the PopClub APK if the app is not already on the device. */
    private static void ensureAppInstalled(String[] adbPrefix, String serial) throws Exception {
        // pm path <package> returns the APK path if installed, empty if not — exact match, no false positives
        Process check = adbShell(adbPrefix, "pm", "path", APP_PACKAGE);
        check.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        String checkOut = new String(check.getInputStream().readAllBytes()).trim();
        System.out.println("[LaunchApp/Forge] pm path check: " + (checkOut.isEmpty() ? "NOT installed" : checkOut));
        if (checkOut.startsWith("package:")) {
            return; // already installed
        }

        // Find the APK in resources
        java.net.URL apkUrl = LaunchAppAction.class.getClassLoader().getResource("pop-qaDebug.apk");
        if (apkUrl == null) {
            throw new RuntimeException("[LaunchApp/Forge] PopClub APK not installed on device " + serial
                    + " and pop-qaDebug.apk not found in src/main/resources.");
        }
        String apkPath = new java.io.File(apkUrl.toURI()).getAbsolutePath();
        System.out.println("[LaunchApp/Forge] App not installed — installing from " + apkPath);

        // Disable package verifier to avoid INSTALL_FAILED_VERIFICATION_FAILURE
        adbShell(adbPrefix, "settings", "put", "global", "package_verifier_enable", "0")
                .waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

        String[] installCmd = new String[adbPrefix.length + 4];
        System.arraycopy(adbPrefix, 0, installCmd, 0, adbPrefix.length);
        installCmd[adbPrefix.length]     = "install";
        installCmd[adbPrefix.length + 1] = "-r";
        installCmd[adbPrefix.length + 2] = "-t";
        installCmd[adbPrefix.length + 3] = apkPath;

        System.out.println("[LaunchApp/Forge] Installing 350MB APK over network — this may take a few minutes…");
        Process install = new ProcessBuilder(installCmd).redirectErrorStream(true).start();
        install.waitFor(600, java.util.concurrent.TimeUnit.SECONDS);
        String out = new String(install.getInputStream().readAllBytes()).trim();
        System.out.println("[LaunchApp/Forge] Install output: " + out);
        if (!out.contains("Success")) {
            throw new RuntimeException("[LaunchApp/Forge] APK install failed on " + serial + ": " + out);
        }
        System.out.println("[LaunchApp/Forge] ✅ PopClub app installed on " + serial);
    }

    /** Returns the serial of the first locally connected ADB device, or throws if none found. */
    private static String detectLocalSerial() {
        try {
            Process p = new ProcessBuilder("adb", "devices").redirectErrorStream(true).start();
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes());
            for (String line : out.split("\n")) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("List") && line.endsWith("device")) {
                    String serial = line.split("\\s+")[0].trim();
                    if (!serial.isEmpty()) return serial;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("[LaunchApp/Forge] Failed to detect local ADB device: " + e.getMessage(), e);
        }
        throw new RuntimeException("[LaunchApp/Forge] No local ADB device found. "
                + "Connect a device or pass -DdeviceSerial=<serial>.");
    }

    /** Builds ["adb", "-s", serial] or ["adb", "-H", host, "-P", "5037", "-s", serial]. */
    private static String[] buildAdbPrefix(String serial, String adbHost) {
        if (adbHost != null && !adbHost.isBlank()) {
            return new String[]{"adb", "-H", adbHost, "-P", "5037", "-s", serial};
        }
        return new String[]{"adb", "-s", serial};
    }

    /** Runs  adb [...prefix] shell <shellArgs>  and returns the Process. */
    private static Process adbShell(String[] prefix, String... shellArgs)
            throws java.io.IOException {
        String[] cmd = new String[prefix.length + 1 + shellArgs.length];
        System.arraycopy(prefix, 0, cmd, 0, prefix.length);
        cmd[prefix.length] = "shell";
        System.arraycopy(shellArgs, 0, cmd, prefix.length + 1, shellArgs.length);
        return new ProcessBuilder(cmd).redirectErrorStream(true).start();
    }

    private void wakeDevice(AndroidDriver driver) {
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
            activateAppExplicit(driver);
            System.out.println("[LaunchApp] App activated.");
        } catch (Exception e) {
            System.out.println("[LaunchApp] queryAppState/activateApp failed: " + e.getMessage());
        }
    }

    /**
     * Activates the app via an explicit `am start -n package/.LauncherFresh` shell command.
     * driver.activateApp(APP_PACKAGE) resolves the launcher activity itself, which fails here —
     * the manifest also lists a LauncherClassic entry, but that activity class no longer exists
     * in this build (`am start` on it fails with "Activity class does not exist"), so
     * package-only resolution is ambiguous ("Unable to resolve the launchable activity").
     * LauncherFresh is the one that actually works.
     */
    private void activateAppExplicit(AndroidDriver driver) {
        try {
            driver.executeScript("mobile: shell", java.util.Map.of(
                    "command", "am",
                    "args",    java.util.List.of("start", "-n", APP_PACKAGE + "/.LauncherFresh")
            ));
            System.out.println("[LaunchApp] Launched via am start -n " + APP_PACKAGE + "/.LauncherFresh");
        } catch (Exception e) {
            System.out.println("[LaunchApp] am start failed: " + e.getMessage());
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
        activateAppExplicit(driver);
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
                    .until(d -> {
                        try {
                            AppiumDriver ad = (AppiumDriver) d;
                            // Dismiss GMS/system overlays that block the app from reaching foreground
                            tapIfPresent(ad, By.id("com.google.android.gms:id/cancel"),
                                    "Google account chooser — Cancel");
                            tapIfPresent(ad, By.id("com.google.android.gms:id/decline_button"),
                                    "Google sign-in — Decline");
                            tapIfPresent(ad, By.id("android:id/button2"),
                                    "System dialog — Cancel/Deny");

                            String pkg = ((AndroidDriver) ad).getCurrentPackage();
                            if (APP_PACKAGE.equals(pkg)) return true;

                            // If we dismissed a dialog but the app isn't foreground yet, re-activate
                            ((AndroidDriver) ad).activateApp(APP_PACKAGE);
                            return false;
                        } catch (Exception e) {
                            return false;
                        }
                    });
            System.out.println("[LaunchApp] POP app is in foreground — proceeding.");
        } catch (Exception e) {
            System.out.println("[LaunchApp] ⚠️  POP app did not come to foreground after "
                    + APP_READY_TIMEOUT_SEC + "s: " + e.getMessage());
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

            // autoGrantPermissions/PermissionWatcher only cover a fresh install — with
            // noReset:true the app is reused, so runtime prompts (e.g. notifications) can
            // still appear. Package name varies by device: AOSP uses
            // com.android.permissioncontroller, GMS (e.g. this realme unit) uses
            // com.google.android.permissioncontroller — try both.
            dismissed |= tapIfPresent(driver,
                    AppiumBy.androidUIAutomator("new UiSelector().resourceId("
                            + "\"com.android.permissioncontroller:id/permission_deny_button\")"),
                    "Runtime permission dialog — Don't allow (AOSP)");

            dismissed |= tapIfPresent(driver,
                    AppiumBy.androidUIAutomator("new UiSelector().resourceId("
                            + "\"com.google.android.permissioncontroller:id/permission_deny_button\")"),
                    "Runtime permission dialog — Don't allow (GMS)");

            dismissed |= tapIfPresent(driver, By.id("android:id/button2"),
                    "System dialog — Cancel/Deny (button2)");

            // App-level update prompt ("Later" button via accessibility ID)
            dismissed |= tapIfPresent(driver,
                    AppiumBy.accessibilityId("app_update_later_button"),
                    "App update — Later");

            // App onboarding / tour screen — "Close" button
            dismissed |= tapIfPresent(driver,
                    AppiumBy.accessibilityId("Close"),
                    "App tour — Close/Skip");

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