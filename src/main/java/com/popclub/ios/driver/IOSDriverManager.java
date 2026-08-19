package com.popclub.ios.driver;

import com.popclub.android.driver.DeviceInfo;
import com.popclub.core.TestContext;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;

import java.net.URL;
import java.time.Duration;

/**
 * iOS equivalent of AppiumDriverManager.
 *
 * Manages XCUITest-based Appium sessions for iOS physical devices and simulators.
 * Call getDriver() to obtain (or create) the driver for the current thread.
 * Call quitDriver() in @AfterMethod to clean up.
 */
public class IOSDriverManager {

    private static final ThreadLocal<AppiumDriver> driver     = new ThreadLocal<>();
    public  static final ThreadLocal<DeviceInfo>   deviceInfo = new ThreadLocal<>();

    private static final int APPIUM_PORT = 4850;

    /** iOS bundle ID of the POP app. */
    private static final String BUNDLE_ID = "co.popclub.pop";

    /** IPA filename under src/main/resources/. */
    private static final String IPA_FILENAME = "pop-qaDebug.ipa";

    public static AppiumDriver getDriver() {
        if (driver.get() == null) {
            driver.set(createDriver());
        }
        return driver.get();
    }

    private static AppiumDriver createDriver() {
        DeviceInfo device = null;
        try {
            device = IOSDeviceManager.getDevice();
            System.out.println("[IOSAppium] Mode: LOCAL | Device: " + device.udid);

            IOSAppiumServerManager.ensureServer(APPIUM_PORT);

            XCUITestOptions options = new XCUITestOptions();
            options.setPlatformName("iOS");
            options.setUdid(device.udid);
            options.setDeviceName("Admin's iPhone");
            options.setAutomationName("XCUITest");
            if (device.platformVersion != null && !device.platformVersion.isEmpty()) {
                options.setPlatformVersion(device.platformVersion);
            }
            options.setBundleId(BUNDLE_ID);

            boolean resumeMode = TestContext.isNoReset();
            boolean appInstalled = resumeMode && isAppInstalled(device.udid, BUNDLE_ID);

            if (resumeMode && appInstalled) {
                // Attach to running app — no reinstall, no relaunch
                options.setNoReset(true);
                options.setCapability("appium:autoLaunch", false);
                System.out.println("[IOSDriver] Resume mode — attaching to running app");
            } else {
                String ipaPath = System.getProperty("user.dir") + "/src/main/resources/" + IPA_FILENAME;
                options.setApp(ipaPath);
                options.setNoReset(false);
            }

            // Unique WDA port per parallel session to avoid conflicts
            int wdaPort = IOSAppiumServerManager.nextWdaPort();
            options.setWdaLocalPort(wdaPort);

            options.setNewCommandTimeout(Duration.ofSeconds(300));
            options.setCapability("appium:autoAcceptAlerts", false); // handle via PermissionWatcher equivalent
            options.setCapability("appium:includeSafariInWebviews", false);
            options.setCapability("appium:connectHardwareKeyboard", false);

            // Longer timeouts for WDA install on physical device
            options.setCapability("appium:wdaLaunchTimeout", 120000);
            options.setCapability("appium:wdaConnectionTimeout", 120000);

            TestContext.setFreshLaunch(!resumeMode);

            AppiumDriver driverInstance = new IOSDriver(
                    new URL("http://127.0.0.1:" + APPIUM_PORT), options);

            deviceInfo.set(device);
            return driverInstance;

        } catch (Exception e) {
            if (device != null) {
                IOSDeviceManager.release(device.udid);
            }
            e.printStackTrace();
            throw new RuntimeException("[IOSDriver] Driver creation failed", e);
        }
    }

    public static void quitDriver() {
        if (driver.get() != null) {
            try {
                driver.get().quit();
            } catch (Exception ignored) {}

            DeviceInfo device = deviceInfo.get();
            if (device != null) {
                IOSDeviceManager.release(device.udid);
            }

            driver.remove();
            deviceInfo.remove();
        }
    }

    public static DeviceInfo getDeviceInfo() {
        return deviceInfo.get();
    }

    /** Checks if the app is installed on the device via simctl (simulators only). */
    private static boolean isAppInstalled(String udid, String bundleId) {
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"xcrun", "simctl", "get_app_container", udid, bundleId});
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
