package com.popclub.api.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads environment/infrastructure config (base URL, API key, auth mobile).
 * Test data (names, PAN, addresses, etc.) lives in testdata/valid_user.json → TestUser.
 *
 * Set ENV via -DENV=stage|prod at runtime; defaults to local — same convention as ApiConfig.
 */
public class ConfigManager {

    private static final Properties PROPS = new Properties();

    static {
        String env = System.getProperty("ENV", "local");
        try (InputStream in = ConfigManager.class.getClassLoader()
                .getResourceAsStream("config/" + env + ".properties")) {
            if (in == null) throw new RuntimeException("Config file not found for env: " + env);
            PROPS.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    private ConfigManager() {}

    /** -D system property > env var (k8s Secret in CI) > properties file. */
    private static String get(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys;
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        return PROPS.getProperty(key);
    }

    public static String getBaseUrl()        { return get("base.url"); }
    public static String getXSourceApiKey() { return get("x.source.api.key"); }
    public static String getMobileNumber()  { return get("mobile.number"); }
    public static String getFakePanMobile() { return get("fake.pan.mobile"); }
}
