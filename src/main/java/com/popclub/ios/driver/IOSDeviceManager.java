package com.popclub.ios.driver;

import com.popclub.android.driver.DeviceInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages iOS device/simulator discovery and allocation.
 *
 * Physical devices: discovered via `xcrun xctrace list devices`
 * Simulators:       discovered via `xcrun simctl list devices booted`
 *
 * Usage is identical to the Android DeviceManager — call getDevice() to
 * claim a device and release(udid) to return it to the pool.
 */
public class IOSDeviceManager {

    private static final ConcurrentHashMap<String, Boolean> pool = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    public static synchronized DeviceInfo getDevice() {
        if (!initialized) {
            initialize();
        }
        for (String udid : pool.keySet()) {
            if (pool.replace(udid, false, true)) {
                DeviceInfo info = new DeviceInfo(udid, IOSAppiumServerManager.nextPort());
                info.platformName = "iOS";
                System.out.println("[IOSDevice] Claimed device: " + udid);
                return info;
            }
        }
        throw new RuntimeException("[IOSDevice] No available iOS devices/simulators. " +
                "Connect a device or boot a simulator: xcrun simctl boot <UDID>");
    }

    public static void release(String udid) {
        pool.replace(udid, true, false);
        System.out.println("[IOSDevice] Released device: " + udid);
    }

    private static void initialize() {
        List<String> udids = new ArrayList<>();
        udids.addAll(discoverPhysicalDevices());
        udids.addAll(discoverBootedSimulators());

        if (udids.isEmpty()) {
            throw new RuntimeException("[IOSDevice] No iOS devices or booted simulators found. " +
                    "Connect a device or run: xcrun simctl boot <simulator-udid>");
        }

        for (String udid : udids) {
            pool.put(udid, false);
        }

        System.out.println("[IOSDevice] Device pool initialized: " + udids);
        initialized = true;
    }

    private static List<String> discoverPhysicalDevices() {
        List<String> udids = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"xcrun", "xctrace", "list", "devices"});
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // Physical device lines end with a UUID in parentheses, e.g.:
                    // iPhone 14 Pro (16.4) (00008110-000A5C120E82401E)
                    if (line.matches(".*\\([0-9A-Fa-f-]{36}\\).*") && !line.contains("Simulator")) {
                        String udid = line.replaceAll(".*\\(([0-9A-Fa-f-]{36})\\).*", "$1").trim();
                        udids.add(udid);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[IOSDevice] Could not discover physical devices: " + e.getMessage());
        }
        return udids;
    }

    private static List<String> discoverBootedSimulators() {
        List<String> udids = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"xcrun", "simctl", "list", "devices", "booted"});
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // Booted simulator lines: "    iPhone 15 (UDID) (Booted)"
                    if (line.contains("Booted")) {
                        String udid = line.replaceAll(".*\\(([0-9A-Fa-f-]{8}-[0-9A-Fa-f-]{4}-[0-9A-Fa-f-]{4}-[0-9A-Fa-f-]{4}-[0-9A-Fa-f-]{12})\\).*", "$1").trim();
                        if (!udid.equals(line)) {
                            udids.add(udid);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[IOSDevice] Could not discover simulators: " + e.getMessage());
        }
        return udids;
    }
}
