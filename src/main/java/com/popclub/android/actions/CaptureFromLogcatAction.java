package com.popclub.android.actions;

import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.core.TestContext;
import com.popclub.model.Step;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * captureFromLogcat — greps device logcat for a pattern and extracts named values
 * via regex capture groups. Unlike captureNetworkValue, this doesn't need response
 * bodies — it works with URL patterns, headers, or any logcat text.
 *
 * YAML syntax:
 *
 *   - action: captureFromLogcat
 *     value: "basket/quantity/(\\d+)/"
 *     extract:
 *       api_cart_id: "1"          # capture group number
 *
 *   - action: captureFromLogcat
 *     value: "shipping_address_id=(\\d+)"
 *     extract:
 *       api_shipping_address_id: "1"
 */
public class CaptureFromLogcatAction implements Action {

    @Override
    public void perform(Step step) {
        String regexPattern = step.value;
        if (regexPattern == null || regexPattern.isBlank()) {
            throw new RuntimeException("captureFromLogcat: 'value' (regex pattern) is required");
        }

        String udid = AppiumDriverManager.deviceInfo.get() != null
            ? AppiumDriverManager.deviceInfo.get().udid
            : System.getProperty("deviceSerial", null);

        System.out.println("[captureFromLogcat] Searching logcat for: " + regexPattern);

        try {
            String logcat = readLogcat(udid);
            Pattern p = Pattern.compile(regexPattern);

            // Find the LAST match (most recent)
            Matcher m = p.matcher(logcat);
            String lastMatch = null;
            Matcher lastMatcher = null;
            while (m.find()) {
                lastMatch = m.group(0);
                // Save matcher state by re-matching
                lastMatcher = p.matcher(lastMatch);
                lastMatcher.find();
            }

            if (lastMatcher == null) {
                throw new RuntimeException(
                    "[captureFromLogcat] Pattern '" + regexPattern + "' not found in logcat");
            }

            System.out.println("[captureFromLogcat] Matched: " + lastMatch);

            if (step.extract != null) {
                for (Map.Entry<String, String> entry : step.extract.entrySet()) {
                    String varName = entry.getKey();
                    int groupNum = Integer.parseInt(entry.getValue().trim());
                    String value = lastMatcher.group(groupNum);
                    if (value == null || value.isBlank()) {
                        throw new RuntimeException(
                            "[captureFromLogcat] Group " + groupNum + " is empty in match: " + lastMatch);
                    }
                    TestContext.setScalarData(varName, value);
                    System.out.printf("[captureFromLogcat] ${%s} = \"%s\"%n", varName, value);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[captureFromLogcat] Failed: " + e.getMessage(), e);
        }
    }

    private static String readLogcat(String udid) throws Exception {
        String[] cmd;
        if (udid != null && !udid.isBlank()) {
            cmd = new String[]{"adb", "-s", udid, "shell",
                "logcat -d okhttp.OkHttpClient:I *:S"};
        } else {
            cmd = new String[]{"adb", "shell",
                "logcat -d okhttp.OkHttpClient:I *:S"};
        }

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
}
