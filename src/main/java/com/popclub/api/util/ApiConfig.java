package com.popclub.api.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads API config from {ENV}.properties (local | stage | prod).
 * Set ENV via -DENV=stage at runtime; defaults to local.
 * Mirrors roast's Constants pattern.
 *
 * Secrets resolve in this order: -D system property > env var (k8s Secret in
 * CI) > the properties file. This lets stage/prod CI runs inject real tokens
 * via env vars instead of committing them to the properties file.
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
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        return props.getProperty(key, defaultValue);
    }
}
