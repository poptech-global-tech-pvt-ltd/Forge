package com.popclub.android.driver;

import io.appium.java_client.AppiumDriver;

import static com.popclub.android.driver.AppiumDriverManager.deviceInfo;


public class DriverManager {
    static DeviceInfo device = deviceInfo.get();

    private static ThreadLocal<AppiumDriver> driver = new ThreadLocal<>();

    public static AppiumDriver getDriver() {
        return driver.get();
    }

    public static void setDriver(AppiumDriver d) {
        driver.set(d);
    }

    public static void quitDriver() {
        if (driver.get() != null) {
            DeviceManager.release(device.udid);
            driver.get().quit();
            driver.remove();
        }
    }
}