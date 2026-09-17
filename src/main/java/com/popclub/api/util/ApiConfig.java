package com.popclub.api.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads API config from {ENV}.properties (local | qa | prod).
 * Set ENV via -DENV=qa at runtime; defaults to local.
 * Mirrors roast's Constants pattern.
 */
public class ApiConfig {

    private static final Logger     log   = LoggerFactory.getLogger(ApiConfig.class);
    private static final Properties props = load();

    private static Properties load() {
        String env  = System.getProperty("ENV", "local");
        String file = "config/" + env + ".properties";
        Properties p = new Properties();
        try (InputStream is = ApiConfig.class.getClassLoader().getResourceAsStream(file)) {
            if (is != null) {
                p.load(is);
                log.info("[ApiConfig] Loaded {}", file);
            } else {
                log.warn("[ApiConfig] {} not found — using defaults", file);
            }
        } catch (IOException e) {
            log.error("[ApiConfig] Failed to load {}", file, e);
        }
        return p;
    }

    public static String get(String key, String defaultValue) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys;
        return props.getProperty(key, defaultValue);
    }
}
