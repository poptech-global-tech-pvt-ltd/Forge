package com.popclub.iOSTests;

import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import org.testng.annotations.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Dumps the XCUITest hierarchy of the currently installed iOS app to reports/ios_hierarchy.xml.
 * Run with: mvn test -Dsurefire.suiteXmlFiles=src/test/resources/suites/testng-ios-dump.xml
 */
public class DumpIOSHierarchy {

    private static final String UDID      = "00008110-000469E636E8401E";
    private static final String BUNDLE_ID = "com.popclub.popclubapp";

    @Test
    public void dumpHierarchy() throws Exception {
        // Start Appium (must already be running on 4850, or start it manually)
        // Ensure: appium --port 4850 --relaxed-security &

        XCUITestOptions options = new XCUITestOptions();
        options.setPlatformName("iOS");
        options.setUdid(UDID);
        options.setDeviceName("Admin's iPhone");
        options.setAutomationName("XCUITest");
        options.setBundleId(BUNDLE_ID);
        options.setNoReset(true);
        options.setNewCommandTimeout(Duration.ofSeconds(120));
        options.setCapability("appium:xcodeOrgId", "QR39T9B2GQ");
        options.setCapability("appium:xcodeSigningId", "Apple Development");
        options.setCapability("appium:updatedWDABundleId", "com.popclub.wda");
        options.setCapability("appium:usePrebuiltWDA", true);
        options.setCapability("appium:wdaLaunchTimeout", 120000);
        options.setCapability("appium:wdaConnectionTimeout", 120000);
        options.setWdaLocalPort(8101);

        System.out.println("[DumpHierarchy] Connecting to Appium...");
        IOSDriver driver = new IOSDriver(new URL("http://127.0.0.1:4850"), options);

        try {
            System.out.println("[DumpHierarchy] Session created: " + driver.getSessionId());
            String source = driver.getPageSource();

            Path out = Path.of("reports/ios_hierarchy.xml");
            Files.createDirectories(out.getParent());
            Files.writeString(out, source);

            System.out.println("[DumpHierarchy] ✅ Saved hierarchy to " + out.toAbsolutePath());
            System.out.println("[DumpHierarchy] Lines: " + source.lines().count());

            // Print accessibility IDs from the tree
            System.out.println("\n[DumpHierarchy] Accessibility IDs found:");
            source.lines()
                  .filter(l -> l.contains("name=\"") || l.contains("label=\""))
                  .map(l -> l.trim())
                  .distinct()
                  .limit(60)
                  .forEach(System.out::println);
        } finally {
            driver.quit();
        }
    }
}
