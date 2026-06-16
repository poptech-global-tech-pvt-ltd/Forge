package com.popclub.mobile.driver;

import com.popclub.core.TestContext;
import com.popclub.mobile.cloud.CloudConfig;
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
            options.setAutoGrantPermissions(true);

            boolean resumeMode = TestContext.isNoReset();
            if (resumeMode) {
                // Resume mode (run-from-step): attach to the already-running app.
                // Do NOT set `app` — that triggers install + launch.
                // Do NOT autoLaunch — keep whatever is on screen.
                options.setAppPackage("com.popclub.android");
                options.setCapability("appium:autoLaunch", false);
                options.setNoReset(true);
                System.out.println("[Driver] Resume mode — attaching to running app (no install, no launch)");
            } else {
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
}
