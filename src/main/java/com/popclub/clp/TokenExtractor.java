package com.popclub.clp;

import com.popclub.api.util.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TokenExtractor — retrieves the logged-in user's JWT from the device.
 *
 * Priority:
 *   1. USER_TOKEN already set in local.properties → use it directly
 *   2. Extract from SharedPreferences on device (app must be installed + logged in)
 *   3. Extract from recent OkHttp logcat (app must have made a request recently)
 *
 * Usage:
 *   String token = TokenExtractor.get("10BDCM0YJZ00043");
 *   // → "eyJhbGci..."  or null if not found
 */
public class TokenExtractor {

    private static final Logger log = LoggerFactory.getLogger(TokenExtractor.class);

    private static final String APP_PACKAGE = "com.popclub.android";

    // SharedPreferences key patterns to try (adjust if key name changes)
    private static final String[] PREFS_KEYS = {
            "access_token", "accessToken", "jwt_token", "jwtToken",
            "user_token", "userToken", "pop_access_token"
    };

    /**
     * Returns a valid JWT for CLP API calls, or null if none found.
     * Automatically picks the fastest available method.
     */
    public static String get(String deviceSerial) {

        // 1. Already configured in local.properties
        if (!ApiConstants.USER_TOKEN.isBlank()) {
            log.info("[TokenExtractor] Using USER_TOKEN from properties");
            return ApiConstants.USER_TOKEN;
        }

        // 2. DataStore proto file (fastest — plain-text JWT in SpSharedPrefs.preferences_pb)
        String fromDataStore = fromDataStore(deviceSerial);
        if (fromDataStore != null) return fromDataStore;

        // 3. Legacy SharedPreferences (usually encrypted now — kept as fallback)
        String fromPrefs = fromSharedPrefs(deviceSerial);
        if (fromPrefs != null) return fromPrefs;

        // 4. Logcat (needs recent OkHttp activity)
        String fromLogcat = fromLogcat(deviceSerial);
        if (fromLogcat != null) return fromLogcat;

        log.warn("[TokenExtractor] No token found — run loginIfNeeded before captureToken");
        return null;
    }

    // ── Strategy 2: DataStore proto file ────────────────────────────────────
    // The app stores user data (including jwt_access_token) in a proto DataStore
    // at files/datastore/SpSharedPrefs.preferences_pb as a JSON-encoded string.
    // This is readable as plain text — no decryption needed.

    private static String fromDataStore(String deviceSerial) {
        try {
            String path = "/data/data/" + APP_PACKAGE + "/files/datastore/SpSharedPrefs.preferences_pb";
            String content = run(buildAdb(deviceSerial,
                    "run-as", APP_PACKAGE, "cat", path));

            // The proto file contains a JSON blob with jwt_access_token
            Pattern p = Pattern.compile("\"jwt_access_token\"\\s*:\\s*\"(eyJ[A-Za-z0-9._-]+)\"");
            Matcher m = p.matcher(content);
            if (m.find()) {
                log.info("[TokenExtractor] Got token from DataStore SpSharedPrefs");
                return m.group(1);
            }
        } catch (Exception e) {
            log.debug("[TokenExtractor] DataStore extraction failed: {}", e.getMessage());
        }
        return null;
    }

    // ── Strategy 3 (legacy): SharedPreferences ────────────────────────────────
    // Note: the app now uses EncryptedSharedPreferences so this rarely finds anything.

    private static String fromSharedPrefs(String deviceSerial) {
        try {
            String[] listCmd = buildAdb(deviceSerial,
                    "run-as", APP_PACKAGE,
                    "ls", "/data/data/" + APP_PACKAGE + "/shared_prefs/");
            String files = run(listCmd);

            for (String file : files.split("\\s+")) {
                if (file.isBlank()) continue;
                String path = "/data/data/" + APP_PACKAGE + "/shared_prefs/" + file.trim();
                String content = run(buildAdb(deviceSerial,
                        "run-as", APP_PACKAGE, "cat", path));

                String token = extractFromXml(content);
                if (token != null) return token;
            }
        } catch (Exception e) {
            log.debug("[TokenExtractor] SharedPrefs extraction failed: {}", e.getMessage());
        }
        return null;
    }

    private static String extractFromXml(String xml) {
        // Matches: <string name="access_token">eyJ...</string>
        Pattern p = Pattern.compile(
                "<string\\s+name=\"(?:access_token|accessToken|jwt_token|jwtToken"
                + "|user_token|userToken|pop_access_token)\"[^>]*>([^<]+)</string>",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(xml);
        while (m.find()) {
            String val = m.group(1).trim();
            if (val.startsWith("eyJ")) return val; // JWT starts with eyJ
        }

        // Also check JSON-encoded values: "access_token":"eyJ..."
        Pattern p2 = Pattern.compile("\"(?:access_token|accessToken)\"\\s*:\\s*\"(eyJ[^\"]+)\"");
        Matcher m2 = p2.matcher(xml);
        if (m2.find()) return m2.group(1);

        return null;
    }

    // ── Strategy 3: Logcat ────────────────────────────────────────────────────

    private static String fromLogcat(String deviceSerial) {
        try {
            // Flush logcat then grep for the header the app sends
            String output = run(buildAdb(deviceSerial,
                    "logcat", "-d", "-t", "500", "OkHttp:V", "*:S"));

            // Match: X-Access-Token: Bearer eyJ...
            Pattern p = Pattern.compile("X-Access-Token:\\s*Bearer\\s+(eyJ[A-Za-z0-9._-]+)");
            Matcher m = p.matcher(output);
            String last = null;
            while (m.find()) last = m.group(1); // take most recent
            return last;
        } catch (Exception e) {
            log.debug("[TokenExtractor] Logcat extraction failed: {}", e.getMessage());
        }
        return null;
    }

    // ── ADB helpers ───────────────────────────────────────────────────────────

    private static String[] buildAdb(String deviceSerial, String... args) {
        String[] base = deviceSerial != null && !deviceSerial.isBlank()
                ? new String[]{"adb", "-s", deviceSerial}
                : new String[]{"adb"};
        String[] cmd = new String[base.length + args.length];
        System.arraycopy(base, 0, cmd, 0, base.length);
        System.arraycopy(args, 0, cmd, base.length, args.length);
        return cmd;
    }

    private static String run(String[] cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(cmd);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }
}
