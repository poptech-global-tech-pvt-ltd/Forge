package com.popclub.mobile.driver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DeviceManager {

    private static List<String> devices = new ArrayList<>();
    private static Map<String, Integer> devicePortMap = new HashMap<>();
    private static Set<String> busyDevices = new HashSet<>();

    private static AtomicInteger portCounter = new AtomicInteger(4723);

    static {
        loadDevices();
    }

    // 🔥 Load devices from ADB
    private static void loadDevices() {

        try {

            Process process = Runtime.getRuntime().exec("adb devices");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.endsWith("device") && !line.startsWith("List")) {

                    String udid = line.split("\\s+")[0];

                    devices.add(udid);

                    //  Assign unique port
                    devicePortMap.put(udid, portCounter.getAndAdd(2));
                }
            }

            System.out.println("📱 Detected devices: " + devices);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load devices from ADB", e);
        }

        if (devices.isEmpty()) {
            throw new RuntimeException(" No devices connected!");
        }
    }

    // 🔥 Allocate free device
    public static synchronized DeviceInfo getDevice() {

        for (String udid : devices) {

            if (!busyDevices.contains(udid)) {

                busyDevices.add(udid);

                int port = devicePortMap.get(udid);

                System.out.println("Allocated device → " + udid + " | port: " + port);

                return new DeviceInfo(udid, port);
            }
        }

        throw new RuntimeException(" No free devices available");
    }

    // 🔥 Release device after test
    public static synchronized void release(String udid) {
        busyDevices.remove(udid);
        System.out.println(" Released device → " + udid);
    }
}