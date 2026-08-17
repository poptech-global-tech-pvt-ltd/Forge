package com.popclub.ios.driver;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages Appium server instances for iOS test sessions.
 *
 * iOS uses XCUITest driver which communicates via WDA (WebDriverAgent).
 * Each parallel session needs a unique wdaLocalPort to avoid conflicts.
 *
 * Unlike Android, iOS Appium sessions don't need a separate per-device
 * server process — one Appium server handles multiple sessions.
 * We start a single shared Appium server and assign unique wdaLocalPorts per session.
 */
public class IOSAppiumServerManager {

    private static final int BASE_APPIUM_PORT = 4850;
    private static final int BASE_WDA_PORT    = 8100;

    private static final AtomicInteger portCounter = new AtomicInteger(0);
    private static volatile Process appiumProcess;
    private static volatile int appiumPort;

    /** Returns the next available Appium port for a new iOS session. */
    public static synchronized int nextPort() {
        int slot = portCounter.getAndIncrement();
        return BASE_APPIUM_PORT + (slot * 2);
    }

    /** Returns a unique wdaLocalPort for a parallel iOS session slot. */
    public static int nextWdaPort() {
        int slot = portCounter.get();
        return BASE_WDA_PORT + slot;
    }

    /**
     * Ensures a local Appium server is running for iOS sessions.
     * Reuses an already-running server if healthy.
     */
    public static synchronized void ensureServer(int port) {
        if (isAppiumUp(port)) {
            System.out.println("[IOSAppium] Server already running on port " + port);
            appiumPort = port;
            return;
        }

        System.out.println("[IOSAppium] Starting server on port " + port + "...");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "appium",
                    "--port", String.valueOf(port),
                    "--relaxed-security",
                    "--allow-cors",
                    "--log-level", "info"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            Thread logThread = new Thread(() -> {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[IOSAppium] " + line);
                    }
                } catch (Exception ignored) {}
            }, "ios-appium-log-" + port);
            logThread.setDaemon(true);
            logThread.start();

            appiumProcess = process;
            appiumPort = port;

            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                if (isAppiumUp(port)) {
                    System.out.println("[IOSAppium] Server ready on port " + port);
                    return;
                }
                if (!process.isAlive()) {
                    throw new RuntimeException("[IOSAppium] Server process exited unexpectedly");
                }
                Thread.sleep(500);
            }
            throw new RuntimeException("[IOSAppium] Server did not become ready within 30s on port " + port);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[IOSAppium] Failed to start server: " + e.getMessage(), e);
        }
    }

    public static void stopAll() {
        if (appiumProcess != null && appiumProcess.isAlive()) {
            appiumProcess.destroy();
            System.out.println("[IOSAppium] Server stopped");
        }
    }

    private static boolean isAppiumUp(int port) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("http://127.0.0.1:" + port + "/status").openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
