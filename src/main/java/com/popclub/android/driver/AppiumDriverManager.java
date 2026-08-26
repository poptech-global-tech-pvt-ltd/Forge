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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class AppiumDriverManager {

    private static ThreadLocal<AppiumDriver> driver     = new ThreadLocal<>();
    public  static ThreadLocal<DeviceInfo>   deviceInfo = new ThreadLocal<>();

    // One lock per device serial — prevents two tests from creating sessions on the same device simultaneously.
    private static final ConcurrentHashMap<String, ReentrantLock> DEVICE_LOCKS   = new ConcurrentHashMap<>();
    private static final ThreadLocal<String>                      LOCKED_SERIAL  = new ThreadLocal<>();

    // Remaining test count per device — session is kept alive until this hits 0.
    private static final ConcurrentHashMap<String, AtomicInteger> DEVICE_TEST_COUNTERS = new ConcurrentHashMap<>();

    private static ReentrantLock lockFor(String serial) {
        return DEVICE_LOCKS.computeIfAbsent(serial, k -> new ReentrantLock(true));
    }

    /**
     * Register how many tests will run on a device before they start.
     * Called once from TestRunnerTest's @DataProvider after the test list is built.
     */
    public static void setExpectedTestCount(String serial, int count) {
        if (serial == null || serial.isBlank()) return;
        DEVICE_TEST_COUNTERS.put(serial, new AtomicInteger(count));
        System.out.println("[Device] Expected test count for " + serial + ": " + count);
    }

    public static AppiumDriver getDriver() {
        // Reuse an existing live session if one is held for this device.
        if (driver.get() != null) {
            try {
                driver.get().getSessionId(); // lightweight liveness check
                return driver.get();
            } catch (Exception e) {
                System.out.println("[Device] Session dead — will create a new one: " + e.getMessage());
                driver.remove();
                deviceInfo.remove();
                // Don't release the STF device or unlock — we still own it, just need a new session.
            }
        }
        String serial = CloudConfig.getDeviceSerial();
        if (serial != null && !serial.isBlank()) {
            ReentrantLock lock = lockFor(serial);
            // Only acquire the lock if we don't already hold it (i.e. first test on this device).
            if (!lock.isHeldByCurrentThread()) {
                System.out.println("[Device] Waiting for device lock: " + serial);
                lock.lock();
                LOCKED_SERIAL.set(serial);
                System.out.println("[Device] Acquired device lock: " + serial);
            }
        }
        driver.set(createDriver());
        return driver.get();
    }

    private static AppiumDriver createDriver() {
        DeviceInfo device = null;
        try {
            // 1. Obtain a device (local ADB pool  OR  STF cloud reservation)
            device = DeviceManager.getDevice();
            String udid       = device.udid;
            int    port       = device.port;

            System.out.println("[Appium] Mode: " + (CloudConfig.isCloudEnabled() ? "CLOUD" : "LOCAL")
                    + " | Device: " + udid + " | Port: " + port);

            // 2. Start Appium server.
            //    • Local  → start a local Appium server on this machine
            //    • Cloud  → one pre-started Appium per parallel slot on the STF Mac;
            //               claim a slot (unique appium port + systemPort), then verify it's up
            int[] cloudSlot = null;
            if (device.adbHost != null) {
                cloudSlot = AppiumServerManager.nextCloudSlot();
                AppiumServerManager.ensureRemoteServer(device.adbHost, cloudSlot[0]);
            } else {
                AppiumServerManager.startServer(udid, port); // starts or reuses local server
            }

            // 3. Build capabilities
            UiAutomator2Options options = new UiAutomator2Options();
            options.setPlatformName(device.platformName != null ? device.platformName : "Android");
            options.setDeviceName(udid);
            options.setUdid(udid);
            // No remoteAdbHost — for cloud the Appium server runs on the STF host and has
            // direct local ADB access to the device (serial is known to that ADB daemon).
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
            boolean appInstalled = resumeMode && isAppInstalled(device, "com.popclub.android");

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
                // Unlock the device using password, PIN, or skip if neither is configured.
                String unlockPassword = CloudConfig.getDeviceUnlockPassword();
                String unlockPin     = CloudConfig.getDeviceUnlockPin();
                if (unlockPassword != null && !unlockPassword.isBlank()) {
                    options.setCapability("appium:unlockType", "password");
                    options.setCapability("appium:unlockKey", unlockPassword);
                } else if (unlockPin != null && !unlockPin.isBlank()) {
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

            // Unlock device before any test steps run
            unlockDevice((AndroidDriver) driverInstance);

            return driverInstance;

        } catch (Exception e) {
            if (device != null) {
                // Don't stop the Appium server on session failure — keep it alive for next test.
                DeviceManager.release(device.udid);
            }
            e.printStackTrace();
            throw new RuntimeException("Driver creation failed", e);
        }
    }

    public static void quitDriver() {
        String serial = LOCKED_SERIAL.get();

        // Decrement the counter for this device. If tests remain, keep the session alive.
        if (serial != null) {
            AtomicInteger counter = DEVICE_TEST_COUNTERS.get(serial);
            if (counter != null) {
                int remaining = counter.decrementAndGet();
                if (remaining > 0) {
                    System.out.println("[Device] " + remaining + " test(s) remaining on " + serial
                            + " — keeping session alive.");
                    return; // lock stays held, driver stays alive, STF stays reserved
                }
            }
        }

        // Last test (or no counter registered) — do the full teardown.
        if (driver.get() != null) {
            try {
                driver.get().quit();
            } catch (Exception ignored) {}

            DeviceInfo device = deviceInfo.get();
            if (device != null) {
                DeviceManager.release(device.udid);
            }

            driver.remove();
            deviceInfo.remove();
        }

        // Release the per-device lock.
        if (serial != null) {
            ReentrantLock lock = DEVICE_LOCKS.get(serial);
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
                System.out.println("[Device] Released device lock: " + serial);
            }
            LOCKED_SERIAL.remove();
        }
    }


    public static DeviceInfo getDeviceInfo() {
        return deviceInfo.get();
    }

    private static void unlockDevice(io.appium.java_client.android.AndroidDriver driver) {
        try {
            if (!driver.isDeviceLocked()) return;

            String pin = com.popclub.android.cloud.CloudConfig.getDeviceUnlockPin();
            DeviceInfo device = deviceInfo.get();

            // Wake screen
            adbRun(device, "shell", "input", "keyevent", "26");
            Thread.sleep(500);
            // Dismiss keyguard / swipe up
            adbRun(device, "shell", "input", "keyevent", "82");
            Thread.sleep(500);

            if (pin != null && !pin.isBlank()) {
                adbRun(device, "shell", "input", "text", pin);
                Thread.sleep(200);
                adbRun(device, "shell", "input", "keyevent", "66"); // ENTER
                Thread.sleep(500);
                System.out.println("[Device] Unlocked with PIN.");
            } else {
                System.out.println("[Device] No PIN configured — swipe-unlock only.");
            }
        } catch (Exception e) {
            System.out.println("[Device] Unlock skipped: " + e.getMessage());
        }
    }

    private static void adbRun(DeviceInfo device, String... args) throws Exception {
        Runtime.getRuntime().exec(adbBase(device, args)).waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
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
