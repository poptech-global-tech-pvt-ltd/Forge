package com.popclub.mobile.driver;

import com.popclub.core.TestContext;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.time.Duration;

public class AppiumDriverManager {

    private static ThreadLocal<AppiumDriver> driver = new ThreadLocal<>();
    public static ThreadLocal<DeviceInfo> deviceInfo = new ThreadLocal<>();

    public static AppiumDriver getDriver() {

        if (driver.get() == null) {
            driver.set(createDriver());
        }

        return driver.get();
    }

    private static AppiumDriver createDriver() {

        try {

            // Get device + port
            DeviceInfo device = DeviceManager.getDevice();

            String udid = device.udid;
            int port = device.port;

            System.out.println(
                    "Thread: " + Thread.currentThread().getId() +
                            " | Device: " + udid +
                            " | Port: " + port
            );

            //  Start Appium server
            AppiumServerManager.startServer(udid, port);

            UiAutomator2Options options = new UiAutomator2Options();

            options.setPlatformName("Android");
            options.setDeviceName(udid);
            options.setUdid(udid);
            options.setAutomationName("UiAutomator2");
            options.setAutoGrantPermissions(true);

            options.setApp(System.getProperty("user.dir") +
                    "/src/main/resources/pop-debug.apk");

            //  Reset behavior
            options.setNoReset(false);
            TestContext.setFreshLaunch(true);

            options.setNewCommandTimeout(Duration.ofSeconds(300));

            // Unique host port for UiAutomator2 (avoids stale-session collisions on fixed 8200+port)
            int systemPort;
            try (ServerSocket socket = new ServerSocket(0)) {
                systemPort = socket.getLocalPort();
            } catch (IOException e) {
                systemPort = 8200 + port;
            }
            options.setSystemPort(systemPort);

            AppiumDriver driverInstance = new AndroidDriver(
                    new URL("http://127.0.0.1:" + port ),
                    options
            );

            // store device info
            deviceInfo.set(device);

            return driverInstance;

        } catch (Exception e) {
            e.printStackTrace();
            throw

                    new RuntimeException("Driver creation failed", e);
        }
    }

    public static void quitDriver() {

        if (driver.get() != null) {

            driver.get().quit();

            DeviceInfo device = deviceInfo.get();

            if (device != null) {
                AppiumServerManager.stopServer(device.udid);
            }

            driver.remove();
            deviceInfo.remove();
        }
    }

    public static DeviceInfo getDeviceInfo() {
        return deviceInfo.get();
    }
}

