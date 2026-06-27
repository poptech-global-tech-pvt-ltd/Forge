package com.popclub.listener;

import com.popclub.ai.FailureTagReporter;
import com.popclub.core.ScreenshotUtil;
import com.popclub.core.TestContext;
import com.popclub.core.VideoUtil;
import com.popclub.android.cloud.CloudConfig;
import com.popclub.android.driver.DeviceKeepAlive;
import com.popclub.android.driver.DriverManager;
import com.popclub.driver.ForgeDriverManager;
import com.popclub.model.TestCase;
import com.popclub.parser.YamlParser;
import com.popclub.testsigma.*;

import io.appium.java_client.AppiumDriver;
import org.testng.*;

import java.io.File;
import java.util.*;

public class TestListener implements ITestListener, ISuiteListener {

    private String projectId;
    private String tags;
    private String title;

    @Override
    public void onStart(ISuite suite) {

        projectId = suite.getParameter("projectId");
        tags = suite.getParameter("tag");
        title = suite.getParameter("runTitle");

        // Forward deviceSerial from testng.xml to CloudConfig
        String deviceSerial = suite.getParameter("deviceSerial");
        CloudConfig.setDeviceSerialFromTestNG(deviceSerial);

        long start = System.currentTimeMillis() / 1000;
        TestContext.setStartTime(start);

        // ── Auto-start ForgeDriver companion APK (like Maestro) ──────────────
        // Installs APK if not present, forwards port, starts server — transparent to tests.
        // Falls back gracefully if APK not available (Appium used instead).
        if (deviceSerial != null && !deviceSerial.isBlank()) {
            try {
                ForgeDriverManager.start(deviceSerial);
            } catch (Exception e) {
                System.out.println("[TestListener] ⚠️  ForgeDriver not started (Appium fallback): " + e.getMessage());
            }
        }

        // ── TestSigma: collect all testCaseIds from YAML files and create a run ──
        System.out.println("[TestSigma] Scanning for test cases to register in run...");

        List<String> allTestCaseIds = new ArrayList<>();

        File root = new File("src/test/java/com/popclub/androidTests");
        collectTestCaseIds(root, allTestCaseIds);

        if (allTestCaseIds.isEmpty()) {
            System.out.println("[TestSigma] No testCaseIds found — skipping run creation.");
            TestContext.setRunId(null);
            return;
        }

        System.out.println("[TestSigma] Found " + allTestCaseIds.size() + " test case(s): " + allTestCaseIds);

        try {
            List<String> uuidList = new ArrayList<>();
            for (String id : allTestCaseIds) {
                if (id.startsWith("PO-")) {
                    String uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, id);
                    uuidList.add(uuid);
                } else {
                    uuidList.add(id);
                }
            }

            String runTitle = (title != null && !title.isBlank()) ? title : "Forge Run";
            String runTags  = (tags  != null && !tags.isBlank())  ? tags  : "Automated";

            String runId = TestSigmaClient.createRun(runTitle, projectId, runTags, uuidList);
            TestContext.setRunId(runId);

            if (runId != null) {
                System.out.println("[TestSigma] ✅ Run created: " + runTitle + " (" + runId + ")");
            } else {
                System.out.println("[TestSigma] ⚠️  Run creation returned null — results will not be reported.");
            }

        } catch (Exception e) {
            System.err.println("[TestSigma] ⚠️  Failed to create run: " + e.getMessage());
            TestContext.setRunId(null);
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        // Stop ForgeDriver server on device
        try {
            String deviceSerial = suite.getParameter("deviceSerial");
            if (deviceSerial != null && !deviceSerial.isBlank()) {
                ForgeDriverManager.stop(deviceSerial);
            }
        } catch (Exception ignored) {}

        if (TestContext.getRunId() == null) return;
        try {
            TestSigmaClient.updateRunStatus(projectId, TestContext.getRunId(), RunStatus.FINISHED);
            System.out.println("[TestSigma] ✅ Run marked FINISHED: " + TestContext.getRunId());
        } catch (Exception e) {
            System.err.println("[TestSigma] ⚠️  Failed to mark run FINISHED: " + e.getMessage());
        } finally {
            TestContext.clear();
        }
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        stopVideoQuietly(result.getName() + "_passed");
        updateStatus(result, TestCaseStatus.PASSED);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        DeviceKeepAlive.stop();
        AppiumDriver driver = DriverManager.getDriver();
        if (driver == null) return;

        // 1. Final screenshot at the exact moment of failure
        String safeName = result.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        File screenshot = ScreenshotUtil.capture("FAIL_" + safeName);
        System.out.println("[TestListener] 📸 Failure screenshot saved.");

        // 2. Stop and save the full video recorded since launchApp
        try {
            File video = VideoUtil.stopAndSave(driver, "FAIL_" + safeName);
            if (video != null) {
                System.out.println("[TestListener] 🎥 Failure video saved: " + video.getPath());
            }
        } catch (Exception e) {
            System.out.println("[TestListener] ⚠️  Video save failed: " + e.getMessage());
        }

        // 3. Analyze screen for missing qaTestTags and write report for popdroid
        String screenshotPath = screenshot != null ? screenshot.getAbsolutePath() : null;
        String failingElement = TestContext.getFailingElement();
        FailureTagReporter.report(driver, failingElement, safeName, screenshotPath);

        // 4. Report FAILED status to TestSigma
        updateStatus(result, TestCaseStatus.FAILED);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        stopVideoQuietly(result.getName() + "_skipped");
        updateStatus(result, TestCaseStatus.SKIPPED);
    }

    /**
     * Recursively collects all testCaseIds from YAML files under {@code dir}.
     * Files without a testCaseIds field are silently skipped.
     */
    private void collectTestCaseIds(File dir, List<String> result) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        java.util.Arrays.sort(entries);
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectTestCaseIds(entry, result);
            } else if (entry.getName().endsWith(".yaml")) {
                try {
                    TestCase tc = YamlParser.parse(entry.getPath());
                    if (tc.testCaseIds != null && !tc.testCaseIds.isEmpty()) {
                        result.addAll(tc.testCaseIds);
                    }
                } catch (Exception e) {
                    System.out.println("[TestSigma] Skipped unreadable YAML: " + entry.getName());
                }
            }
        }
    }

    /** Stop recording without saving — cleans up the Appium buffer on pass/skip. */
    private void stopVideoQuietly(String label) {
        DeviceKeepAlive.stop();
        try {
            AppiumDriver driver = DriverManager.getDriver();
            if (driver != null) {
                VideoUtil.stopAndSave(driver, label);
            }
        } catch (Exception ignored) {
            // Video may not have been started — safe to swallow
        }
    }

    // ===============================
    // UPDATE STATUS
    // ===============================
    private void updateStatus(ITestResult result, TestCaseStatus status) {

        if (TestContext.getRunId() == null) return;

        List<String> testCases = TestContext.getTestCaseIds();
        if (testCases == null || testCases.isEmpty()) return;

        try {
            long duration = (result.getEndMillis() - result.getStartMillis()) / 1000;

            // Build a map of (humanId / uuid) → test_case_run id for this run
            Map<String, String> runCaseMap = TestSigmaClient.getTestCaseRunMap(TestContext.getRunId());
            if (runCaseMap.isEmpty()) {
                System.out.println("[TestSigma] Run case map empty — retrying in 2s...");
                Thread.sleep(2000);
                runCaseMap = TestSigmaClient.getTestCaseRunMap(TestContext.getRunId());
            }

            for (String id : testCases) {

                String uuid = id;
                if (id.startsWith("PO-")) {
                    uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, id);
                }

                System.out.println("[TestSigma] Reporting " + id + " (" + uuid + ") → " + status);

                // Primary update via test_runs endpoint
                TestSigmaClient.updateTestCaseRun(
                        projectId, TestContext.getRunId(), uuid,
                        status.id(), TestSigmaConfig.userId(), duration);

                // Fallback: override API using test_case_run id
                String runCaseId = runCaseMap.getOrDefault(id, runCaseMap.get(uuid));
                if (runCaseId != null) {
                    TestSigmaClient.updateTestCaseStatus(TestContext.getRunId(), runCaseId, status);
                    System.out.println("[TestSigma] ✅ " + id + " marked " + status);
                } else {
                    System.out.println("[TestSigma] ⚠️  No test_case_run id found for: " + id);
                }
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Never let TestSigma reporting affect the test result itself
            System.err.println("[TestSigma] ⚠️  Failed to update status for "
                    + result.getName() + ": " + e.getMessage());
        }
    }
}
