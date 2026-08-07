package com.popclub.android.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.core.TestContext;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;

import java.util.List;
import java.util.Map;

/**
 * captureNetworkValue — waits for the device to make a network request whose URL
 * contains {@code step.value}, then extracts fields from the JSON response body
 * and stores them as TestContext variables.
 *
 * How it works:
 *   Polls Android logcat via Appium's log API. Debug (qaDebug) builds log all
 *   HTTP traffic via OkHttp's HttpLoggingInterceptor, which emits lines like:
 *
 *     D  OkHttp: <-- 200 https://...user-login/initiate (543ms)
 *     D  OkHttp: {"data":{"request_id":"abc123"},...}
 *     D  OkHttp: <-- END HTTP (77-byte body)
 *
 *   The action searches for a "<-- NNN" response line whose URL contains
 *   {@code step.value}, then captures the first JSON body line after it.
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

        AppiumDriver driver = AppiumDriverManager.getDriver();
        long deadline = System.currentTimeMillis() + (long) timeout * 1000;

        // Accumulate all logcat entries seen so far
        StringBuilder allLogs = new StringBuilder();

        while (System.currentTimeMillis() < deadline) {
            try {
                LogEntries entries = driver.manage().logs().get("logcat");
                for (LogEntry entry : entries.getAll()) {
                    allLogs.append(entry.getMessage()).append('\n');
                }

                String body = findResponseBody(allLogs.toString(), urlPattern);
                if (body != null) {
                    System.out.println("[captureNetworkValue] captured response: " + truncate(body, 400));
                    if (step.extract != null && !step.extract.isEmpty()) {
                        extractFields(body, step.extract);
                    }
                    return;
                }

                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("captureNetworkValue: interrupted");
            } catch (Exception e) {
                System.out.println("[captureNetworkValue] logcat read error: " + e.getMessage());
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }

        throw new RuntimeException(
            "[captureNetworkValue] No response for '" + urlPattern + "' found in logcat within " + timeout + "s.\n"
            + "Ensure the app is a debug build with OkHttp HttpLoggingInterceptor at BODY level.");
    }

    // ── Logcat parsing ────────────────────────────────────────────────────────

    /**
     * Searches the accumulated logcat text for an OkHttp response line that
     * contains {@code urlPattern}, then returns the JSON body that follows it.
     *
     * OkHttp BODY-level log format:
     *   "<-- 200 https://...{urlPattern} (123ms)"   ← response header line
     *   "{\"data\":{...}}"                           ← response body line
     *   "<-- END HTTP (N-byte body)"
     */
    private static String findResponseBody(String logcat, String urlPattern) {
        String[] lines = logcat.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Match OkHttp response header line: "<-- NNN ... url ... (NNms)"
            if (line.contains("<--") && line.contains(urlPattern)) {
                // Next non-empty lines until "<-- END HTTP" are the body
                for (int j = i + 1; j < Math.min(i + 30, lines.length); j++) {
                    String next = stripLogPrefix(lines[j]);
                    if (next.startsWith("<-- END") || next.startsWith("--> ")) break;
                    if (next.isBlank()) continue;
                    // Try to parse as JSON
                    String json = tryParseJson(next);
                    if (json != null) return json;
                }
            }
        }
        return null;
    }

    /**
     * Strips the logcat timestamp/tag prefix so we're left with the OkHttp message.
     * Logcat lines look like: "07-23 16:45:01.123  1234  5678 D OkHttp  : <-- 200 ..."
     */
    private static String stripLogPrefix(String line) {
        // Find the last ": " separator (after the tag) and take what follows
        int sep = line.lastIndexOf(": ");
        return sep >= 0 ? line.substring(sep + 2).trim() : line.trim();
    }

    /** Returns the input string if it is valid JSON, otherwise null. */
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

    /**
     * Traverses a JsonNode tree using dot-notation (e.g. "data.request_id").
     * Returns "" if any segment is missing.
     */
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
