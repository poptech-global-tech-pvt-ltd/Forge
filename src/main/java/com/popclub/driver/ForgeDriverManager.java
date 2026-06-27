package com.popclub.driver;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * ForgeDriverManager — fully automatic lifecycle for the ForgeDriver companion APK.
 *
 * Like Maestro, this installs and starts the driver APK automatically on every run.
 * No manual steps required — just run `mvn test` as usual.
 *
 * Flow (runs before every test suite):
 *  1. Ensure APK is installed on device (auto-install from bundled forge-driver.apk)
 *  2. adb forward tcp:6790 tcp:6790
 *  3. Kill any stale instrumentation process
 *  4. Start fresh instrumentation (ForgeDriverService)
 *  5. Poll /ping until server is ready (~1s)
 *
 * APK location: src/test/resources/forge-driver.apk (bundled in Forge repo)
 * Build new APK: cd /Users/deepa/repos/forge-driver && ./gradlew assembleDebugAndroidTest
 *                then copy the output APK to src/test/resources/forge-driver.apk
 */
public class ForgeDriverManager {

    private static final String APK_PACKAGE       = "com.popclub.forgedriver.test";
    private static final String RUNNER            = "androidx.test.runner.AndroidJUnitRunner";
    private static final String TEST_CLASS        = "com.popclub.forgedriver.ForgeDriverService";
    private static final int    PORT              = 6790;
    private static final int    READY_TIMEOUT_SEC = 20;

    /** Bundled APK path — relative to project root */
    private static final String BUNDLED_APK = "src/test/resources/forge-driver.apk";

    private static Process instrumentProcess;
    private static ForgeDriverClient client;
    private static boolean started = false;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start the ForgeDriver server on the device. Called once per test suite.
     * Automatically installs the APK if not already present.
     */
    public static synchronized ForgeDriverClient start(String deviceSerial) throws Exception {
        if (started && client != null && client.isAlive()) {
            System.out.println("[ForgeDriverManager] Already running — reusing session");
            return client;
        }

        System.out.println("[ForgeDriverManager] ▶ Starting ForgeDriver on " + deviceSerial);

        // 1. Auto-install APK if needed
        ensureApkInstalled(deviceSerial);

        // 2. Port forward
        adb(deviceSerial, "forward", "tcp:" + PORT, "tcp:" + PORT);
        System.out.println("[ForgeDriverManager] Port forwarded: localhost:" + PORT + " → device:" + PORT);

        // 3. Kill any stale instrumentation from previous run
        killStaleInstrumentation(deviceSerial);

        // 4. Start fresh instrumentation in background
        ProcessBuilder pb = new ProcessBuilder(
            "adb", "-s", deviceSerial,
            "shell", "am", "instrument", "-w", "-r",
            "-e", "class", TEST_CLASS,
            APK_PACKAGE + "/" + RUNNER
        );
        pb.redirectErrorStream(true);
        instrumentProcess = pb.start();

        // Stream device logs in background (non-blocking)
        Thread logger = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(instrumentProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[ForgeDriver/device] " + line);
                }
            } catch (IOException ignored) {}
        });
        logger.setDaemon(true);
        logger.start();

        // 5. Poll until server responds to /ping
        client = new ForgeDriverClient();
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_SEC * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (client.isAlive()) {
                System.out.println("[ForgeDriverManager] ✅ ForgeDriver ready (~"
                        + ((READY_TIMEOUT_SEC * 1000L - (deadline - System.currentTimeMillis())) / 1000.0)
                        + "s startup)");
                started = true;
                return client;
            }
            Thread.sleep(300);
        }

        throw new RuntimeException(
            "[ForgeDriverManager] Timed out waiting for ForgeDriver server after "
            + READY_TIMEOUT_SEC + "s. Check: adb logcat | grep ForgeDriver");
    }

    public static synchronized void stop(String deviceSerial) {
        killStaleInstrumentation(deviceSerial);
        if (instrumentProcess != null) {
            instrumentProcess.destroyForcibly();
            instrumentProcess = null;
        }
        try { adb(deviceSerial, "forward", "--remove", "tcp:" + PORT); }
        catch (Exception ignored) {}
        started = false;
        client  = null;
        System.out.println("[ForgeDriverManager] Stopped");
    }

    public static ForgeDriverClient getClient() {
        if (client == null) throw new IllegalStateException("ForgeDriverManager not started — call start() first");
        return client;
    }

    // ── APK auto-install ──────────────────────────────────────────────────────

    /**
     * Check if ForgeDriver APK is installed. If not, install from bundled APK.
     * Like Maestro: no manual install step needed.
     */
    private static void ensureApkInstalled(String deviceSerial) throws Exception {
        if (isApkInstalled(deviceSerial)) {
            System.out.println("[ForgeDriverManager] APK already installed: " + APK_PACKAGE);
            return;
        }

        System.out.println("[ForgeDriverManager] APK not found on device — installing...");
        installApk(deviceSerial);
    }

    private static boolean isApkInstalled(String deviceSerial) throws Exception {
        Process p = new ProcessBuilder(
            "adb", "-s", deviceSerial,
            "shell", "pm", "list", "packages", APK_PACKAGE
        ).redirectErrorStream(true).start();

        String output = new String(p.getInputStream().readAllBytes());
        p.waitFor(10, TimeUnit.SECONDS);
        return output.contains(APK_PACKAGE);
    }

    private static void installApk(String deviceSerial) throws Exception {
        // Resolve bundled APK path relative to project working dir
        Path apkPath = Path.of(BUNDLED_APK);

        if (!Files.exists(apkPath)) {
            throw new RuntimeException(
                "[ForgeDriverManager] Bundled APK not found at: " + apkPath.toAbsolutePath() + "\n" +
                "Build it from forge-driver repo:\n" +
                "  cd /Users/deepa/repos/forge-driver\n" +
                "  ./gradlew assembleDebugAndroidTest\n" +
                "  cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \\\n" +
                "     /Users/deepa/repos/Forge/" + BUNDLED_APK
            );
        }

        System.out.println("[ForgeDriverManager] Installing: " + apkPath.toAbsolutePath());
        Process p = new ProcessBuilder(
            "adb", "-s", deviceSerial,
            "install", "-r", "-t", apkPath.toAbsolutePath().toString()
        ).redirectErrorStream(true).start();

        String output = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();

        if (code != 0 || !output.contains("Success")) {
            throw new RuntimeException("[ForgeDriverManager] APK install failed:\n" + output);
        }
        System.out.println("[ForgeDriverManager] ✅ APK installed successfully");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void killStaleInstrumentation(String deviceSerial) {
        try {
            // Kill any running instance of our test package
            new ProcessBuilder(
                "adb", "-s", deviceSerial,
                "shell", "am", "force-stop", APK_PACKAGE
            ).redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }

    private static void adb(String serial, String... args) throws Exception {
        String[] full = new String[args.length + 3];
        full[0] = "adb";
        full[1] = "-s";
        full[2] = serial;
        System.arraycopy(args, 0, full, 3, args.length);
        new ProcessBuilder(full).redirectErrorStream(true)
                .start().waitFor(10, TimeUnit.SECONDS);
    }
}
