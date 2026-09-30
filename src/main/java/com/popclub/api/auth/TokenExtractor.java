package com.popclub.api.auth;

import com.popclub.api.util.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TokenExtractor — retrieves the logged-in user's JWT from the device.
 *
 * Priority:
 *   1. USER_TOKEN already set in local.properties → use it directly
 *   2. OkHttp logcat — reads "jwt_access_token" from VERIFY_OTP response body.
 *      Primary post-login strategy; anchors to the VERIFY_OTP request marker.
 *   3. Local token cache (~/.forge/auth_token) — persisted after every successful
 *      capture. Works across runs when login is skipped (noReset:true, non-debuggable
 *      build, logcat buffer cleared). JWT expires in 120 days so this is stable.
 *   4. DataStore proto file (SpSharedPrefs.preferences_pb) — requires debuggable build.
 *   5. Legacy SharedPreferences — usually encrypted, rarely useful.
 *
 * Usage:
 *   String token = TokenExtractor.get("10BDCM0YJZ00043");
 *   // → "eyJhbGci..."  or null if not found
 */
public class TokenExtractor {

    private static final Logger log = LoggerFactory.getLogger(TokenExtractor.class);

    private static final String APP_PACKAGE = "com.popclub.android";

    /** Local file on the test machine where the last captured JWT is persisted.
     *  Survives across test runs — used when login is skipped (noReset:true) and
     *  the device build is non-debuggable (run-as blocked). */
    private static final Path TOKEN_CACHE_FILE =
            Paths.get(System.getProperty("user.home"), ".forge", "auth_token");

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

        System.out.println("[TokenExtractor] Trying logcat (device: " + (deviceSerial.isBlank() ? "default" : deviceSerial) + ")…");

        // 2. OkHttp logcat — anchored to VERIFY_OTP response body.
        String fromLogcat = fromLogcat(deviceSerial);
        if (fromLogcat != null) {
            System.out.println("[TokenExtractor] ✅ Token found in logcat");
            saveToCache(fromLogcat);
            return fromLogcat;
        }
        System.out.println("[TokenExtractor] Not in logcat — trying local cache…");

        // 3. Local cache file (~/.forge/auth_token) — persisted from the last successful login.
        //    Primary fallback when login is skipped (noReset:true) and build is non-debuggable.
        String fromCache = fromLocalCache();
        if (fromCache != null) {
            System.out.println("[TokenExtractor] ✅ Token found in local cache");
            return fromCache;
        }
        System.out.println("[TokenExtractor] Not in local cache — trying DataStore…");

        // 4. DataStore proto file — requires debuggable build.
        String fromDataStore = fromDataStore(deviceSerial);
        if (fromDataStore != null) {
            System.out.println("[TokenExtractor] ✅ Token found in DataStore");
            saveToCache(fromDataStore);
            return fromDataStore;
        }
        System.out.println("[TokenExtractor] Not in DataStore — trying SharedPrefs…");

        // 5. Legacy SharedPreferences (usually encrypted now — last resort)
        String fromPrefs = fromSharedPrefs(deviceSerial);
        if (fromPrefs != null) {
            System.out.println("[TokenExtractor] ✅ Token found in SharedPrefs");
            saveToCache(fromPrefs);
            return fromPrefs;
        }

        System.out.println("[TokenExtractor] ❌ No token found via any strategy");
        log.warn("[TokenExtractor] No token found — run loginIfNeeded before captureToken");
        return null;
    }

    public static String getLegacyToken(String deviceSerial) {
        String tokenA = grepLogcatLegacy(deviceSerial,
                "logcat -d okhttp.OkHttpClient:I *:S | grep '\"token\"'");
        if (tokenA != null) return tokenA;
        return grepLogcatLegacy(deviceSerial,
                "logcat -d | grep '\"token\"'");
    }

    private static String grepLogcatLegacy(String deviceSerial, String shellCmd) {
        try {
            String[] cmd = buildAdb(deviceSerial, "shell", shellCmd);
            String output = run(cmd);
            if (output == null || output.isBlank()) return null;
            Pattern p = Pattern.compile("\"token\"\\s*:\\s*\"([a-f0-9]{40,})\"");
            Matcher m = p.matcher(output);
            String last = null;
            while (m.find()) last = m.group(1);
            if (last != null) System.out.println("[TokenExtractor] ✅ Legacy DRF token found in logcat");
            return last;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Local cache (strategy 3) ──────────────────────────────────────────────
    // Persists the JWT to ~/.forge/auth_token after every successful capture.
    // Read back on subsequent runs when login is skipped and device storage is
    // inaccessible (non-debuggable release/UAT build, run-as blocked).

    /**
     * Persists the token to the local cache file so future runs can reuse it.
     * Called automatically whenever any strategy successfully returns a token.
     */
    public static void clearCache() {
        try {
            if (Files.deleteIfExists(TOKEN_CACHE_FILE)) {
                System.out.println("[TokenExtractor] Cleared cached token: " + TOKEN_CACHE_FILE);
            }
        } catch (Exception e) {
            System.out.println("[TokenExtractor] Cache clear failed: " + e.getMessage());
        }
    }

    public static void saveToCache(String token) {
        if (token == null || token.isBlank()) return;
        try {
            Files.createDirectories(TOKEN_CACHE_FILE.getParent());
            Files.writeString(TOKEN_CACHE_FILE, token, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("[TokenExtractor] Token persisted to local cache: " + TOKEN_CACHE_FILE);
        } catch (Exception e) {
            System.out.println("[TokenExtractor] Local cache write failed: " + e.getMessage());
        }
    }

    private static String fromLocalCache() {
        try {
            if (!Files.exists(TOKEN_CACHE_FILE)) {
                System.out.println("[TokenExtractor] Local cache not found: " + TOKEN_CACHE_FILE);
                return null;
            }
            String token = Files.readString(TOKEN_CACHE_FILE).trim();
            if (token.startsWith("eyJ")) {
                System.out.println("[TokenExtractor] Local cache hit: " + TOKEN_CACHE_FILE);
                return token;
            }
            System.out.println("[TokenExtractor] Local cache exists but content is not a valid JWT");
        } catch (Exception e) {
            System.out.println("[TokenExtractor] Local cache read failed: " + e.getMessage());
        }
        return null;
    }

    // ── Strategy 2: DataStore proto file ────────────────────────────────────
    // The app stores user data (including jwt_access_token) in a proto DataStore
    // at files/datastore/SpSharedPrefs.preferences_pb as a JSON-encoded string.
    // This is readable as plain text — no decryption needed.

    private static String fromDataStore(String deviceSerial) {
        try {
            String path = "/data/data/" + APP_PACKAGE + "/files/datastore/SpSharedPrefs.preferences_pb";
            String[] cmd = buildAdb(deviceSerial, "run-as", APP_PACKAGE, "cat", path);
            System.out.println("[TokenExtractor] DataStore cmd: " + java.util.Arrays.toString(cmd));
            String content = run(cmd);

            if (content == null || content.isBlank()) {
                System.out.println("[TokenExtractor] DataStore: empty output (run-as may require debuggable build)");
                return null;
            }

            // The proto file contains a JSON blob with jwt_access_token
            Pattern p = Pattern.compile("\"jwt_access_token\"\\s*:\\s*\"(eyJ[A-Za-z0-9._-]+)\"");
            Matcher m = p.matcher(content);
            if (m.find()) {
                log.info("[TokenExtractor] Got token from DataStore SpSharedPrefs");
                return m.group(1);
            }
            System.out.println("[TokenExtractor] DataStore: file read OK but jwt_access_token not found "
                    + "(content length=" + content.length() + ")");
        } catch (Exception e) {
            System.out.println("[TokenExtractor] DataStore extraction failed: " + e.getMessage());
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

    // ── Strategy 2: Logcat ────────────────────────────────────────────────────────
    // Tag is confirmed as "okhttp.OkHttpClient" (I priority).
    //
    // Root cause of previous failures: the app sends massive talsec analytics batches
    // (10KB+ each, every ~5 min) that overflow the logcat ring buffer and push the
    // VERIFY_OTP response out of any fixed -t N window.
    //
    // Fix: run grep on the DEVICE via `adb shell "logcat -d ... | grep jwt_access_token"`
    // so only the matching lines are returned — no line limit needed, no overflow risk.
    //
    // Two passes:
    //   A. okhttp.OkHttpClient:I tag filter + grep  (fast, targeted)
    //   B. Full logcat + grep                       (catches any other logger)

    private static String fromLogcat(String deviceSerial) {
        // ── Pass A: okhttp tag filter + on-device grep ────────────────────────
        String tokenA = grepLogcat(deviceSerial,
                "logcat -d okhttp.OkHttpClient:I *:S | grep jwt_access_token");
        if (tokenA != null) {
            System.out.println("[TokenExtractor] ✅ Token found via logcat okhttp grep");
            return tokenA;
        }
        System.out.println("[TokenExtractor] okhttp grep empty — trying full logcat grep…");

        // ── Pass B: full logcat + on-device grep ──────────────────────────────
        String tokenB = grepLogcat(deviceSerial,
                "logcat -d | grep jwt_access_token");
        if (tokenB != null) {
            System.out.println("[TokenExtractor] ✅ Token found via full logcat grep");
        }
        return tokenB;
    }

    /**
     * Runs `adb shell "<shellCmd>"` on the device and extracts the last
     * jwt_access_token value from the output.
     *
     * The shell command is executed on-device so pipes work and only matching
     * lines travel over the adb connection — no line-count limits needed.
     */
    private static String grepLogcat(String deviceSerial, String shellCmd) {
        try {
            // adb [-s serial] shell "logcat -d ... | grep jwt_access_token"
            String[] cmd = buildAdb(deviceSerial, "shell", shellCmd);
            System.out.println("[TokenExtractor] logcat cmd: " + java.util.Arrays.toString(cmd));
            String output = run(cmd);

            if (output == null || output.isBlank()) return null;

            // Take the LAST occurrence — most recent login
            Pattern p = Pattern.compile("\"jwt_access_token\"\\s*:\\s*\"(eyJ[A-Za-z0-9._-]+)\"");
            Matcher m = p.matcher(output);
            String last = null;
            while (m.find()) last = m.group(1);
            return last;

        } catch (Exception e) {
            System.out.println("[TokenExtractor] logcat grep failed: " + e.getMessage());
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
