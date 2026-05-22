package com.popclub.mobile.cloud;

import java.io.InputStream;
import java.util.Properties;

/**
 * Reads cloud/STF configuration from:
 *  1. System properties  (highest priority)  -Dcloud.enabled=true
 *  2. cloud.properties   (on classpath)
 */
public class CloudConfig {

    private static final Properties props = new Properties();

    static {
        // Load shared defaults first
        try (InputStream is = CloudConfig.class.getClassLoader()
                .getResourceAsStream("cloud.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            System.out.println("[CloudConfig] cloud.properties not found, using system properties only.");
        }
        // local.cloud.properties (gitignored) overrides shared values for local dev machines
        try (InputStream is = CloudConfig.class.getClassLoader()
                .getResourceAsStream("local.cloud.properties")) {
            if (is != null) {
                props.load(is);
                System.out.println("[CloudConfig] Loaded local.cloud.properties");
            }
        } catch (Exception ignored) {}
    }

    private static String get(String key) {
        // System property wins over file
        String val = System.getProperty(key);
        if (val != null && !val.isBlank()) return val.trim();
        val = props.getProperty(key);
        return val != null ? val.trim() : null;
    }

    private static String require(String key) {
        String val = get(key);
        if (val == null || val.isBlank()) {
            throw new RuntimeException("[CloudConfig] Missing required property: " + key);
        }
        return val;
    }

    // ── Feature flag ──────────────────────────────────────────────────────────

    /** Returns true when cloud/STF mode is active. */
    public static boolean isCloudEnabled() {
        String val = get("cloud.enabled");
        return "true".equalsIgnoreCase(val);
    }

    // ── STF settings ──────────────────────────────────────────────────────────

    /**
     * Base URL of the STF/DeviceFarmer instance.
     * If stf.base.url is set in cloud.properties or via -D, that value is used.
     * Otherwise the IP is auto-detected from the en0 network interface (the Mac's WiFi/LAN IP),
     * which is the same interface that start.sh uses to configure the device farm.
     */
    public static String getStfBaseUrl() {
        String val = get("stf.base.url");
        if (val != null && !val.isBlank()) return val;
        return "https://" + detectLocalIp();
    }

    private static String detectLocalIp() {
        try {
            java.net.NetworkInterface en0 = java.net.NetworkInterface.getByName("en0");
            if (en0 != null) {
                java.util.Enumeration<java.net.InetAddress> addrs = en0.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        System.out.println("[CloudConfig] Auto-detected STF host: " + addr.getHostAddress());
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CloudConfig] Could not auto-detect IP from en0: " + e.getMessage());
        }
        throw new RuntimeException(
            "[CloudConfig] Could not detect device farm IP. Set stf.base.url in cloud.properties.");
    }

    /** Email used to auto-authenticate with the device farm (mock auth). */
    public static String getStfAuthEmail() {
        String val = get("stf.auth.email");
        return (val != null && !val.isBlank()) ? val : "testdevices@popclub.co";
    }

    /** Display name used to auto-authenticate with the device farm (mock auth). */
    public static String getStfAuthName() {
        String val = get("stf.auth.name");
        return (val != null && !val.isBlank()) ? val : "Forge";
    }

    /**
     * How long (ms) to reserve the device for.
     * Defaults to 10 minutes.
     */
    public static long getStfDeviceTimeout() {
        String val = get("stf.device.timeout.ms");
        try {
            return val != null ? Long.parseLong(val) : 600_000L;
        } catch (NumberFormatException e) {
            return 600_000L;
        }
    }

    /**
     * Optional: filter devices by platform version e.g. "11", "12".
     * If blank, any available Android device is used.
     */
    public static String getStfPlatformVersion() {
        String val = get("stf.platform.version");
        return (val != null && !val.isBlank()) ? val : null;
    }

    /**
     * Optional: target a specific device by serial/UDID.
     * Priority order:
     *   1. System property  -Dstf.device.serial=XXX
     *   2. cloud.properties stf.device.serial=XXX
     *   3. testng.xml       <parameter name="deviceSerial" value="XXX"/>
     *                       (set via CloudConfig.setDeviceSerialFromTestNG before tests run)
     * Returns null when not set — DeviceManager will auto-pick any free device.
     */
    public static String getDeviceSerial() {
        String val = get("stf.device.serial");
        return (val != null && !val.isBlank()) ? val : null;
    }

    /**
     * Called by TestRunnerTest to forward the testng.xml "deviceSerial" parameter.
     * Only takes effect if stf.device.serial is not already set via file/system-prop.
     */
    public static void setDeviceSerialFromTestNG(String serial) {
        if (serial != null && !serial.isBlank()) {
            // Only set if not already overridden by file or -D flag
            if (get("stf.device.serial") == null) {
                System.setProperty("stf.device.serial", serial.trim());
                System.out.println("[CloudConfig] Device serial set from testng.xml: " + serial.trim());
            }
        }
    }

    // ── Local Appium path settings ────────────────────────────────────────────

    /** Path to the node executable e.g. /usr/local/bin/node */
    public static String getNodePath() {
        return require("appium.node.path");
    }

    /** Path to appium main.js e.g. /usr/local/lib/node_modules/appium/build/lib/main.js */
    public static String getAppiumJsPath() {
        return require("appium.js.path");
    }

    /**
     * Path to the Android SDK root (needed by Appium / UiAutomator2 to locate aapt2
     * and other build tools).  Falls back to the ANDROID_HOME environment variable,
     * then ANDROID_SDK_ROOT, so the property is optional when the env var is already set.
     */
    public static String getAndroidHome() {
        // Explicit property wins
        String val = get("android.home");
        if (val != null && !val.isBlank()) return val;
        // Fall back to environment variables already set on the machine
        val = System.getenv("ANDROID_HOME");
        if (val != null && !val.isBlank()) return val;
        val = System.getenv("ANDROID_SDK_ROOT");
        if (val != null && !val.isBlank()) return val;
        return null; // let Appium try to auto-detect
    }
}
