package com.popclub.android.driver;

import com.popclub.core.TestContext;
import com.popclub.android.cloud.CloudConfig;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.time.Duration;

public class AppiumDriverManager {

    private static ThreadLocal<AppiumDriver> driver     = new ThreadLocal<>();
    public  static ThreadLocal<DeviceInfo>   deviceInfo = new ThreadLocal<>();

    public static AppiumDriver getDriver() {
        if (driver.get() == null) {
            driver.set(createDriver());
        }
        return driver.get();
    }

    private static AppiumDriver createDriver() {
        DeviceInfo device = null;
        try {
            // 1. Obtain a device (local ADB pool  OR  STF cloud reservation)
            device = DeviceManager.getDevice();
            String udid = device.udid;
            int    port = device.port;

            System.out.println(
                    "Thread: " + Thread.currentThread().getId() +
                    " | Device: " + udid +
                    " | Port: "   + port +
                    " | Mode: "   + (CloudConfig.isCloudEnabled() ? "CLOUD" : "LOCAL")
            );

            // 2. Start a local Appium server that will drive the device.
            //    Works for both modes:
            //      • Local  → device was already connected via USB/emulator
            //      • Cloud  → device was just ADB-connected over TCP by DeviceManager
            AppiumServerManager.startServer(udid, port);

            // 3. Build capabilities
            UiAutomator2Options options = new UiAutomator2Options();
            options.setPlatformName(device.platformName != null ? device.platformName : "Android");
            options.setDeviceName(udid);
            options.setUdid(udid);
            options.setAutomationName("UiAutomator2");
            if (device.platformVersion != null && !device.platformVersion.isEmpty()) {
                options.setPlatformVersion(device.platformVersion);
            }
            // autoGrantPermissions removed — grantAllPermissions() below covers all dangerous
            // permissions via ADB after session creation, and PermissionWatcher handles any
            // runtime dialogs mid-test. The capability is redundant.

            boolean resumeMode = TestContext.isNoReset();
            // Even in noReset/resume mode, install the app if it is not present on the device.
            // This handles first-run on a fresh device without requiring the user to change the flag.
            boolean appInstalled = resumeMode && isAppInstalled(udid, "com.popclub.android");

            if (resumeMode && appInstalled) {
                // Resume mode (run-from-step): attach to the already-running app.
                // Do NOT set `app` — that triggers install + launch.
                // Do NOT autoLaunch — keep whatever is on screen.
                options.setAppPackage("com.popclub.android");
                // The manifest lists two launcher activities (LauncherFresh, LauncherClassic),
                // so Appium's auto-resolution is ambiguous even with autoLaunch=false — it still
                // needs appActivity to build the session. LauncherClassic's activity class no
                // longer exists in this build, so LauncherFresh is the one that works.
                options.setAppActivity("com.popclub.android.LauncherFresh");
                options.setCapability("appium:autoLaunch", false);
                options.setNoReset(true);
                System.out.println("[Driver] Resume mode — attaching to running app (no install, no launch)");
            } else {
                if (resumeMode) {
                    System.out.println("[Driver] noReset=true but app not installed — falling back to fresh install");
                }
                options.setApp(System.getProperty("user.dir") +
                        "/src/main/resources/pop-qaDebug.apk");
                // Explicitly set package + main activity so Appium resolves the
                // correct entry point when the APK declares multiple launcher activities.
                options.setAppPackage("com.popclub.android");
                options.setAppActivity("com.popclub.android.LauncherFresh");
                options.setNoReset(false);
            }

            options.setNewCommandTimeout(Duration.ofSeconds(300));
            // Keep the screen on throughout the test run — prevents mid-test lock/sleep
            options.setCapability("appium:keepScreenOn", true);
            TestContext.setFreshLaunch(!resumeMode);

            // Unique UiAutomator2 system port to avoid stale-session collisions
            int systemPort;
            try (ServerSocket socket = new ServerSocket(0)) {
                systemPort = socket.getLocalPort();
            } catch (IOException e) {
                systemPort = 8200 + port;
            }
            options.setSystemPort(systemPort);

            // 4. Connect to the local Appium server
            AppiumDriver driverInstance = new AndroidDriver(
                    new URL("http://127.0.0.1:" + port),
                    options
            );

            deviceInfo.set(device);

            // Grant all dangerous permissions declared in the manifest upfront via ADB.
            // pm grant marks them as granted at system level — Android never shows a runtime
            // dialog for a permission that's already granted, so no watcher is needed.
            grantAllPermissions(udid, "com.popclub.android");

            return driverInstance;

        } catch (Exception e) {
            // Release the device if it was allocated but session creation failed
            if (device != null) {
                try { AppiumServerManager.stopServer(device.udid); } catch (Exception ignored) {}
                DeviceManager.release(device.udid);
            }
            e.printStackTrace();
            throw new RuntimeException("Driver creation failed", e);
        }
    }

    public static void quitDriver() {
        if (driver.get() != null) {
            try {
                driver.get().quit();
            } catch (Exception ignored) {}

            DeviceInfo device = deviceInfo.get();
            if (device != null) {
                // Stop local Appium server for this device
                AppiumServerManager.stopServer(device.udid);
                // Release device (local pool or STF reservation)
                DeviceManager.release(device.udid);
            }

            driver.remove();
            deviceInfo.remove();
        }
    }

    public static DeviceInfo getDeviceInfo() {
        return deviceInfo.get();
    }

    /**
     * Returns true if the given package is installed on the device.
     * Uses {@code adb shell pm list packages} — fast, no app launch needed.
     */
    private static boolean isAppInstalled(String serial, String packageName) {
        try {
            String[] cmd = (serial != null && !serial.isBlank())
                ? new String[]{"adb", "-s", serial, "shell", "pm", "list", "packages", packageName}
                : new String[]{"adb", "shell", "pm", "list", "packages", packageName};
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains(packageName)) {
                        System.out.println("[Driver] App installed: " + packageName);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[Driver] Could not check app install status: " + e.getMessage());
        }
        System.out.println("[Driver] App NOT installed: " + packageName);
        return false;
    }

    /**
     * Grants all dangerous Android permissions declared in the app's manifest
     * using {@code adb shell pm grant}.
     *
     * <p>This is a belt-and-suspenders complement to {@code autoGrantPermissions: true}
     * which only fires at Appium session creation.  Some permissions (especially
     * on Android 13+ fine-grained media permissions) can slip through.
     */
    private static void grantAllPermissions(String serial, String packageName) {
        // Full list of dangerous permissions that the POP app might request
        String[] permissions = {
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.USE_BIOMETRIC",
            "android.permission.USE_FINGERPRINT",
        };

        String[] adbBase = (serial != null && !serial.isBlank())
            ? new String[]{"adb", "-s", serial, "shell", "pm", "grant", packageName}
            : new String[]{"adb", "shell", "pm", "grant", packageName};

        int granted = 0;
        for (String perm : permissions) {
            try {
                String[] cmd = new String[adbBase.length + 1];
                System.arraycopy(adbBase, 0, cmd, 0, adbBase.length);
                cmd[adbBase.length] = perm;
                Process p = Runtime.getRuntime().exec(cmd);
                p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                granted++;
            } catch (Exception ignored) {
                // Permission may not be declared in manifest — safe to ignore
            }
        }
        System.out.println("[PermissionGrant] Granted " + granted + " permissions to " + packageName);
    }
}
