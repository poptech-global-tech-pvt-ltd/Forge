package com.popclub.android.driver;

import com.popclub.android.cloud.CloudConfig;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppiumServerManager {

    private static final ConcurrentHashMap<String, AppiumDriverLocalService> services =
            new ConcurrentHashMap<>();

    /** Start (or reuse) a local Appium server for the given device + port. */
    public static synchronized AppiumDriverLocalService startServer(String udid, int port) {

        if (services.containsKey(udid)) {
            System.out.println("♻️ Reusing Appium server → " + udid);
            return services.get(udid);
        }

        // Paths come from cloud.properties (or -D system properties) so they
        // are not hard-coded to any individual developer's machine.
        String nodePath   = CloudConfig.getNodePath();
        String appiumPath = CloudConfig.getAppiumJsPath();

        AppiumServiceBuilder builder =
                new AppiumServiceBuilder()
                        .usingDriverExecutable(new File(nodePath))
                        .withAppiumJS(new File(appiumPath))
                        .usingPort(port)
                        .withIPAddress("127.0.0.1");

        // Pass ANDROID_HOME / ANDROID_SDK_ROOT so Appium can locate aapt2
        String androidHome = CloudConfig.getAndroidHome();
        if (androidHome != null) {
            Map<String, String> env = new HashMap<>();
            env.put("ANDROID_HOME", androidHome);
            env.put("ANDROID_SDK_ROOT", androidHome);
            builder.withEnvironment(env);
            System.out.println("[Appium] ANDROID_HOME → " + androidHome);
        }

        AppiumDriverLocalService service = builder.build();

        System.out.println("Starting Appium server on port " + port + " ...");
        service.start();

        int waited = 0;
        while (!service.isRunning()) {
            try { Thread.sleep(500); } catch (Exception ignored) {}
            waited += 500;
            if (waited % 3000 == 0) {
                System.out.println("  Still waiting for Appium... (" + (waited / 1000) + "s)");
            }
            if (waited >= 30000) {
                throw new RuntimeException(
                    "Appium server did not start within 30s on port " + port +
                    ". Check that node and appium paths in local.cloud.properties are correct.\n" +
                    "  appium.node.path = " + CloudConfig.getNodePath() + "\n" +
                    "  appium.js.path   = " + CloudConfig.getAppiumJsPath()
                );
            }
        }

        System.out.println("Appium server started → " + udid + " | port: " + port);
        services.put(udid, service);
        return service;
    }

    public static void stopServer(String udid) {
        AppiumDriverLocalService service = services.remove(udid);
        if (service != null) {
            service.stop();
            System.out.println("Stopped Appium server → " + udid);
        }
    }

    /** Stop ALL servers (call this at suite teardown). */
    public static void stopAll() {
        for (AppiumDriverLocalService service : services.values()) {
            try { service.stop(); } catch (Exception ignored) {}
        }
        services.clear();
        System.out.println("All Appium servers stopped");
    }
}
