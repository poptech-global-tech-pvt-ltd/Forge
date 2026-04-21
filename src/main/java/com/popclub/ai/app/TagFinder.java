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
import java.util.List;
import java.util.Map;

public class TagFinder {

    public static void main(String[] args) throws Exception {

        AndroidDriver driver = createDriver();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("TagFinder Started");
        System.out.println("Navigate manually on the device.");
        System.out.println("Press ENTER to print testTags.");
        System.out.println("Type 'exit' to stop.\n");

        while (true) {

            System.out.print("Press ENTER to scan: ");
            String input = reader.readLine();

            if ("exit".equalsIgnoreCase(input)) break;

            printTestTags(driver);
        }

        driver.quit();
        System.out.println("Done");
    }

    private static AndroidDriver createDriver() throws Exception {

        DeviceInfo device = DeviceManager.getDevice();

        String udid = device.udid;
        int port = device.port;

        System.out.println("Device: " + udid + " | Port: " + port);

        AppiumServerManager.startServer(udid, port);

        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setDeviceName(udid);
        options.setUdid(udid);
        options.setAutomationName("UiAutomator2");
        options.setApp(System.getProperty("user.dir") + "/src/main/resources/pop-debug.apk");
        options.setAutoGrantPermissions(true);
        options.setNoReset(false);
        options.setCapability("appium:dontStopAppOnReset", true); // avoids -S flag in am start-activity (multiple launcher activities bug)
        options.setNewCommandTimeout(Duration.ofSeconds(300));

        int systemPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            systemPort = socket.getLocalPort();
        } catch (IOException e) {
            systemPort = 8200 + port;
        }
        options.setSystemPort(systemPort);

        return new AndroidDriver(new URL("http://127.0.0.1:" + port), options);
    }

    private static void printTestTags(AndroidDriver driver) throws Exception {

        Thread.sleep(800); // wait for UI to settle

        String xml = driver.getPageSource();

        List<Map<String, String>> raw = XmlElementParser.parse(xml);

        System.out.println("\n--- Visible testTags ---");

        boolean found = false;

        for (Map<String, String> r : raw) {

            String tag = r.get("accessibilityId");

            if (tag != null && !tag.isBlank()) {
                System.out.println("  " + tag);
                found = true;
            }
        }

        if (!found) {
            System.out.println("  No testTags found on this screen.");
        }

        System.out.println("------------------------\n");
    }
}