package com.popclub.android.driver;

import com.popclub.android.cloud.CloudConfig;
import com.popclub.android.cloud.STFClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DeviceManager {

    // ── Local-mode state ──────────────────────────────────────────────────────
    private static List<String> localDevices    = new ArrayList<>();
    private static Map<String, Integer> devicePortMap = new HashMap<>();
    private static Set<String> busyDevices      = new HashSet<>();
    private static AtomicInteger portCounter    = new AtomicInteger(4723);

    // ── Cloud-mode state ──────────────────────────────────────────────────────
    /** Maps serial → ADB-over-TCP address so we can disconnect on release */
    private static Map<String, String> cloudRemoteUrls = new HashMap<>();

    static {
        if (!CloudConfig.isCloudEnabled()) {
            loadLocalDevices();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Allocates a device for the current test thread.
     *
     * • Cloud mode  → reserves a device via STF, ADB-connects it, returns its info.
     * • Local mode  → picks a free ADB-connected device as before.
     */
    public static synchronized DeviceInfo getDevice() {
        if (CloudConfig.isCloudEnabled()) {
            return getCloudDevice();
        } else {
            return getLocalDevice();
        }
    }

    /**
     * Releases a device after the test.
     *
     * • Cloud mode  → ADB-disconnects and releases back to STF.
     * • Local mode  → marks the device as free in the local pool.
     */
    public static synchronized void release(String udid) {
        if (CloudConfig.isCloudEnabled()) {
            releaseCloudDevice(udid);
        } else {
            busyDevices.remove(udid);
            System.out.println("Released device → " + udid);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLOUD MODE
    // ─────────────────────────────────────────────────────────────────────────

    private static DeviceInfo getCloudDevice() {
        STFClient stf = new STFClient(
                CloudConfig.getStfBaseUrl(),
                CloudConfig.getStfAuthEmail(),
                CloudConfig.getStfAuthName()
        );

        // 1. Find an available device
        String preferredSerial = CloudConfig.getDeviceSerial();
        String serial;

        if (preferredSerial != null) {
            // ── Specific device requested ──────────────────────────────────
            if (busyDevices.contains(preferredSerial)) {
                throw new RuntimeException(
                        "[STF] Requested device is already in use: " + preferredSerial);
            }
            serial = preferredSerial;
            System.out.println("[STF] Using specified device: " + serial);
        } else {
            // ── Auto-pick first available device ───────────────────────────
            List<String> available = stf.getAvailableDevices(CloudConfig.getStfPlatformVersion());
            if (available.isEmpty()) {
                throw new RuntimeException("[STF] No available Android devices on the farm");
            }

            serial = null;
            for (String s : available) {
                if (!busyDevices.contains(s)) {
                    serial = s;
                    break;
                }
            }
            if (serial == null) {
                throw new RuntimeException("[STF] No free devices available (all busy)");
            }
        }

        // 2. Release any stale reservation first, then reserve fresh
        stf.releaseDevice(serial); // no-op if not owned; clears stale holds from crashed runs
        try {
            stf.reserveDevice(serial, CloudConfig.getStfDeviceTimeout());
        } catch (Exception e) {
            throw new RuntimeException("[STF] Failed to reserve device " + serial
                    + ": " + e.getMessage(), e);
        }
        busyDevices.add(serial);

        // 3. Extract the STF server host from the base URL (e.g. "10.25.11.224" from "https://10.25.11.224")
        String stfHost = CloudConfig.getStfBaseUrl()
                .replaceFirst("https?://", "")
                .replaceAll("/.*", "")
                .replaceAll(":\\d+$", "");

        // 4. Fetch platform info (platform name + version)
        String[] platformInfo = stf.getDevicePlatformInfo(serial);

        // 5. Assign a local Appium port
        int port = portCounter.getAndAdd(2);
        devicePortMap.put(serial, port);

        System.out.println("[STF] Cloud device ready → " + serial
                + " | Platform: " + platformInfo[0] + " " + platformInfo[1]
                + " | ADB server: " + stfHost + ":5037 | Appium port: " + port);

        DeviceInfo info = new DeviceInfo(serial, port, platformInfo[0], platformInfo[1]);
        info.adbHost       = stfHost;
        info.adbServerPort = 5037;
        return info;
    }

    private static void releaseCloudDevice(String serial) {
        cloudRemoteUrls.remove(serial); // kept for backward compat; no TCP disconnect needed

        // Release reservation on STF
        STFClient stf = new STFClient(
                CloudConfig.getStfBaseUrl(),
                CloudConfig.getStfAuthEmail(),
                CloudConfig.getStfAuthName()
        );
        stf.releaseDevice(serial);

        busyDevices.remove(serial);
        System.out.println("[STF] Released cloud device → " + serial);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOCAL MODE  (unchanged logic)
    // ─────────────────────────────────────────────────────────────────────────

    private static void loadLocalDevices() {
        try {
            Process process = Runtime.getRuntime().exec("adb devices");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.endsWith("device") && !line.startsWith("List")) {
                    String udid = line.split("\\s+")[0];
                    localDevices.add(udid);
                    devicePortMap.put(udid, portCounter.getAndAdd(2));
                }
            }

            System.out.println("[Device] Local devices: " + localDevices);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load devices from ADB", e);
        }

        if (localDevices.isEmpty()) {
            throw new RuntimeException("No local devices connected!");
        }
    }

    private static DeviceInfo getLocalDevice() {
        String preferredSerial = CloudConfig.getDeviceSerial();
        if (preferredSerial != null && !preferredSerial.isBlank()) {
            if (busyDevices.contains(preferredSerial)) {
                throw new RuntimeException("Requested device is already in use: " + preferredSerial);
            }
            if (!localDevices.contains(preferredSerial)) {
                throw new RuntimeException("Requested device not found in ADB: " + preferredSerial);
            }
            busyDevices.add(preferredSerial);
            int port = devicePortMap.get(preferredSerial);
            System.out.println("[Device] Allocated (pinned) → " + preferredSerial);
            return new DeviceInfo(preferredSerial, port);
        }
        for (String udid : localDevices) {
            if (!busyDevices.contains(udid)) {
                busyDevices.add(udid);
                int port = devicePortMap.get(udid);
                System.out.println("[Device] Allocated → " + udid);
                return new DeviceInfo(udid, port);
            }
        }
        throw new RuntimeException("No free local devices available");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADB helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static void adbConnect(String address) {
        runAdb("adb connect " + address,
                "[ADB] Connected to " + address);
    }

    /** Re-connects to a cloud device after Appium's internal adb-server restart drops TCP links. */
    public static void adbReconnect(String address) {
        runAdb("adb connect " + address,
                "[ADB] Reconnected to " + address);
    }

    private static void adbDisconnect(String address) {
        runAdb("adb disconnect " + address,
                "[ADB] Disconnected from " + address);
    }

    private static void runAdb(String command, String successMessage) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            int exit = p.waitFor();
            System.out.println(successMessage + " → " + output.toString().trim()
                    + " (exit: " + exit + ")");

        } catch (Exception e) {
            throw new RuntimeException("ADB command failed: " + command, e);
        }
    }
}
