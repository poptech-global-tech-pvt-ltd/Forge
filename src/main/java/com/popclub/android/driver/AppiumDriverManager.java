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
            String udid       = device.udid;
            int    port       = device.port;

            System.out.println(
                    "Thread: " + Thread.currentThread().getId() +
                    " | Device: " + udid +
                    " | Port: "   + port +
                    " | Mode: "   + (CloudConfig.isCloudEnabled() ? "CLOUD" : "LOCAL")
            );

            // 2. Start Appium server.
            //    • Local  → start a local Appium server on this machine
            //    • Cloud  → one pre-started Appium per parallel slot on the STF Mac;
            //               claim a slot (unique appium port + systemPort), then verify it's up
            int[] cloudSlot = null;
            if (device.adbHost != null) {
                cloudSlot = AppiumServerManager.nextCloudSlot();
                AppiumServerManager.ensureRemoteServer(device.adbHost, cloudSlot[0]);
            } else {
                AppiumServerManager.startServer(udid, port);
            }

            // 3. Build capabilities
            UiAutomator2Options options = new UiAutomator2Options();
            options.setPlatformName(device.platformName != null ? device.platformName : "Android");
            options.setDeviceName(udid);
            options.setUdid(udid);
            // No remoteAdbHost — for cloud the Appium server runs on the STF host and has
            // direct local ADB access to the device (serial is known to that ADB daemon).
            options.setAutomationName("UiAutomator2");

            String androidHome = CloudConfig.getAndroidHome();
            if (androidHome != null) {
                options.setCapability("appium:androidSdkRoot", androidHome);
            }

            if (device.platformVersion != null && !device.platformVersion.isEmpty()) {
                options.setPlatformVersion(device.platformVersion);
            }
            // autoGrantPermissions removed — grantAllPermissions() below covers all dangerous
            // permissions via ADB after session creation, and PermissionWatcher handles any
            // runtime dialogs mid-test. The capability is redundant.

            boolean resumeMode = TestContext.isNoReset();
            // Even in noReset/resume mode, install the app if it is not present on the device.
            // This handles first-run on a fresh device without requiring the user to change the flag.
            boolean appInstalled = resumeMode && isAppInstalled(device, "com.popclub.android");

            if (resumeMode && appInstalled) {
                // Resume mode (run-from-step): attach to the already-running app.
                // Do NOT set `app` — that triggers install + launch.
                // Do NOT autoLaunch — keep whatever is on screen.
                options.setAppPackage("com.popclub.android");
                options.setAppActivity("com.popclub.android.LauncherFresh");
                options.setCapability("appium:autoLaunch", false);
                options.setNoReset(true);
                System.out.println("[Driver] Resume mode — attaching to running app (no install, no launch)");
            } else {
                if (resumeMode) {
                    System.out.println("[Driver] noReset=true but app not installed — falling back to fresh install");
                }
                // Cloud: Appium runs on the STF Mac and cannot read local file paths.
                // Serve the APK over HTTP from this machine so Appium can download it.
                String appPath = (device.adbHost != null)
                        ? ApkHttpServer.getApkUrl("pop-qaDebug.apk")
                        : System.getProperty("user.dir") + "/src/main/resources/pop-qaDebug.apk";
                options.setApp(appPath);
                // Explicitly set package + main activity so Appium resolves the
                // correct entry point when the APK declares multiple launcher activities.
                options.setAppPackage("com.popclub.android");
                options.setAppActivity("com.popclub.android.LauncherFresh");
                options.setNoReset(false);
            }

            options.setNewCommandTimeout(Duration.ofSeconds(300));
            options.setCapability("appium:keepScreenOn", true);
            if (device.adbHost != null) {
                // Cloud: Appium is co-located with the device, but ADB over network is still
                // slower than USB — give extra time for installs and server launch.
                options.setCapability("appium:uiautomator2ServerLaunchTimeout", 120000);
                options.setCapability("appium:uiautomator2ServerInstallTimeout", 120000);
                options.setCapability("appium:androidInstallTimeout", 120000);
                // Unlock the device screen using the PIN configured for the farm devices.
                String unlockPin = CloudConfig.getDeviceUnlockPin();
                if (unlockPin != null) {
                    options.setCapability("appium:unlockType", "pin");
                    options.setCapability("appium:unlockKey", unlockPin);
                } else {
                    options.setCapability("appium:skipUnlock", true);
                }
            }
            TestContext.setFreshLaunch(!resumeMode);

            // Unique systemPort per parallel session to avoid ADB-forward conflicts.
            // Local:  free random port on this machine (no firewall constraint).
            // Cloud:  systemPort comes from the slot claimed above (8200, 8201, …).
            int systemPort;
            if (device.adbHost != null) {
                systemPort = cloudSlot[1];
            } else {
                try (ServerSocket socket = new ServerSocket(0)) {
                    systemPort = socket.getLocalPort();
                } catch (IOException e) {
                    systemPort = 8200 + port;
                }
            }
            options.setSystemPort(systemPort);

            // 4. Connect to Appium server
            //    • Local  → local Appium we just started on port `port`
            //    • Cloud  → the slot-specific Appium server on the STF host
            String appiumUrl = (device.adbHost != null)
                    ? "http://" + device.adbHost + ":" + cloudSlot[0]
                    : "http://127.0.0.1:" + port;

            AppiumDriver driverInstance = new AndroidDriver(new URL(appiumUrl), options);

            deviceInfo.set(device);

            // Grant all dangerous permissions declared in the manifest upfront via ADB.
            // pm grant marks them as granted at system level — Android never shows a runtime
            // dialog for a permission that's already granted, so no watcher is needed.
            grantAllPermissions(device, "com.popclub.android");

            return driverInstance;

        } catch (Exception e) {
            if (device != null) {
                if (device.adbHost == null) {
                    try { AppiumServerManager.stopServer(device.udid); } catch (Exception ignored) {}
                }
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
                // Stop local Appium server only in local mode — cloud uses the STF-hosted server
                if (device.adbHost == null) {
                    AppiumServerManager.stopServer(device.udid);
                }
                DeviceManager.release(device.udid);
            }

            driver.remove();
            deviceInfo.remove();
        }
    }


    public static DeviceInfo getDeviceInfo() {
        return deviceInfo.get();
    }

    /** Builds the adb base command with optional remote-server and serial flags. */
    private static String[] adbBase(DeviceInfo device, String... args) {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("adb");
        if (device.adbHost != null) {
            cmd.add("-H"); cmd.add(device.adbHost);
            cmd.add("-P"); cmd.add(String.valueOf(device.adbServerPort));
        }
        if (device.udid != null && !device.udid.isBlank()) {
            cmd.add("-s"); cmd.add(device.udid);
        }
        for (String a : args) cmd.add(a);
        return cmd.toArray(new String[0]);
    }

    /**
     * Returns true if the given package is installed on the device.
     * Uses {@code adb shell pm list packages} — fast, no app launch needed.
     */
    private static boolean isAppInstalled(DeviceInfo device, String packageName) {
        try {
            String[] cmd = adbBase(device, "shell", "pm", "list", "packages", packageName);
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
    private static void grantAllPermissions(DeviceInfo device, String packageName) {
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

        int granted = 0;
        for (String perm : permissions) {
            try {
                String[] cmd = adbBase(device, "shell", "pm", "grant", packageName, perm);
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
