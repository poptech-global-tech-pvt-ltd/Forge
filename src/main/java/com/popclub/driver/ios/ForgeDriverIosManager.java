package com.popclub.driver.ios;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * ForgeDriverIosManager — manages the lifecycle of the iOS XCTest HTTP server.
 *
 * Responsibilities:
 *  1. Run install.sh to build + launch the XCTest UITest runner (which starts the HTTP server)
 *  2. Poll /ping until the server is ready on localhost:7791
 *  3. On shutdown: POST /stop to signal the XCTest runner to exit
 *
 * On simulators the server is on localhost:7791 directly (no port forwarding needed).
 * On real devices, install.sh handles iproxy forwarding.
 *
 * Usage:
 *   ForgeDriverIosClient driver = ForgeDriverIosManager.start("booted");
 *   // ... run tests ...
 *   ForgeDriverIosManager.stop();
 */
public class ForgeDriverIosManager {

    private static final int PORT             = 7791;
    private static final int READY_TIMEOUT_SEC = 60;

    private static Process   xcodebuildProcess;
    private static ForgeDriverIosClient client;
    private static boolean started = false;

    public static synchronized ForgeDriverIosClient start(String deviceId) throws Exception {
        if (started && client != null && client.isAlive()) {
            System.out.println("[ForgeDriverIosManager] Already running — reusing");
            return client;
        }

        System.out.println("[ForgeDriverIosManager] Launching XCTest runner on device: " + deviceId);

        String forgeDriverIosPath = System.getProperty("forge.driver.ios.path",
                System.getProperty("user.home") + "/repos/forge-driver-ios");

        // Run install.sh in background (it blocks xcodebuild test-without-building)
        ProcessBuilder pb = new ProcessBuilder("bash", "install.sh", deviceId)
                .directory(new java.io.File(forgeDriverIosPath))
                .redirectErrorStream(true);
        xcodebuildProcess = pb.start();

        Thread logger = new Thread(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(xcodebuildProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[ForgeDriveriOS/device] " + line);
                }
            } catch (IOException ignored) {}
        });
        logger.setDaemon(true);
        logger.start();

        // Poll /ping until the server is ready
        client = new ForgeDriverIosClient();
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_SEC * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (client.isAlive()) {
                System.out.println("[ForgeDriverIosManager] iOS driver ready on port " + PORT);
                started = true;
                return client;
            }
            Thread.sleep(1000);
        }

        throw new RuntimeException(
                "[ForgeDriverIosManager] Timed out waiting for iOS driver to start. " +
                "Run install.sh manually and check for Xcode build errors.");
    }

    public static synchronized void stop() {
        if (client != null) {
            try { client.stop(); } catch (Exception ignored) {}
            client = null;
        }
        if (xcodebuildProcess != null) {
            xcodebuildProcess.destroyForcibly();
            xcodebuildProcess = null;
        }
        started = false;
        System.out.println("[ForgeDriverIosManager] Stopped");
    }

    public static ForgeDriverIosClient getClient() {
        if (client == null) throw new IllegalStateException("ForgeDriverIosManager not started");
        return client;
    }
}
