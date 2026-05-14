package com.popclub.mobile.driver;

import com.popclub.mobile.cloud.CloudConfig;
import com.popclub.mobile.cloud.STFClient;

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
                CloudConfig.getStfApiToken()
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

        // 2. Reserve it (best-effort — some STF builds have a buggy reservation API
        //    but still allow remote-connect, so we warn and continue on failure)
        try {
            stf.reserveDevice(serial, CloudConfig.getStfDeviceTimeout());
        } catch (Exception e) {
            System.out.println("[STF] Warning: reservation API failed (" + e.getMessage()
                    + ") — attempting remote connect anyway");
        }
        busyDevices.add(serial);

        // 3. Get the ADB-over-TCP address and connect
        String remoteConnectUrl = stf.getRemoteConnectUrl(serial);
        adbConnect(remoteConnectUrl);
        cloudRemoteUrls.put(serial, remoteConnectUrl);

        // 4. Fetch platform info (platform name + version)
        String[] platformInfo = stf.getDevicePlatformInfo(serial);

        // 5. Assign a local Appium port
        int port = portCounter.getAndAdd(2);
        devicePortMap.put(serial, port);

        System.out.println("[STF] Cloud device ready → " + serial
                + " | Platform: " + platformInfo[0] + " " + platformInfo[1]
                + " | ADB: " + remoteConnectUrl + " | Appium port: " + port);

        return new DeviceInfo(serial, port, platformInfo[0], platformInfo[1]);
    }

    private static void releaseCloudDevice(String serial) {
        // ADB disconnect
        String remoteUrl = cloudRemoteUrls.remove(serial);
        if (remoteUrl != null) {
            adbDisconnect(remoteUrl);
        }

        // Release reservation on STF
        STFClient stf = new STFClient(
                CloudConfig.getStfBaseUrl(),
                CloudConfig.getStfApiToken()
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

            System.out.println("📱 Detected local devices: " + localDevices);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load devices from ADB", e);
        }

        if (localDevices.isEmpty()) {
            throw new RuntimeException("No local devices connected!");
        }
    }

    private static DeviceInfo getLocalDevice() {
        for (String udid : localDevices) {
            if (!busyDevices.contains(udid)) {
                busyDevices.add(udid);
                int port = devicePortMap.get(udid);
                System.out.println("Allocated device → " + udid + " | port: " + port);
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
