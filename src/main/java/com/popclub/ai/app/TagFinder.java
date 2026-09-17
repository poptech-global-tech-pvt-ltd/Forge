package com.popclub.ai.app;

import com.popclub.parser.XmlElementParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Standalone QA tag scanner — uses ADB only, no Appium needed.
 *
 * Usage:
 *   ./run-tag-finder.sh                     ← auto-detect device
 *   ./run-tag-finder.sh 10BDCM0YJZ00043     ← specific device
 */
public class TagFinder {

    private static final String REPORTS_DIR = "reports";
    private static final String APP_PACKAGE  = "com.popclub.android";
    private static final String APP_ACTIVITY = "com.popclub.android.LauncherClassic";

    public static void main(String[] args) throws Exception {

        System.out.println("[1] Detecting device...");
        String udid = args.length > 0 ? args[0].trim() : detectDevice();
        System.out.println("[2] Device: " + udid);

        System.out.println("[3] Launching app...");
        launchApp(udid);
        System.out.println("[4] App launched.");

        System.out.println("\nNavigate on the device, then press ENTER to scan.");
        System.out.println("Type a screen name before ENTER to label it.");
        System.out.println("Type 'exit' to stop.\n");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        List<QaTagAnalyzer.ScreenReport> allReports = new ArrayList<>();

        while (true) {
            System.out.print("Screen name (or ENTER to detect): ");
            String input = reader.readLine();
            if ("exit".equalsIgnoreCase(input != null ? input.trim() : "")) break;

            String screenName = (input != null && !input.isBlank())
                    ? input.trim()
                    : resolveActivity(udid);

            System.out.println("Scanning: " + screenName + " ...");

            String xml = getPageSource(udid);
            if (xml == null || xml.isBlank()) {
                System.out.println("  Could not get screen XML — is the app open on the device?");
                continue;
            }

            List<Map<String, String>> elements = XmlElementParser.parse(xml);
            QaTagAnalyzer.ScreenReport report = QaTagAnalyzer.analyse(elements, screenName);
            QaTagAnalyzer.printReport(report);
            QaTagAnalyzer.writeYaml(report, REPORTS_DIR);
            allReports.add(report);
        }

        if (!allReports.isEmpty()) {
            QaTagAnalyzer.writeSummary(allReports, REPORTS_DIR);
            System.out.println("Summary → " + REPORTS_DIR + "/qa-app-scan/summary.yaml");
        }

        System.out.println("Done.");
    }

    // ── App launch ────────────────────────────────────────────────────────────

    private static void launchApp(String udid) throws Exception {
        System.out.println("[3a] Waking screen...");
        adb(udid, "shell", "input", "keyevent", "224");

        // Verify the package is installed before trying to launch
        System.out.println("[3b] Checking package installed...");
        String installedPkg = adbOutput(udid, "shell", "pm", "list", "packages", APP_PACKAGE);
        if (!installedPkg.contains(APP_PACKAGE)) {
            System.out.println("  ✗ Package not found: " + APP_PACKAGE);
            System.out.println("  Installed com.popclub.* packages:");
            String allPkgs = adbOutput(udid, "shell", "pm", "list", "packages", "com.popclub");
            System.out.println("  " + allPkgs.trim().replace("\n", "\n  "));
            throw new RuntimeException(
                "App not installed. Check APP_PACKAGE in TagFinder.java.\n" +
                "Current value: " + APP_PACKAGE);
        }
        System.out.println("  ✓ Found: " + APP_PACKAGE);

        System.out.println("[3c] Force stopping app...");
        Thread.sleep(500);
        adb(udid, "shell", "am", "force-stop", APP_PACKAGE);

        System.out.println("[3d] Starting app...");
        Thread.sleep(1000);
        String startResult = adbOutput(udid, "shell", "am", "start", "-n",
                APP_PACKAGE + "/" + APP_ACTIVITY);
        System.out.println("  am start → " + startResult.trim());
        if (startResult.contains("Error type") || startResult.contains("does not exist")) {
            throw new RuntimeException("Failed to start app: " + startResult.trim());
        }

        System.out.println("[3e] Waiting 4s for app to load...");
        Thread.sleep(4000);
    }

    // ── Page source via uiautomator dump ─────────────────────────────────────

    private static String getPageSource(String udid) throws Exception {
        Thread.sleep(800);
        // Wake screen first to ensure UI is ready
        adb(udid, "shell", "input", "keyevent", "224");
        Thread.sleep(500);

        adb(udid, "shell", "uiautomator", "dump", "/sdcard/wd.xml");
        Path tmp = Files.createTempFile("wd_", ".xml");
        adb(udid, "pull", "/sdcard/wd.xml", tmp.toAbsolutePath().toString());
        String xml = Files.readString(tmp);
        Files.deleteIfExists(tmp);

        int start = xml.indexOf("<hierarchy");
        int end   = xml.lastIndexOf("</hierarchy>");
        if (start >= 0 && end >= 0) {
            return xml.substring(start, end + "</hierarchy>".length());
        }
        return null;
    }

    // ── Activity detection ────────────────────────────────────────────────────

    private static String resolveActivity(String udid) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "adb", "-s", udid, "shell", "dumpsys", "window", "windows");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains("mCurrentFocus") || line.contains("mFocusedApp")) {
                        int slash = line.lastIndexOf('/');
                        int brace = line.lastIndexOf('}');
                        if (slash >= 0 && brace > slash) {
                            String activity = line.substring(slash + 1, brace).trim();
                            String[] parts = activity.split("\\.");
                            return parts[parts.length - 1];
                        }
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        return "unknown_screen";
    }

    // ── ADB device detection ──────────────────────────────────────────────────

    private static String detectDevice() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("adb", "devices");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        List<String> udids = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.endsWith("\tdevice")) udids.add(line.split("\t")[0].trim());
            }
        }
        p.waitFor();
        if (udids.isEmpty()) throw new RuntimeException("No ADB device connected.");
        if (udids.size() > 1) System.out.println("Multiple devices found, using: " + udids.get(0));
        return udids.get(0);
    }

    // ── ADB helpers ───────────────────────────────────────────────────────────

    /** Run adb command, discard output. */
    private static void adb(String udid, String... cmd) throws Exception {
        adbOutput(udid, cmd);
    }

    /** Run adb command, return stdout as string. */
    private static String adbOutput(String udid, String... cmd) throws Exception {
        List<String> full = new ArrayList<>();
        full.add("adb"); full.add("-s"); full.add(udid);
        for (String c : cmd) full.add(c);
        ProcessBuilder pb = new ProcessBuilder(full);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        }
        p.waitFor();
        return sb.toString();
    }
}
