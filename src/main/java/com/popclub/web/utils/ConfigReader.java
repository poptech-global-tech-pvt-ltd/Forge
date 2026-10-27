package com.popclub.web.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {

    private static final Logger log = LoggerFactory.getLogger(ConfigReader.class);

    private static Properties props;

    private static Properties getProps() {
        if (props != null) return props;
        props = new Properties();
        try (InputStream is = ConfigReader.class.getClassLoader()
                .getResourceAsStream("config/web-config.properties")) {
            if (is != null) {
                props.load(is);
                log.info("[ConfigReader] Loaded web-config.properties");
            } else {
                log.info("[ConfigReader] web-config.properties not found on classpath; using defaults");
            }
        } catch (IOException e) {
            log.error("[ConfigReader] Failed to load web-config.properties", e);
        }
        return props;
    }

    public static String get(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys;
        return getProps().getProperty(key, defaultFor(key));
    }

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public static int getInt(String key) {
        try { return Integer.parseInt(get(key)); }
        catch (NumberFormatException e) {
            log.error("[ConfigReader] Invalid integer value for key '{}'; using default", key, e);
            return Integer.parseInt(defaultFor(key));
        }
    }

    private static String defaultFor(String key) {
        return switch (key) {
            case "browser"  -> "chromium";
            case "headless" -> "false";
            case "timeout"  -> "30000";
            case "base.url" -> "https://popcard-reskin-sit.popclub.co.in";
            default         -> "";
        };
    }
}
