package com.popclub.android.cloud;

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
                .getResourceAsStream("config/local.cloud.properties")) {
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
        // Thread-local wins (set per-thread from testng.xml in parallel runs)
        String local = THREAD_DEVICE_SERIAL.get();
        if (local != null) return local;
        // Fall back to global -D flag or properties file (single-device runs)
        String val = get("stf.device.serial");
        return (val != null && !val.isBlank()) ? val : null;
    }

    // Per-thread serial so parallel tests each target their own device.
    // System.setProperty would be a global write and cause both threads to fight over the same device.
    private static final ThreadLocal<String> THREAD_DEVICE_SERIAL = new ThreadLocal<>();

    /**
     * Called by TestRunnerTest to forward the testng.xml "deviceSerial" parameter.
     * Stored in a ThreadLocal so parallel tests don't overwrite each other's serial.
     */
    public static void setDeviceSerialFromTestNG(String serial) {
        if (serial != null && !serial.isBlank()) {
            THREAD_DEVICE_SERIAL.set(serial.trim());
            System.out.println("[CloudConfig] Device serial set for thread "
                    + Thread.currentThread().getId() + ": " + serial.trim());
        }
    }

    // ── Cloud Appium server ───────────────────────────────────────────────────

    /**
     * PIN used to unlock STF farm devices at session start.
     * Set appium.device.unlock.pin in local.cloud.properties.
     * Returns null when not set.
     */
    public static String getDeviceUnlockPin() {
        return get("appium.device.unlock.pin");
    }

    /**
     * Password used to unlock STF farm devices at session start.
     * Set appium.device.unlock.password in local.cloud.properties.
     * Returns null when not set.
     */
    public static String getDeviceUnlockPassword() {
        return get("appium.device.unlock.password");
    }

    /**
     * Comma-separated list of Appium ports running on the STF Mac
     * (started by appium_manager.py).  Set appium.remote.ports in
     * local.cloud.properties.  Returns null when not configured —
     * AppiumServerManager will fall back to the built-in defaults.
     */
    public static String getAppiumRemotePorts() {
        return get("appium.remote.ports");
    }

    /**
     * Port on which Appium is running on the STF server.
     * Set appium.remote.port in local.cloud.properties to override (default 4723).
     * Only used when cloud.enabled=true.
     */
    public static int getAppiumRemotePort() {
        String val = get("appium.remote.port");
        try {
            return (val != null && !val.isBlank()) ? Integer.parseInt(val.trim()) : 4723;
        } catch (NumberFormatException e) {
            return 4723;
        }
    }

    /**
     * SSH username for starting remote Appium on the STF server.
     * Set appium.remote.ssh.user in local.cloud.properties.
     * Returns null if not configured (remote Appium must be started manually).
     */
    public static String getAppiumSshUser() {
        return get("appium.remote.ssh.user");
    }

    /**
     * Path to the SSH private key for starting remote Appium.
     * Uses appium.remote.ssh.key if set; otherwise auto-detects the first key
     * found in ~/.ssh (id_rsa, id_ed25519, id_ecdsa).
     * Returns null if no key is found.
     */
    public static String getAppiumSshKey() {
        String val = get("appium.remote.ssh.key");
        if (val != null && !val.isBlank()) return val;

        String home = System.getProperty("user.home");
        for (String name : new String[]{"id_ed25519", "id_rsa", "id_ecdsa"}) {
            java.io.File key = new java.io.File(home + "/.ssh/" + name);
            if (key.exists()) {
                System.out.println("[CloudConfig] Auto-detected SSH key → " + key.getPath());
                return key.getPath();
            }
        }
        return null;
    }

    // ── Local Appium path settings ────────────────────────────────────────────

    /**
     * Path to the node executable.
     * Uses appium.node.path from properties if set; otherwise auto-detects via `which node`.
     */
    public static String getNodePath() {
        String val = get("appium.node.path");
        if (val != null && !val.isBlank()) return expandHome(val);
        return autoDetect("node", "appium.node.path");
    }

    /**
     * Path to appium's main.js entry point.
     * Uses appium.js.path from properties if set; otherwise auto-detects from
     * `npm root -g` or the appium binary location.
     */
    public static String getAppiumJsPath() {
        String val = get("appium.js.path");
        if (val != null && !val.isBlank()) return val;
        return autoDetectAppiumJs();
    }

    /**
     * Path to the Android SDK root (needed by Appium / UiAutomator2 to locate aapt2
     * and other build tools).  Falls back to the ANDROID_HOME environment variable,
     * then ANDROID_SDK_ROOT, so the property is optional when the env var is already set.
     */
    public static String getAndroidHome() {
        String val = get("android.home");
        if (val != null && !val.isBlank()) return expandHome(val);
        val = System.getenv("ANDROID_HOME");
        if (val != null && !val.isBlank()) return val;
        val = System.getenv("ANDROID_SDK_ROOT");
        if (val != null && !val.isBlank()) return val;
        return null; // let Appium try to auto-detect
    }

    private static String expandHome(String path) {
        if (path.startsWith("~/") || path.equals("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    // ── Auto-detection helpers ────────────────────────────────────────────────

    /** Runs `which <binary>` and returns the trimmed path, or throws with a helpful message. */
    private static String autoDetect(String binary, String propertyName) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"which", binary});
            String path = new String(p.getInputStream().readAllBytes()).trim();
            if (!path.isBlank()) {
                System.out.println("[CloudConfig] Auto-detected " + binary + " → " + path);
                return path;
            }
        } catch (Exception ignored) {}
        throw new RuntimeException(
            "[CloudConfig] Cannot find '" + binary + "' on PATH. " +
            "Set " + propertyName + " in src/main/resources/config/local.cloud.properties"
        );
    }

    /**
     * Locates appium's main.js by trying (in order):
     *  1. npm root -g  →  <npm-global>/appium/build/lib/main.js
     *  2. which appium →  <bin-prefix>/lib/node_modules/appium/build/lib/main.js
     */
    private static String autoDetectAppiumJs() {
        // Strategy 1: npm root -g
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"npm", "root", "-g"});
            String npmRoot = new String(p.getInputStream().readAllBytes()).trim();
            if (!npmRoot.isBlank()) {
                java.io.File candidate = new java.io.File(npmRoot + "/appium/build/lib/main.js");
                if (candidate.exists()) {
                    System.out.println("[CloudConfig] Auto-detected appium.js → " + candidate.getPath());
                    return candidate.getPath();
                }
            }
        } catch (Exception ignored) {}

        // Strategy 2: derive from `which appium`, following symlinks (handles Homebrew Cellar layout)
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"which", "appium"});
            String appiumBin = new String(p.getInputStream().readAllBytes()).trim();
            if (!appiumBin.isBlank()) {
                // Follow symlinks so Homebrew's bin/appium → Cellar/.../libexec/bin/appium
                java.nio.file.Path realBin = java.nio.file.Paths.get(appiumBin).toRealPath();
                // realBin = .../libexec/bin/appium  →  prefix = .../libexec
                String prefix = realBin.getParent().getParent().toString();
                for (String rel : new String[]{
                        "/lib/node_modules/appium/build/lib/main.js",
                        "/node_modules/appium/build/lib/main.js"}) {
                    java.io.File candidate = new java.io.File(prefix + rel);
                    if (candidate.exists()) {
                        System.out.println("[CloudConfig] Auto-detected appium.js → " + candidate.getPath());
                        return candidate.getPath();
                    }
                }
            }
        } catch (Exception ignored) {}

        throw new RuntimeException(
            "[CloudConfig] Cannot find Appium main.js. " +
            "Set appium.js.path in src/main/resources/config/local.cloud.properties\n" +
            "  Example: appium.js.path=/opt/homebrew/lib/node_modules/appium/build/lib/main.js"
        );
    }
}
