package com.popclub.web.util;

import com.popclub.api.util.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Aadhaar OTP from the Android device's SMS inbox via ADB.
 *
 * <p>After YES Bank triggers an Aadhaar OTP, UIDAI sends an SMS to the
 * Aadhaar-linked mobile number (e.g. "Your OTP for Aadhaar Authentication
 * is 123456. This OTP is valid for 10 minutes.").
 *
 * <p>This class polls {@code content://sms/inbox} on the connected ADB device
 * until the OTP arrives (or times out). Requires the device to be reachable
 * via {@code adb} — uses {@code DEVICE_SERIAL} from {@code local.properties}.
 *
 * <p>Usage:
 * <pre>
 *   yesBankPage.clickSubmit();          // triggers OTP send
 *   String otp = AadhaarOtpReader.waitForOtp(30_000);
 *   yesBankPage.fillOtp(otp).clickSubmitOtp();
 * </pre>
 */
public class AadhaarOtpReader {

    private static final Logger log = LoggerFactory.getLogger(AadhaarOtpReader.class);

    /** Matches a standalone 6-digit number that is the OTP. */
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b([0-9]{6})\\b");

    /**
     * Known OTP senders for Aadhaar eKYC.
     * UIDAI sends from "UIDAI" or numeric short-codes like "524800" / "YESBNK".
     * An empty array means: accept any sender (search all recent messages).
     */
    private static final String[] OTP_SENDERS = {
            "UIDAI", "524800", "YESBNK", "YESBK", "YESBANK"
    };

    /** Keywords that must appear in the SMS body (case-insensitive). */
    private static final String[] BODY_KEYWORDS = {
            "otp", "one time", "authentication"
    };

    /**
     * Polls the device SMS inbox until an Aadhaar OTP is found or the timeout elapses.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @return the 6-digit OTP string
     * @throws RuntimeException if no OTP is found within the timeout
     */
    /**
     * Waits for a fresh Aadhaar OTP sent AFTER this method is called.
     * Captures the current time first, then polls for an SMS with a date > startedAt.
     * This prevents stale OTPs from previous test runs from being returned.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @return the 6-digit OTP string
     * @throws RuntimeException if no OTP arrives within the timeout
     */
    public static String waitForOtp(long timeoutMs) {
        long startedAt    = System.currentTimeMillis();
        long deadline     = startedAt + timeoutMs;
        long pollInterval = 3_000;

        log.info("[AadhaarOtpReader] Waiting up to {}s for Aadhaar OTP (SMS after {})…",
                timeoutMs / 1000, startedAt);

        while (System.currentTimeMillis() < deadline) {
            String otp = readLatestOtpAfter(startedAt);
            if (otp != null) {
                log.info("[AadhaarOtpReader] OTP found: {}", otp);
                return otp;
            }
            log.debug("[AadhaarOtpReader] OTP not found yet — retrying in {}ms", pollInterval);
            sleep(pollInterval);
        }

        throw new RuntimeException(
                "[AadhaarOtpReader] Timed out after " + (timeoutMs / 1000) + "s — no Aadhaar OTP received after "
                + startedAt + ". Ensure the device (" + deviceLabel() + ") is reachable via ADB and the SMS was delivered.");
    }

    /**
     * Reads the most recent Aadhaar OTP from the device SMS inbox right now.
     * Only considers messages whose {@code date} column (Unix ms) is >= {@code afterEpochMs}.
     * Returns {@code null} if no matching message is found.
     */
    public static String readLatestOtpAfter(long afterEpochMs) {
        try {
            // Query SMS inbox sorted newest-first; limit to top 20 rows
            String[] cmd = buildAdb(
                    "shell", "content", "query",
                    "--uri", "content://sms/inbox",
                    "--projection", "address,body,date",
                    "--sort", "date DESC"
            );

            String output = run(cmd);
            if (output == null || output.isBlank()) {
                log.debug("[AadhaarOtpReader] ADB SMS query returned empty output");
                return null;
            }

            // Each row is: "Row: N address=..., body=..., date=..."
            String[] rows = output.split("Row:");
            int checked = 0;
            for (String row : rows) {
                if (row.isBlank()) continue;
                if (++checked > 20) break; // only scan the 20 most recent

                // Skip SMS older than the test started — avoids stale OTPs from previous runs
                long smsDate = extractDate(row);
                if (smsDate > 0 && smsDate < afterEpochMs) {
                    log.debug("[AadhaarOtpReader] Skipping old SMS (date={} < afterEpochMs={})", smsDate, afterEpochMs);
                    continue;
                }

                if (!isRelevantMessage(row)) continue;

                String otp = extractOtp(row);
                if (otp != null) {
                    log.info("[AadhaarOtpReader] Matched row (date={}): {}", smsDate, row.trim().replace('\n', ' '));
                    return otp;
                }
            }
        } catch (Exception e) {
            log.warn("[AadhaarOtpReader] Failed to read SMS via ADB: {}", e.getMessage());
        }
        return null;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns true if this SMS row looks like an Aadhaar/eKYC OTP message. */
    private static boolean isRelevantMessage(String row) {
        String lower = row.toLowerCase();

        // Must contain at least one body keyword
        boolean hasKeyword = false;
        for (String kw : BODY_KEYWORDS) {
            if (lower.contains(kw)) { hasKeyword = true; break; }
        }
        if (!hasKeyword) return false;

        // If we have a known-sender list, check it; otherwise accept any sender
        if (OTP_SENDERS.length == 0) return true;
        for (String sender : OTP_SENDERS) {
            if (lower.contains(sender.toLowerCase())) return true;
        }

        // Also accept if the body explicitly mentions "aadhaar"
        return lower.contains("aadhaar");
    }

    /** Extracts the Unix-epoch-ms {@code date} field from the SMS row, or -1 if missing. */
    private static long extractDate(String row) {
        int idx = row.indexOf(", date=");
        if (idx < 0) idx = row.indexOf("date=");
        if (idx < 0) return -1;
        String after = row.substring(idx + (row.charAt(idx) == ',' ? 7 : 5)).trim();
        // date value ends at next ", " or end-of-string
        int end = after.indexOf(',');
        String dateStr = (end > 0 ? after.substring(0, end) : after).trim();
        try { return Long.parseLong(dateStr); } catch (NumberFormatException e) { return -1; }
    }

    /** Extracts the first 6-digit number from the SMS row (the OTP). */
    private static String extractOtp(String row) {
        // Extract the body value from "body=<text>, date=..."
        int bodyIdx = row.indexOf("body=");
        if (bodyIdx < 0) return null;
        String bodyOnward = row.substring(bodyIdx + 5);
        // body ends at ", date=" marker
        int dateIdx = bodyOnward.indexOf(", date=");
        String body = dateIdx > 0 ? bodyOnward.substring(0, dateIdx) : bodyOnward;

        Matcher m = OTP_PATTERN.matcher(body);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String[] buildAdb(String... args) {
        String serial = ApiConstants.DEVICE_SERIAL;
        String[] base = (serial != null && !serial.isBlank())
                ? new String[]{"adb", "-s", serial}
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

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String deviceLabel() {
        String serial = ApiConstants.DEVICE_SERIAL;
        return (serial != null && !serial.isBlank()) ? serial : "default";
    }
}