package com.popclub.runner;

import com.popclub.core.DeviceAwarePrintStream;
import com.popclub.core.TestContext;
import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.android.cloud.CloudConfig;
import com.popclub.android.driver.AppiumServerManager;
import com.popclub.ios.driver.IOSDriverManager;
import com.popclub.ios.driver.IOSDeviceManager;
import com.popclub.ios.driver.IOSAppiumServerManager;
import com.popclub.model.TestCase;
import com.popclub.parser.YamlParser;
import com.popclub.testsigma.TestSigmaClient;
import org.testng.ITestContext;
import org.testng.annotations.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestRunnerTest {

    List<String> stepTexts;

    @BeforeMethod
    public void loadTestSigmaSteps() {
        // TODO: Re-enable TestSigma integration once token is refreshed
//        String projectId = "d8f4a221-bc6d-47d8-9448-0834f5d012ec";
//        String testCaseUUID = TestSigmaClient.getTestCaseIdByHumanId(projectId, "PO-7053");
//        List<Map<String,Object>> steps = TestSigmaClient.getTestCaseSteps(projectId, testCaseUUID);
//        stepTexts = TestSigmaClient.extractStepTexts(steps);
    }

    @DataProvider(name = "testData", parallel = false)
    public Object[][] getTestData(ITestContext context) {

        String platformParam = System.getProperty("platform",
                context.getCurrentXmlTest().getParameter("platform"));
        String testFileParam0 = System.getProperty("testFile",
                context.getCurrentXmlTest().getParameter("testFile"));

        // Auto-detect platform from testFile name if not explicitly set
        if ((platformParam == null || platformParam.isBlank()) && testFileParam0 != null) {
            if (testFileParam0.toLowerCase().contains("ios") ||
                    new File("src/test/java/com/popclub/iOSTests/" + testFileParam0).exists()) {
                platformParam = "ios";
            }
        }

        String primaryDir  = "ios".equalsIgnoreCase(platformParam)
                ? "src/test/java/com/popclub/iOSTests"
                : "src/test/java/com/popclub/androidTests";
        String fallbackDir = "ios".equalsIgnoreCase(platformParam)
                ? "src/test/java/com/popclub/androidTests"
                : "src/test/java/com/popclub/iOSTests";

        List<File> allFiles = new ArrayList<>();
        collectYamlFiles(new File(primaryDir), allFiles);
        if (allFiles.isEmpty()) {
            throw new RuntimeException("No YAML files found in " + primaryDir);
        }

        // Wire per-<test> deviceSerial into the ThreadLocal so DeviceManager picks the right device.
        // Must happen on the test thread (not the suite listener) so parallel runs don't collide.
        CloudConfig.setDeviceSerialFromTestNG(
                context.getCurrentXmlTest().getParameter("deviceSerial"));

        String tagParam      = System.getProperty("tag",
                                context.getCurrentXmlTest().getParameter("tag"));
        String testFileParam = testFileParam0;

        // Build an ordered name list from the comma-separated testFile param
        List<String> orderedNames = new ArrayList<>();
        if (testFileParam != null && !testFileParam.isEmpty()) {
            for (String name : testFileParam.split(",")) {
                orderedNames.add(name.trim().toLowerCase());
            }
        }

        // Index files by lowercase filename (basename only) for -DtestFile= lookup
        Map<String, File> fileIndex = new java.util.HashMap<>();
        for (File file : allFiles) {
            fileIndex.put(file.getName().toLowerCase(), file);
        }

        // Build fallback index from the other platform's test dir
        Map<String, File> fallbackIndex = new java.util.HashMap<>();
        List<File> fallbackFiles = new ArrayList<>();
        collectYamlFiles(new File(fallbackDir), fallbackFiles);
        for (File file : fallbackFiles) {
            fallbackIndex.put(file.getName().toLowerCase(), file);
        }

        // Collect in declared order (or natural discovery order if no filter)
        List<File> orderedFiles = new ArrayList<>();
        if (!orderedNames.isEmpty()) {
            for (String name : orderedNames) {
                File f = fileIndex.get(name);
                if (f == null) f = fallbackIndex.get(name);
                if (f != null) {
                    orderedFiles.add(f);
                } else {
                    System.out.println("Warning: testFile '" + name + "' not found in either test directory");
                }
            }
        } else {
            orderedFiles.addAll(allFiles);
        }

        List<Object[]> filtered = new ArrayList<>();

        for (File file : orderedFiles) {

            // Skip files not in the filter (already handled by orderedFiles, but kept for tag-only runs)
            if (!orderedNames.isEmpty() && !orderedNames.contains(file.getName().toLowerCase())) {
                System.out.println("Skipping: " + file.getName() + " (not in testFile filter)");
                continue;
            }

            TestCase testCase = YamlParser.parse(file.getPath());
            testCase.sourceFile = file.getPath(); // used by SelfHealingEngine

            // ✅ TAG FILTERING BEFORE EXECUTION
            if (tagParam != null && !tagParam.isEmpty()) {

                List<String> requiredTags = List.of(tagParam.split(","));

                if (testCase.tags == null ||
                        testCase.tags.stream().noneMatch(requiredTags::contains)) {

                    System.out.println("Skipping: " + file.getName() + " (tag mismatch)");
                    continue;
                }
            }

            // 🔥 CORE LINE
            filtered.add(new Object[]{testCase});
        }

        if (filtered.isEmpty()) {
            if (!orderedNames.isEmpty() && orderedFiles.isEmpty()) {
                throw new RuntimeException("No test files found matching: " + testFileParam0 + " (searched " + primaryDir + " and " + fallbackDir + ")");
            }
            throw new RuntimeException("No tests matched filter — testFile=" + testFileParam0 + " tag=" + tagParam);
        }

        // Register the total test count for this device so AppiumDriverManager
        // knows to keep the session alive until the last test completes.
        String serial = context.getCurrentXmlTest().getParameter("deviceSerial");
        if (serial != null && !serial.isBlank()) {
            AppiumDriverManager.setExpectedTestCount(serial, filtered.size());
        }

        return filtered.toArray(new Object[0][0]);
    }

    /**
     * Recursively collects all {@code .yaml} files under {@code dir},
     * scanning subdirectories in alphabetical order so execution order
     * is deterministic across machines.
     */
    private void collectYamlFiles(File dir, List<File> result) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        java.util.Arrays.sort(entries); // alphabetical within each directory
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectYamlFiles(entry, result);
            } else if (entry.getName().endsWith(".yaml")) {
                result.add(entry);
            }
        }
    }

    @Test(dataProvider = "testData")
    public void runTest(TestCase testCase) {

        try {
            TestContext.setTestCaseIds(testCase.testCaseIds);
            new TestExecutor().execute(testCase);
        } catch (Exception e) {
            throw new RuntimeException("Test failed: " + testCase.testName, e);
        }
    }

    @AfterMethod(alwaysRun = true)
    public void quitDriverAfterTest() {
        String platform = TestContext.getPlatform();
        if ("ios".equalsIgnoreCase(platform)) {
            IOSDriverManager.quitDriver();
        } else {
            AppiumDriverManager.quitDriver();
        }
    }

    // TODO: Re-enable once TestSigma token is refreshed
//    @Test
//    public void loginTest() {
//        stepTexts.forEach(System.out::println);
//    }



    @BeforeSuite
    @org.testng.annotations.Parameters({"deviceUdid", "platform"})
    public void setUp(@org.testng.annotations.Optional("") String deviceUdid,
                      @org.testng.annotations.Optional("android") String platform) {
        System.setOut(new DeviceAwarePrintStream(System.out));
        if ("ios".equalsIgnoreCase(platform) && !deviceUdid.isBlank()) {
            IOSDeviceManager.setFixedUdid(deviceUdid);
        }
    }

    @AfterSuite
    public void tearDown() {
        String platform = TestContext.getPlatform();
        if ("ios".equalsIgnoreCase(platform)) {
            IOSAppiumServerManager.stopAll();
        } else if (!CloudConfig.isCloudEnabled()) {
            // Cloud Appium servers are pre-started externally on the STF Mac — never stop them.
            System.out.println("Stopping all Appium servers...");
            AppiumServerManager.stopAll();
        }
    }
}