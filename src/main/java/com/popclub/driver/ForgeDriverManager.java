package com.popclub.driver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * ForgeDriverManager — manages the lifecycle of the ForgeDriver companion APK.
 *
 * Responsibilities:
 *  1. adb forward tcp:6790 tcp:6790       (port forward)
 *  2. adb shell am instrument -w ...      (start the server on device)
 *  3. Poll /ping until the server is ready
 *  4. On shutdown: kill the instrumentation process
 *
 * Call ForgeDriverManager.start() once before your test suite.
 * Call ForgeDriverManager.stop() in suite teardown.
 */
public class ForgeDriverManager {

    private static final String APK_PACKAGE  = "com.popclub.forgedriver.test";
    private static final String RUNNER       = "androidx.test.runner.AndroidJUnitRunner";
    private static final int    PORT         = 6790;
    private static final int    READY_TIMEOUT_SEC = 30;

    private static Process instrumentProcess;
    private static ForgeDriverClient client;
    private static boolean started = false;

    public static synchronized ForgeDriverClient start(String deviceSerial) throws Exception {
        if (started && client != null && client.isAlive()) {
            System.out.println("[ForgeDriverManager] Already running — reusing");
            return client;
        }

        System.out.println("[ForgeDriverManager] Starting ForgeDriver on device: " + deviceSerial);

        // 1. Port forward
        adb(deviceSerial, "forward", "tcp:" + PORT, "tcp:" + PORT);
        System.out.println("[ForgeDriverManager] Port forwarded: " + PORT);

        // 2. Start instrumentation in background
        ProcessBuilder pb = new ProcessBuilder(
            "adb", "-s", deviceSerial,
            "shell", "am", "instrument", "-w", "-r",
            "-e", "class", "com.popclub.forgedriver.ForgeDriverService",
            APK_PACKAGE + "/" + RUNNER
        );
        pb.redirectErrorStream(true);
        instrumentProcess = pb.start();

        // Stream output in background thread (so we can see device logs)
        Thread logger = new Thread(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(instrumentProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[ForgeDriver/device] " + line);
                }
            } catch (IOException ignored) {}
        });
        logger.setDaemon(true);
        logger.start();

        // 3. Wait until server is ready
        client = new ForgeDriverClient();
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_SEC * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (client.isAlive()) {
                System.out.println("[ForgeDriverManager] ✅ ForgeDriver ready on port " + PORT);
                started = true;
                return client;
            }
            Thread.sleep(500);
        }

        throw new RuntimeException("[ForgeDriverManager] Timed out waiting for ForgeDriver to start. " +
                "Is the APK installed? Run: ./forge-driver/install.sh");
    }

    public static synchronized void stop(String deviceSerial) {
        if (instrumentProcess != null) {
            instrumentProcess.destroyForcibly();
            instrumentProcess = null;
        }
        try {
            adb(deviceSerial, "forward", "--remove", "tcp:" + PORT);
        } catch (Exception ignored) {}
        started = false;
        client  = null;
        System.out.println("[ForgeDriverManager] Stopped");
    }

    public static ForgeDriverClient getClient() {
        if (client == null) throw new IllegalStateException("ForgeDriverManager not started");
        return client;
    }

    private static void adb(String serial, String... args) throws Exception {
        String[] cmd = new String[args.length + 2];
        cmd[0] = "adb";
        cmd[1] = "-s";
        // Note: serial inserted at index 2, remaining args shifted
        String[] full = new String[args.length + 3];
        full[0] = "adb";
        full[1] = "-s";
        full[2] = serial;
        System.arraycopy(args, 0, full, 3, args.length);

        Process p = new ProcessBuilder(full).redirectErrorStream(true).start();
        p.waitFor(10, TimeUnit.SECONDS);
    }
}
