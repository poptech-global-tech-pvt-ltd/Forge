package com.popclub.mobile.driver;

import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

public class AppiumServerManager {

    private static ConcurrentHashMap<String, AppiumDriverLocalService> services = new ConcurrentHashMap<>();

    // Start OR reuse server
    public static synchronized AppiumDriverLocalService startServer(String udid, int port) {

        // Reuse if already running
        if (services.containsKey(udid)) {
            System.out.println("♻️ Reusing Appium server → " + udid);
            return services.get(udid);
        }

        AppiumDriverLocalService service =
                new AppiumServiceBuilder()
                        .usingDriverExecutable(new File("/Users/deepa/.nvm/versions/node/v20.20.1/bin/node"))
                        .withAppiumJS(new File("/Users/deepa/.nvm/versions/node/v20.20.1/lib/node_modules/appium/build/lib/main.js"))
                        .usingPort(port)
                        .withIPAddress("127.0.0.1")
                        .build();

        service.start();

        // Wait until server is ready
        while (!service.isRunning()) {
            try {
                Thread.sleep(500);
            } catch (Exception ignored) {}
        }

        System.out.println("🚀 Started Appium server → " + udid + " | port: " + port);

        services.put(udid, service);

        return service;
    }

    // DO NOT use this per test
    public static void stopServer(String udid) {

        AppiumDriverLocalService service = services.get(udid);

        if (service != null) {
            service.stop();
            System.out.println("Stopped Appium server → " + udid);
        }
    }

    // Stop ALL servers at end (IMPORTANT)
    public static void stopAll() {

        for (AppiumDriverLocalService service : services.values()) {
            try {
                service.stop();
            } catch (Exception ignored) {}
        }

        services.clear();

        System.out.println("All Appium servers stopped");
    }
}