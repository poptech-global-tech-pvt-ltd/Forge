package com.popclub.api.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads environment/infrastructure config (base URL, API key, auth mobile).
 * Test data (names, PAN, addresses, etc.) lives in testdata/valid_user.json → TestUser.
 */
public class ConfigManager {

    private static final Properties PROPS = new Properties();

    static {
        String env = System.getProperty("env", "sit");
        try (InputStream in = ConfigManager.class.getClassLoader()
                .getResourceAsStream("config/" + env + ".properties")) {
            if (in == null) throw new RuntimeException("Config file not found for env: " + env);
            PROPS.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    private ConfigManager() {}

    public static String getBaseUrl()        { return PROPS.getProperty("base.url"); }
    public static String getXSourceApiKey() { return PROPS.getProperty("x.source.api.key"); }
    public static String getMobileNumber()  { return PROPS.getProperty("mobile.number"); }
    public static String getFakePanMobile() { return PROPS.getProperty("fake.pan.mobile"); }
}
