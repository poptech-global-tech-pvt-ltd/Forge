package com.popclub.ai.app;

import com.popclub.mobile.driver.AppiumServerManager;
import com.popclub.mobile.driver.DeviceManager;
import com.popclub.mobile.driver.DeviceInfo;
import com.popclub.parser.XmlElementParser;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Standalone QA tag scanner.
 *
 * Starts Appium server, launches the app fresh, then lets you navigate
 * screen by screen and scan test tags on demand.
 *
 * Usage:  ./run-tag-finder.sh
 */
public class TagFinder {

    private static final String REPORTS_DIR  = "reports";
    private static final String APP_PACKAGE  = "com.popclub.android";
    private static final String APP_ACTIVITY = "com.popclub.android.LauncherFresh";

    public static void main(String[] args) throws Exception {

        // Optional: pass device UDID as first argument
        //   ./run-tag-finder.sh emulator-5554
        //   ./run-tag-finder.sh R5CTA1XXXXX
        if (args.length > 0 && !args[0].isBlank()) {
            System.setProperty("stf.device.serial", args[0].trim());
            System.out.println("Using device: " + args[0].trim());
        }

        AndroidDriver driver = createDriver();
        System.out.println("App launched. Navigate on the device.");
        System.out.println("Press ENTER to scan current screen.");
        System.out.println("Type a screen name before ENTER to label it, or just ENTER to auto-detect.");
        System.out.println("Type 'exit' to stop and write summary.\n");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        List<QaTagAnalyzer.ScreenReport> allReports = new ArrayList<>();

        while (true) {
            System.out.print("Screen name (or ENTER): ");
            String input = reader.readLine();
            if ("exit".equalsIgnoreCase(input != null ? input.trim() : "")) break;

            String screenName = (input != null && !input.isBlank())
                    ? input.trim()
                    : resolveActivity(driver);

            System.out.println("Scanning: " + screenName + " ...");
            Thread.sleep(800); // let UI settle

            String xml = driver.getPageSource();
            List<Map<String, String>> elements = XmlElementParser.parse(xml);
            QaTagAnalyzer.ScreenReport report = QaTagAnalyzer.analyse(elements, screenName);
            QaTagAnalyzer.printReport(report);
            QaTagAnalyzer.writeYaml(report, REPORTS_DIR);
            allReports.add(report);
        }

        if (!allReports.isEmpty()) {
            QaTagAnalyzer.writeSummary(allReports, REPORTS_DIR);
            System.out.println("\nSummary → " + REPORTS_DIR + "/qa-app-scan/summary.yaml");
        }

        driver.quit();
        System.out.println("Done.");
    }

    private static AndroidDriver createDriver() throws Exception {
        DeviceInfo device = DeviceManager.getDevice();
        String udid = device.udid;
        int port    = device.port;

        System.out.println("Device: " + udid + " | Port: " + port);
        AppiumServerManager.startServer(udid, port);

        int systemPort;
        try (ServerSocket s = new ServerSocket(0)) { systemPort = s.getLocalPort(); }
        catch (IOException e) { systemPort = 8200 + port; }

        UiAutomator2Options options = new UiAutomator2Options()
                .setPlatformName("Android")
                .setDeviceName(udid)
                .setUdid(udid)
                .setAutomationName("UiAutomator2")
                .setAppPackage(APP_PACKAGE)
                .setAppActivity(APP_ACTIVITY)
                .setAutoGrantPermissions(true)
                .setNoReset(false)
                .setNewCommandTimeout(Duration.ofSeconds(300))
                .setSystemPort(systemPort);

        return new AndroidDriver(new URL("http://127.0.0.1:" + port), options);
    }

    private static String resolveActivity(AndroidDriver driver) {
        try {
            String activity = driver.currentActivity();
            String[] parts  = activity.split("\\.");
            return parts[parts.length - 1];
        } catch (Exception ignored) {
            return "unknown_screen";
        }
    }
}
