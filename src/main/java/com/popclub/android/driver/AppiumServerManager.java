package com.popclub.android.driver;

import com.popclub.android.cloud.CloudConfig;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AppiumServerManager {

    private static final ConcurrentHashMap<String, AppiumDriverLocalService> services =
            new ConcurrentHashMap<>();

    // Predefined Appium ports running on the STF Mac — one server per parallel slot.
    // Configurable via appium.remote.ports in local.cloud.properties (comma-separated).
    // Default matches appium_manager.py defaults: 4723, 4725, 4727, 4729, 4731.
    private static final int[] CLOUD_APPIUM_PORTS = resolveCloudPorts();

    // Slot counter — each parallel session claims the next slot (wraps around if > port count).
    private static final AtomicInteger slotCounter = new AtomicInteger(0);

    private static int[] resolveCloudPorts() {
        String val = CloudConfig.getAppiumRemotePorts();
        if (val != null && !val.isBlank()) {
            String[] parts = val.split(",");
            int[] ports = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                ports[i] = Integer.parseInt(parts[i].trim());
            }
            System.out.println("[Appium] Cloud ports: " + java.util.Arrays.toString(ports));
            return ports;
        }
        return new int[]{4723, 4725, 4727, 4729, 4731};
    }

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

        String androidHome = CloudConfig.getAndroidHome();

        AppiumServiceBuilder builder =
                new AppiumServiceBuilder()
                        .usingDriverExecutable(new File(nodePath))
                        .withAppiumJS(new File(appiumPath))
                        .usingPort(port)
                        .withIPAddress("127.0.0.1")
                        .withArgument(() -> "--relaxed-security");

        // Pass ANDROID_HOME / ANDROID_SDK_ROOT so Appium/UiAutomator2 can locate the SDK.
        // withEnvironment replaces the whole env, so start from the current process env.
        Map<String, String> env = new HashMap<>();
        try { env.putAll(System.getenv()); } catch (Exception ignored) {}
        if (androidHome != null) {
            env.put("ANDROID_HOME", androidHome);
            env.put("ANDROID_SDK_ROOT", androidHome);
            env.put("PATH", androidHome + "/platform-tools:" + androidHome + "/tools:" + env.getOrDefault("PATH", ""));
            System.out.println("[Appium] ANDROID_HOME → " + androidHome);
        }
        builder.withEnvironment(env);

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

    /**
     * Claims the next available parallel slot and returns [appiumPort, systemPort].
     * Appium port: fixed per slot (4723, 4725, …) so each parallel session uses its own server.
     * systemPort: random in 18200-19200 range — avoids collisions with stale ADB forwards
     * from previous runs (which always reuse the same counter-based ports).
     * Thread-safe.
     */
    public static int[] nextCloudSlot() {
        int slot = slotCounter.getAndIncrement() % CLOUD_APPIUM_PORTS.length;
        int appiumPort = CLOUD_APPIUM_PORTS[slot];
        int systemPort = 18200 + java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 1000);
        System.out.println("[Appium] Cloud slot " + slot +
                " → Appium port " + appiumPort + " | systemPort " + systemPort);
        return new int[]{appiumPort, systemPort};
    }

    /**
     * Checks that a remote Appium server is reachable on the STF host.
     * Throws a clear error with startup instructions if it is not running.
     * Appium must be started manually on the STF Mac before running tests:
     *   ANDROID_HOME=<sdk-path> nohup appium --port 4723 --relaxed-security --allow-cors > ~/appium.log 2>&1 &
     */
    public static void ensureRemoteServer(String host, int port) {
        String url = "http://" + host + ":" + port + "/status";

        if (isRemoteAppiumUp(url)) {
            System.out.println("[Appium] Remote server already running at " + url);
            return;
        }

        throw new RuntimeException(
            "[Appium] Remote Appium is not running at " + url + ".\n" +
            "Start it on the STF Mac (" + host + ") before running tests:\n" +
            "  ANDROID_HOME=<sdk-path> nohup appium --port " + port +
            " --relaxed-security --allow-cors > ~/appium.log 2>&1 &\n" +
            "Then verify with:  curl " + url
        );
    }

    private static boolean isRemoteAppiumUp(String statusUrl) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(statusUrl).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
