package com.popclub.driver;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ForgeDriverManager — manages per-device ForgeDriver instances for parallel test runs.
 *
 * Local mode  (adbHost=null):
 *   adb -s serial forward tcp:&lt;port&gt; tcp:7790   → gRPC on localhost:&lt;port&gt;
 *
 * Remote/STF mode (adbHost set):
 *   adb -H host forward tcp:&lt;port&gt; tcp:7790     → gRPC on adbHost:&lt;port&gt;
 *   Requires socat listening on every port in forge.port.range on the STF server.
 *   max-children/connect-timeout are required — without them, failed/hung relays
 *   accumulate child processes until socat's fork() starts failing with EAGAIN:
 *     for p in $(seq 7790 7799); do
 *       socat TCP-LISTEN:$p,fork,reuseaddr,max-children=5 TCP:localhost:$p,connect-timeout=5 &amp;
 *     done
 *
 * Ports are drawn from ForgeDriverPortPool (forge.port.range, default 7790-7799).
 * Each device holds one port for the duration of the test; it is released on stop().
 */
public class ForgeDriverManager {

    private static final String APK_PACKAGE       = "com.popclub.forgedriver.test";
    private static final String TARGET_PACKAGE    = "com.popclub.forgedriver"; // app-under-test; instrumentation needs it installed too
    private static final String RUNNER            = "androidx.test.runner.AndroidJUnitRunner";
    private static final int    DEVICE_PORT       = 7790; // ForgeDriver always listens on 7790 on-device
    private static final int    ADB_SERVER_PORT   = 5037;
    private static final int    READY_TIMEOUT_SEC = 60;
    private static final int    PORT_ACQUIRE_TIMEOUT_MS = 60_000;

    private static class Instance {
        final Process instrumentProcess;
        final ForgeDriverClient client;
        final int hostPort;
        final String adbHost;

        Instance(Process instrumentProcess, ForgeDriverClient client, int hostPort, String adbHost) {
            this.instrumentProcess = instrumentProcess;
            this.client            = client;
            this.hostPort          = hostPort;
            this.adbHost           = adbHost;
        }
    }

    private static final ConcurrentHashMap<String, Instance> instances = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    public static ForgeDriverClient start(String deviceSerial) throws Exception {
        return start(deviceSerial, null);
    }

    public static ForgeDriverClient start(String deviceSerial, String adbHost) throws Exception {
        // Reuse if already alive for this device
        Instance existing = instances.get(deviceSerial);
        if (existing != null && existing.client.isAlive()) {
            System.out.println("[ForgeDriverManager] Reusing existing instance for " + deviceSerial);
            return existing.client;
        }

        boolean remote = adbHost != null && !adbHost.isBlank();
        System.out.println("[ForgeDriverManager] Starting ForgeDriver on " + deviceSerial
                + (remote ? " via " + adbHost : " (local)"));

        // 0. Force-stop any stale ForgeDriver process from a previous killed run
        //    so the device port (7790) is freed. Use --remove tcp:7790 only —
        //    --remove-all would clear STF-managed forwards for other ports too.
        try {
            adb(deviceSerial, adbHost, "shell", "am", "force-stop", APK_PACKAGE);
            adb(deviceSerial, adbHost, "forward", "--remove", "tcp:" + DEVICE_PORT);
            Thread.sleep(800);
        } catch (Exception ignored) {}

        // 0b. Ensure APK is installed
        ensureInstalled(deviceSerial, adbHost);

        // 1. Acquire a free port from the pool
        int hostPort = ForgeDriverPortPool.acquire(PORT_ACQUIRE_TIMEOUT_MS);

        try {
            // 2. Forward hostPort on the ADB server host → device port 7790
            adb(deviceSerial, adbHost, "forward", "tcp:" + hostPort, "tcp:" + DEVICE_PORT);
            System.out.println("[ForgeDriverManager] Forwarded "
                    + (remote ? adbHost : "localhost") + ":" + hostPort
                    + " → device:" + DEVICE_PORT);

            // 3. Start ForgeDriver instrumentation on the device
            String[] instrCmd = buildAdbCmd(deviceSerial, adbHost,
                    "shell", "am", "instrument", "-w", "-r",
                    "-e", "class", "com.popclub.forgedriver.ForgeDriverService",
                    APK_PACKAGE + "/" + RUNNER);
            Process instrProc = new ProcessBuilder(instrCmd)
                    .redirectErrorStream(true).start();

            Thread logger = new Thread(() -> {
                try (var reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(instrProc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[ForgeDriver/" + deviceSerial + "] " + line);
                    }
                } catch (IOException ignored) {}
            });
            logger.setDaemon(true);
            logger.start();

            // 3b. Mirror device-side logcat scoped to the ForgeDriver test process's own PID.
            //     A tag filter (AndroidRuntime:E) is system-wide and drowns in unrelated app
            //     crashes on a shared STF device — --pid isolates exactly this process's output,
            //     including a silent hang before it ever logs anything.
            String testPid = findProcessPid(deviceSerial, adbHost, APK_PACKAGE);
            Process logcatProc;
            if (testPid != null) {
                logcatProc = new ProcessBuilder(
                        buildAdbCmd(deviceSerial, adbHost, "logcat", "-v", "brief", "--pid=" + testPid))
                        .redirectErrorStream(true).start();
                System.out.println("[ForgeDriverManager] Mirroring logcat for pid " + testPid
                        + " (" + APK_PACKAGE + ")");
            } else {
                // Fallback: couldn't resolve a PID in time — use the old tag filter so we still
                // see ForgeDriver's own tag plus crash traces, accepting some unrelated noise.
                logcatProc = new ProcessBuilder(
                        buildAdbCmd(deviceSerial, adbHost, "logcat", "-v", "brief",
                                "ForgeDriver:V", "AndroidRuntime:E", "*:S"))
                        .redirectErrorStream(true).start();
                System.out.println("[ForgeDriverManager] Could not resolve PID for " + APK_PACKAGE
                        + " — falling back to tag-filtered logcat");
            }
            Thread logcatThread = new Thread(() -> {
                try (var reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(logcatProc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[logcat/" + deviceSerial + "] " + line);
                    }
                } catch (IOException ignored) {}
            });
            logcatThread.setDaemon(true);
            logcatThread.start();

            // 4. Wait until gRPC server is ready.
            //    Remote: connect to adbHost:hostPort (socat relays to 127.0.0.1:hostPort → device:7790)
            //    Local:  connect to localhost:hostPort
            String grpcHost = remote ? adbHost : "localhost";
            ForgeDriverClient client = new ForgeDriverClient(grpcHost, hostPort);

            long deadline = System.currentTimeMillis() + READY_TIMEOUT_SEC * 1000L;
            long lastForwardMs = System.currentTimeMillis();
            while (System.currentTimeMillis() < deadline) {
                if (client.isAlive()) {
                    System.out.println("[ForgeDriverManager] ✅ ForgeDriver ready on "
                            + grpcHost + ":" + hostPort + " (device " + deviceSerial + ")");
                    logcatProc.destroyForcibly(); // one-shot diagnostic — not needed once connected
                    instances.put(deviceSerial, new Instance(instrProc, client, hostPort, adbHost));
                    return client;
                }
                // Re-issue adb forward every 5s — in cloud mode the forward can drop if the
                // ADB server on the STF host restarts, causing "connection refused" in the relay.
                if (System.currentTimeMillis() - lastForwardMs >= 5_000) {
                    try {
                        adb(deviceSerial, adbHost, "forward", "tcp:" + hostPort, "tcp:" + DEVICE_PORT);
                        System.out.println("[ForgeDriverManager] Re-issued forward tcp:"
                                + hostPort + " → device:" + DEVICE_PORT);
                        lastForwardMs = System.currentTimeMillis();
                    } catch (Exception ignored) {}
                }
                Thread.sleep(500);
            }

            // Timed out — clean up the port we acquired
            instrProc.destroyForcibly();
            logcatProc.destroyForcibly();
            adb(deviceSerial, adbHost, "forward", "--remove", "tcp:" + hostPort);
            ForgeDriverPortPool.release(hostPort);
            throw new RuntimeException("[ForgeDriverManager] Timed out waiting for ForgeDriver on "
                    + deviceSerial + ". Is the APK installed? Run: ./forge-driver/install.sh");

        } catch (Exception e) {
            // Release port on any startup failure
            if (!e.getMessage().contains("Timed out")) { // already released above on timeout
                ForgeDriverPortPool.release(hostPort);
            }
            throw e;
        }
    }

    public static void stop(String deviceSerial) {
        Instance inst = instances.remove(deviceSerial);
        if (inst == null) return;
        if (inst.instrumentProcess != null) {
            inst.instrumentProcess.destroyForcibly();
        }
        try {
            adb(deviceSerial, inst.adbHost, "forward", "--remove", "tcp:" + inst.hostPort);
        } catch (Exception ignored) {}
        ForgeDriverPortPool.release(inst.hostPort);
        System.out.println("[ForgeDriverManager] Stopped device " + deviceSerial
                + ", released port " + inst.hostPort);
    }

    /** Returns the active client for a device, or null if not started. */
    public static ForgeDriverClient getClient(String deviceSerial) {
        Instance inst = instances.get(deviceSerial);
        return inst != null ? inst.client : null;
    }

    // ── APK installation ──────────────────────────────────────────────────────

    private static void ensureInstalled(String deviceSerial, String adbHost) throws Exception {
        String root = System.getProperty("user.dir");

        // The test APK instruments TARGET_PACKAGE — am instrument fails with
        // INSTRUMENTATION_FAILED if the app-under-test isn't installed too.
        installIfMissing(deviceSerial, adbHost, TARGET_PACKAGE,
                java.nio.file.Paths.get(root, "..", "forge-driver", "app", "build", "outputs",
                        "apk", "debug", "app-debug.apk"));

        installIfMissing(deviceSerial, adbHost, APK_PACKAGE,
                java.nio.file.Paths.get(root, "..", "forge-driver", "app", "build", "outputs",
                        "apk", "androidTest", "debug", "app-debug-androidTest.apk"));
    }

    private static void installIfMissing(String deviceSerial, String adbHost,
                                           String packageName, java.nio.file.Path apk) throws Exception {
        Process check = new ProcessBuilder(
                buildAdbCmd(deviceSerial, adbHost, "shell", "pm", "path", packageName))
                .redirectErrorStream(true).start();
        check.waitFor(10, TimeUnit.SECONDS);
        String out = new String(check.getInputStream().readAllBytes()).trim();
        if (out.startsWith("package:")) {
            System.out.println("[ForgeDriverManager] " + packageName + " already installed on " + deviceSerial);
            return;
        }

        if (!java.nio.file.Files.exists(apk)) {
            throw new RuntimeException(
                    "[ForgeDriverManager] " + packageName + " not installed and pre-built APK not found at "
                    + apk.toAbsolutePath() + ". Run: cd forge-driver && ./install.sh");
        }

        System.out.println("[ForgeDriverManager] Installing " + packageName + " on " + deviceSerial);
        new ProcessBuilder(buildAdbCmd(deviceSerial, adbHost,
                "shell", "settings", "put", "global", "package_verifier_enable", "0"))
                .start().waitFor(5, TimeUnit.SECONDS);
        Process install = new ProcessBuilder(
                buildAdbCmd(deviceSerial, adbHost, "install", "-r", "-t", apk.toAbsolutePath().toString()))
                .redirectErrorStream(true).start();
        install.waitFor(60, TimeUnit.SECONDS);
        String installOut = new String(install.getInputStream().readAllBytes()).trim();
        if (!installOut.contains("Success")) {
            throw new RuntimeException("[ForgeDriverManager] " + packageName + " install failed: " + installOut);
        }
        System.out.println("[ForgeDriverManager] ✅ " + packageName + " installed on " + deviceSerial);
    }

    // ── Process lookup ────────────────────────────────────────────────────────

    /**
     * Resolves the on-device PID of {@code packageName}, retrying briefly since the
     * process may not have spawned yet immediately after {@code am instrument} starts.
     */
    private static String findProcessPid(String serial, String adbHost, String packageName) {
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                Process p = new ProcessBuilder(
                        buildAdbCmd(serial, adbHost, "shell", "pidof", packageName))
                        .redirectErrorStream(true).start();
                p.waitFor(5, TimeUnit.SECONDS);
                String out = new String(p.getInputStream().readAllBytes()).trim();
                if (!out.isEmpty() && out.matches("\\d+(\\s+\\d+)*")) {
                    return out.split("\\s+")[0];
                }
            } catch (Exception ignored) {}
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        return null;
    }

    // ── ADB helpers ───────────────────────────────────────────────────────────

    private static void adb(String serial, String adbHost, String... args) throws Exception {
        Process p = new ProcessBuilder(buildAdbCmd(serial, adbHost, args))
                .redirectErrorStream(true).start();
        p.waitFor(10, TimeUnit.SECONDS);
    }

    private static String[] buildAdbCmd(String serial, String adbHost, String... args) {
        boolean remote = adbHost != null && !adbHost.isBlank();
        int prefixLen = remote ? 7 : 3;
        String[] cmd = new String[prefixLen + args.length];
        int i = 0;
        cmd[i++] = "adb";
        if (remote) {
            cmd[i++] = "-H"; cmd[i++] = adbHost;
            cmd[i++] = "-P"; cmd[i++] = String.valueOf(ADB_SERVER_PORT);
        }
        cmd[i++] = "-s"; cmd[i++] = serial;
        System.arraycopy(args, 0, cmd, i, args.length);
        return cmd;
    }
}
