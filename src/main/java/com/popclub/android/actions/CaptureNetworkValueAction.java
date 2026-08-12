package com.popclub.android.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.core.TestContext;
import com.popclub.model.Step;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * captureNetworkValue — waits for the device to make a network request whose URL
 * contains {@code step.value}, then extracts fields from the JSON response body
 * and stores them as TestContext variables.
 *
 * How it works:
 *   Reads Android logcat via `adb -s <udid> logcat -d *:D` (DEBUG level).
 *   Debug (qaDebug) builds log all HTTP traffic via OkHttp's HttpLoggingInterceptor:
 *
 *     D/OkHttp: <-- 200 https://...user-login/initiate (543ms)
 *     D/OkHttp: {"data":{"request_id":"abc123"},...}
 *     D/OkHttp: <-- END HTTP (77-byte body)
 *
 *   NOTE: Appium's built-in logcat API only returns INFO+ entries and silently drops
 *   DEBUG-level OkHttp logs — hence adb is used directly here.
 *
 * YAML syntax:
 *
 *   - action: captureNetworkValue
 *     value: "user-login/initiate"          # URL substring to match
 *     extract:
 *       smv_request_id: "data.request_id"   # varName: dot-notation JSON path
 *     timeout: 15                           # seconds (default 15)
 *
 * Captured variables are available as ${smv_request_id} in later steps.
 */
public class CaptureNetworkValueAction implements Action {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void perform(Step step) {
        String urlPattern = step.value;
        if (urlPattern == null || urlPattern.isBlank()) {
            throw new RuntimeException("captureNetworkValue: 'value' (URL pattern to match) is required");
        }

        int timeout = step.timeout > 0 ? step.timeout : 15;
        System.out.println("[captureNetworkValue] waiting up to " + timeout + "s for: " + urlPattern);

        String udid = AppiumDriverManager.deviceInfo.get() != null
            ? AppiumDriverManager.deviceInfo.get().udid
            : null;

        long deadline = System.currentTimeMillis() + (long) timeout * 1000;
        StringBuilder allLogs = new StringBuilder();
        long lastProgressAt = System.currentTimeMillis();
        boolean responseLinesDumped = false;

        while (System.currentTimeMillis() < deadline) {
            try {
                String fresh = readLogcatViaAdb(udid);
                allLogs.append(fresh);

                long okHttpLines = fresh.lines().filter(l -> l.contains("OkHttp")).count();
                long elapsed = (System.currentTimeMillis() - (deadline - (long) timeout * 1000)) / 1000;

                // On first batch with OkHttp lines, dump all response URL lines so we can verify the pattern
                if (!responseLinesDumped && okHttpLines > 0) {
                    responseLinesDumped = true;
                    System.out.println("[captureNetworkValue] OkHttp response lines found in logcat:");
                    fresh.lines()
                        .filter(l -> l.contains("OkHttp") && l.contains("<--"))
                        .forEach(l -> System.out.println("  >> " + l));
                }

                // Print progress every 10s so the log doesn't go silent
                if (System.currentTimeMillis() - lastProgressAt >= 10_000) {
                    lastProgressAt = System.currentTimeMillis();
                    long remaining = (deadline - System.currentTimeMillis()) / 1000;
                    System.out.println("[captureNetworkValue] still waiting... " + elapsed + "s elapsed, "
                        + remaining + "s left | OkHttp lines in last batch: " + okHttpLines);
                }

                String body = findResponseBody(allLogs.toString(), urlPattern);
                if (body != null) {
                    System.out.println("[captureNetworkValue] captured response: " + truncate(body, 400));
                    if (step.extract != null && !step.extract.isEmpty()) {
                        extractFields(body, step.extract);
                    }
                    return;
                }

                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("captureNetworkValue: interrupted");
            } catch (Exception e) {
                System.out.println("[captureNetworkValue] logcat read error: " + e.getMessage());
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }

        // On timeout, dump a diagnostic snippet to help triage
        String sample = allLogs.toString();
        long okHttpLines = sample.lines().filter(l -> l.contains("OkHttp")).count();
        System.out.println("[captureNetworkValue] timeout diagnostics — total logcat lines: "
            + sample.lines().count() + ", OkHttp lines: " + okHttpLines);
        if (okHttpLines == 0) {
            System.out.println("[captureNetworkValue] ⚠️  No OkHttp lines in logcat.");
            System.out.println("  → Is this a debug/qaDebug build with HttpLoggingInterceptor at BODY level?");
            // Show a few lines so the user can see what tags are present
            sample.lines().limit(10).forEach(l -> System.out.println("  logcat> " + l));
        } else {
            System.out.println("[captureNetworkValue] OkHttp lines found but URL pattern '" + urlPattern + "' not matched.");
            sample.lines().filter(l -> l.contains("OkHttp")).limit(10)
                .forEach(l -> System.out.println("  okhttp> " + l));
        }

        throw new RuntimeException(
            "[captureNetworkValue] No response for '" + urlPattern + "' found in logcat within " + timeout + "s.\n"
            + "Ensure the app is a debug build with OkHttp HttpLoggingInterceptor at BODY level.");
    }

    // ── ADB logcat reader ─────────────────────────────────────────────────────

    /**
     * Runs `adb [-s udid] logcat -d *:D` and returns all output as a string.
     * Uses DEBUG level so OkHttp entries (logged at D) are included.
     * `-d` dumps the buffer and exits (non-blocking).
     */
    private static String readLogcatViaAdb(String udid) throws Exception {
        String[] cmd = udid != null
            ? new String[]{"adb", "-s", udid, "logcat", "-d", "*:D"}
            : new String[]{"adb", "logcat", "-d", "*:D"};

        Process proc = Runtime.getRuntime().exec(cmd);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        proc.waitFor();
        return sb.toString();
    }

    // ── Logcat parsing ────────────────────────────────────────────────────────

    /**
     * Searches logcat text for an OkHttp response line containing {@code urlPattern},
     * then returns the JSON body that follows it.
     *
     * OkHttp BODY-level format (as it appears in raw adb logcat):
     *   "D/OkHttp  ( 1234): <-- 200 https://...{urlPattern} (123ms)"
     *   "D/OkHttp  ( 1234): {\"data\":{...}}"
     *   "D/OkHttp  ( 1234): <-- END HTTP (N-byte body)"
     */
    private static String findResponseBody(String logcat, String urlPattern) {
        String[] lines = logcat.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("<--") && line.contains(urlPattern)) {
                for (int j = i + 1; j < Math.min(i + 50, lines.length); j++) {
                    String payload = stripLogPrefix(lines[j]);
                    if (payload.startsWith("<-- END") || payload.startsWith("--> ")) break;
                    if (payload.isBlank()) continue;
                    String json = tryParseJson(payload);
                    if (json != null) return json;
                }
            }
        }
        return null;
    }

    /**
     * Strips the logcat tag prefix, leaving just the OkHttp message payload.
     * Raw adb line: "08-12 10:23:01.123  1234  5678 D OkHttp  : <-- 200 ..."
     * Brief format: "D/OkHttp ( 1234): <-- 200 ..."
     */
    private static String stripLogPrefix(String line) {
        int sep = line.lastIndexOf("): ");
        if (sep >= 0) return line.substring(sep + 3).trim();
        sep = line.lastIndexOf(": ");
        return sep >= 0 ? line.substring(sep + 2).trim() : line.trim();
    }

    private static String tryParseJson(String s) {
        String trimmed = s.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null;
        try {
            mapper.readTree(trimmed);
            return trimmed;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Field extraction ──────────────────────────────────────────────────────

    private static void extractFields(String body, Map<String, String> extract) {
        try {
            JsonNode root = mapper.readTree(body);
            for (Map.Entry<String, String> entry : extract.entrySet()) {
                String varName  = entry.getKey();
                String jsonPath = entry.getValue();
                String value    = extractPath(root, jsonPath);
                if (value == null || value.isBlank()) {
                    throw new RuntimeException(
                        "captureNetworkValue: path \"" + jsonPath + "\" not found or empty in response.\n"
                        + "Full JSON body: " + truncate(body, 600) + "\n"
                        + "Check the 'extract' path in your YAML.");
                }
                TestContext.setScalarData(varName, value);
                System.out.printf("[captureNetworkValue] ${%s} = \"%s\"  (path: %s)%n",
                    varName, value, jsonPath);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                "captureNetworkValue: failed to parse JSON — " + e.getMessage()
                + "\nBody: " + truncate(body, 300));
        }
    }

    private static String extractPath(JsonNode root, String dotPath) {
        JsonNode node = root;
        for (String key : dotPath.split("\\.")) {
            if (node == null || node.isMissingNode()) return "";
            node = node.path(key);
        }
        return (node != null && !node.isMissingNode() && !node.isNull()) ? node.asText() : "";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "(null)";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
