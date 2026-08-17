package com.popclub.android.driver;

import com.popclub.android.cloud.CloudConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AppiumServerManager {

    // Tracks local Appium processes started by this JVM: udid → Process
    private static final ConcurrentHashMap<String, Process> localProcesses = new ConcurrentHashMap<>();

    // Predefined Appium ports for cloud/STF parallel slots.
    // Configurable via appium.remote.ports in local.cloud.properties (comma-separated).
    private static final int[] CLOUD_APPIUM_PORTS = resolveCloudPorts();

    // Slot counter — each parallel session claims the next slot.
    private static final AtomicInteger slotCounter = new AtomicInteger(0);

    private static int[] resolveCloudPorts() {
        String val = CloudConfig.getAppiumRemotePorts();
        if (val != null && !val.isBlank()) {
            String[] parts = val.split(",");
            int[] ports = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                ports[i] = Integer.parseInt(parts[i].trim());
            }
            return ports;
        }
        return new int[]{4723, 4725, 4727, 4729, 4731};
    }

    /**
     * Ensures a local Appium server is running on {@code port} for {@code udid}.
     * If already running (from a previous test or a manually started server), reuses it.
     * Starts Appium via `appium --port <port> --relaxed-security` — no path configuration needed.
     */
    public static synchronized void startServer(String udid, int port) {
        // If we already started a process for this udid and it's still alive, reuse it.
        Process existing = localProcesses.get(udid);
        if (existing != null && existing.isAlive()) {
            System.out.println("[Appium] Server already managed on port " + port + " — reusing.");
            return;
        }

        // Kill any externally-started Appium on this port so we own the process and can stream its logs.
        // Only in local mode — never kill a cloud/STF Appium server.
        if (!CloudConfig.isCloudEnabled() && isLocalAppiumUp(port)) {
            System.out.println("[Appium] Found external server on port " + port + " — restarting to capture logs...");
            killPortProcess(port);
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }

        System.out.println("[Appium] Starting server on port " + port + "...");

        try {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add("appium");
            cmd.add("--port"); cmd.add(String.valueOf(port));
            cmd.add("--relaxed-security");
            cmd.add("--allow-cors");
            cmd.add("--log-level"); cmd.add("debug");

            String androidHome = CloudConfig.getAndroidHome();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            if (androidHome != null) {
                pb.environment().put("ANDROID_HOME", androidHome);
                pb.environment().put("ANDROID_SDK_ROOT", androidHome);
            }

            Process process = pb.start();

            // Stream Appium output to console on a daemon thread
            Thread logThread = new Thread(() -> {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[Appium] " + line);
                    }
                } catch (Exception ignored) {}
            }, "appium-log-" + port);
            logThread.setDaemon(true);
            logThread.start();
            localProcesses.put(udid, process);

            // Wait up to 30s for Appium to be ready
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                if (isLocalAppiumUp(port)) {
                    System.out.println("[Appium] Server ready on port " + port);
                    return;
                }
                if (!process.isAlive()) {
                    throw new RuntimeException("[Appium] Server process exited unexpectedly on port " + port);
                }
                Thread.sleep(500);
            }
            throw new RuntimeException("[Appium] Server did not become ready within 30s on port " + port
                + ". Is 'appium' on your PATH? Run: appium --version");

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[Appium] Failed to start server on port " + port + ": " + e.getMessage(), e);
        }
    }

    public static void stopServer(String udid) {
        Process process = localProcesses.remove(udid);
        if (process != null && process.isAlive()) {
            process.destroy();
            System.out.println("[Appium] Server stopped → " + udid);
        }
    }

    /** Stop ALL local servers (call at suite teardown). */
    public static void stopAll() {
        localProcesses.values().forEach(p -> { try { p.destroy(); } catch (Exception ignored) {} });
        localProcesses.clear();
        System.out.println("[Appium] All servers stopped");
    }

    /**
     * Claims the next available parallel slot and returns [appiumPort, systemPort].
     * Only called in cloud mode.
     */
    public static int[] nextCloudSlot() {
        int slot = slotCounter.getAndIncrement() % CLOUD_APPIUM_PORTS.length;
        int appiumPort = CLOUD_APPIUM_PORTS[slot];
        int systemPort = 18200 + java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 1000);
        System.out.println("[Appium] Cloud ports: " + java.util.Arrays.toString(CLOUD_APPIUM_PORTS));
        System.out.println("[Appium] Slot " + slot + " → port " + appiumPort + " | systemPort " + systemPort);
        return new int[]{appiumPort, systemPort};
    }

    /**
     * Verifies a remote Appium server is reachable on the STF host.
     * Appium must be started on the STF Mac before running cloud tests.
     */
    public static void ensureRemoteServer(String host, int port) {
        String url = "http://" + host + ":" + port + "/status";
        if (isAppiumUp(url)) {
            System.out.println("[Appium] Remote server ready at " + url);
            return;
        }
        throw new RuntimeException(
            "[Appium] Remote server not running at " + url + ".\n" +
            "Start it on the STF Mac (" + host + "):\n" +
            "  ANDROID_HOME=<sdk-path> nohup appium --port " + port +
            " --relaxed-security --allow-cors > ~/appium.log 2>&1 &\n" +
            "Then verify: curl " + url);
    }

    private static void killPortProcess(int port) {
        try {
            // -sTCP:LISTEN ensures we only kill the process listening on the port (the server),
            // not client processes (including this JVM) that have an open connection to it.
            Process lsof = Runtime.getRuntime().exec(new String[]{"lsof", "-ti", "tcp:" + port, "-sTCP:LISTEN"});
            String pids = new String(lsof.getInputStream().readAllBytes()).trim();
            if (!pids.isBlank()) {
                for (String pid : pids.split("\n")) {
                    Runtime.getRuntime().exec(new String[]{"kill", "-9", pid.trim()});
                }
                System.out.println("[Appium] Killed process on port " + port + " (pid: " + pids.replace("\n", ",") + ")");
            }
        } catch (Exception e) {
            System.out.println("[Appium] Could not kill process on port " + port + ": " + e.getMessage());
        }
    }

    private static boolean isLocalAppiumUp(int port) {
        return isAppiumUp("http://127.0.0.1:" + port + "/status");
    }

    private static boolean isAppiumUp(String statusUrl) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(statusUrl).openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
