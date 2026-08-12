package com.popclub.listener;

import com.popclub.ai.FailureTagReporter;
import com.popclub.core.ScreenshotUtil;
import com.popclub.core.TestContext;
import com.popclub.core.TestLogCapture;
import com.popclub.core.VideoUtil;
import com.popclub.android.cloud.CloudConfig;
import com.popclub.android.driver.DeviceKeepAlive;
import com.popclub.android.driver.DriverManager;
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

        TestSigmaSessionManager.refreshSessionCookie();

        projectId = suite.getParameter("projectId");
        tags = suite.getParameter("tag");
        title = suite.getParameter("runTitle");

        CloudConfig.setDeviceSerialFromTestNG(suite.getParameter("deviceSerial"));

        long start = System.currentTimeMillis() / 1000;
        TestContext.setStartTime(start);

        String tagParam = System.getProperty("tag", suite.getParameter("tag"));
        boolean tagRun = tagParam != null && !tagParam.isEmpty();
        boolean batchRun = Boolean.parseBoolean(System.getProperty("batchRun", "false")) || tagRun;
        if (!batchRun) {
            System.out.println("[TestSigma] Single-file run — skipping TestSigma run creation "
                    + "(pass -DbatchRun=true, use -Dtag=..., or use Forge UI's \"Run Folder\", to report to TestSigma).");
            TestContext.setRunId(null);
            return;
        }

        String existingRunId = System.getProperty("existingRunId");
        if (existingRunId != null && !existingRunId.isBlank()) {
            TestContext.setRunId(existingRunId);
            System.out.println("[TestSigma] Joining existing run: " + existingRunId);
            return;
        }

        System.out.println("[TestSigma] Scanning for test cases to register in run...");

        java.util.Set<String> onlyFiles = new java.util.HashSet<>();
        String testFileParam = System.getProperty("testFile");
        if (testFileParam == null || testFileParam.isEmpty()) {
            try { testFileParam = suite.getParameter("testFile"); } catch (Exception ignored) {}
        }
        if (testFileParam != null && !testFileParam.isEmpty()) {
            for (String name : testFileParam.split(",")) {
                String n = name.trim().toLowerCase();
                if (!n.isEmpty()) onlyFiles.add(n);
            }
        }

        List<String> allTestCaseIds = new ArrayList<>();

        File root = new File("src/test/java/com/popclub/androidTests");
        collectTestCaseIds(root, allTestCaseIds, onlyFiles);

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
                    try {
                        String uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, id);
                        if (uuid != null) {
                            uuidList.add(uuid);
                        } else {
                            System.out.println("[TestSigma] ⚠️  Skipping id not found in TestSigma: " + id);
                        }
                    } catch (Exception ex) {
                        System.out.println("[TestSigma] ⚠️  Skipping unresolvable id " + id + ": " + ex.getMessage());
                    }
                } else {
                    uuidList.add(id);
                }
            }

            if (uuidList.isEmpty()) {
                System.out.println("[TestSigma] No resolvable test case ids — skipping run creation.");
                TestContext.setRunId(null);
                return;
            }

            String baseTitle = (title != null && !title.isBlank()) ? title : "Forge Run";
            java.text.SimpleDateFormat istFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            istFormat.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
            String runTitle = baseTitle + " - " + istFormat.format(new java.util.Date());
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
        if (TestContext.getRunId() == null) return;

        // Runs are intentionally left ACTIVE — no clear requirement yet for when
        // a run should be closed, so we don't auto-close here.
        TestContext.clear();
    }

    @Override
    public void onTestStart(ITestResult result) {
        TestLogCapture.start(resolveTestName(result));
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        stopVideoQuietly(resolveTestName(result) + "_passed");
        updateStatus(result, TestCaseStatus.PASSED);
        TestLogCapture.stop();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        DeviceKeepAlive.stop();

        String safeName = resolveTestName(result).replaceAll("[^a-zA-Z0-9_-]", "_");
        File screenshot = null;
        File video = null;

        AppiumDriver driver = DriverManager.getDriver();
        if (driver != null) {
            screenshot = ScreenshotUtil.capture("FAIL_" + safeName);
            System.out.println("[TestListener] 📸 Failure screenshot saved.");

            try {
                video = VideoUtil.stopAndSave(driver, "FAIL_" + safeName);
                if (video != null) {
                    System.out.println("[TestListener] 🎥 Failure video saved: " + video.getPath());
                }
            } catch (Exception e) {
                System.out.println("[TestListener] ⚠️  Video save failed: " + e.getMessage());
            }

            String screenshotPath = screenshot != null ? screenshot.getAbsolutePath() : null;
            String failingElement = TestContext.getFailingElement();
            FailureTagReporter.report(driver, failingElement, safeName, screenshotPath);
        }

        updateStatus(result, TestCaseStatus.FAILED);

        File log = TestLogCapture.stop();
        uploadAttachments(TestCaseStatus.FAILED, screenshot, video, log);

        postFailureLogComment(log);
    }

    private String resolveTestName(ITestResult result) {
        Object[] params = result.getParameters();
        if (params != null && params.length > 0 && params[0] instanceof TestCase) {
            TestCase tc = (TestCase) params[0];
            if (tc.testName != null && !tc.testName.isBlank()) return tc.testName;
        }
        return result.getName();
    }

    private void postFailureLogComment(File log) {
        String runId = TestContext.getRunId();
        if (runId == null || log == null || !log.exists()) return;

        List<String> testCases = TestContext.getTestCaseIds();
        if (testCases == null || testCases.isEmpty()) return;

        String logContent;
        try {
            logContent = java.nio.file.Files.readString(log.toPath());
        } catch (Exception e) {
            System.out.println("[TestSigma] Could not read log for comment: " + e.getMessage());
            return;
        }

        for (String id : testCases) {
            String uuid = id;
            if (id.startsWith("PO-")) {
                try {
                    uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, id);
                } catch (Exception e) {
                    System.out.println("[TestSigma] ⚠️  Could not resolve " + id + " for log comment: " + e.getMessage());
                    continue;
                }
            }
            if (uuid == null) continue;

            TestSigmaClient.addRunComment(runId, uuid, TestCaseStatus.FAILED.id(),
                    TestSigmaConfig.sessionUserId(), logContent);
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        stopVideoQuietly(resolveTestName(result) + "_skipped");
        updateStatus(result, TestCaseStatus.SKIPPED);
        TestLogCapture.stop();
    }

    private void uploadAttachments(TestCaseStatus status, File... files) {
        String runId = TestContext.getRunId();
        if (runId == null) return;

        List<String> testCases = TestContext.getTestCaseIds();
        if (testCases == null || testCases.isEmpty()) return;

        for (String id : testCases) {

            String uuid = id;
            if (id.startsWith("PO-")) {
                try {
                    uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, id);
                } catch (Exception e) {
                    System.out.println("[TestSigma] ⚠️  Could not resolve " + id
                            + " for attachment upload: " + e.getMessage());
                    continue;
                }
            }
            if (uuid == null) continue;

            for (File f : files) {
                if (f == null || !f.exists()) continue;
                try {
                    boolean uploaded = TestSigmaClient.uploadAttachment(
                            runId, uuid, status.id(), TestSigmaConfig.sessionUserId(),
                            "Forge automation — " + f.getName(), f);
                    if (uploaded) {
                        System.out.println("[TestSigma] 📎 Uploaded " + f.getName() + " → " + id);
                    }
                } catch (Exception e) {
                    System.out.println("[TestSigma] ⚠️  Attachment upload failed for "
                            + f.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void collectTestCaseIds(File dir, List<String> result, java.util.Set<String> onlyFiles) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        java.util.Arrays.sort(entries);
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectTestCaseIds(entry, result, onlyFiles);
            } else if (entry.getName().endsWith(".yaml")) {
                if (onlyFiles != null && !onlyFiles.isEmpty()
                        && !onlyFiles.contains(entry.getName().toLowerCase())) {
                    continue;
                }
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

    private void stopVideoQuietly(String label) {
        DeviceKeepAlive.stop();
        try {
            AppiumDriver driver = DriverManager.getDriver();
            if (driver != null) {
                VideoUtil.stopAndSave(driver, label);
            }
        } catch (Exception ignored) {
        }
    }

    private void updateStatus(ITestResult result, TestCaseStatus status) {

        if (TestContext.getRunId() == null) return;

        List<String> testCases = TestContext.getTestCaseIds();
        if (testCases == null || testCases.isEmpty()) return;

        try {
            long duration = (result.getEndMillis() - result.getStartMillis()) / 1000;

            Map<String, String> runCaseMap = TestSigmaClient.getTestCaseRunMap(projectId, TestContext.getRunId());
            for (int attempt = 0; runCaseMap.isEmpty() && attempt < 4; attempt++) {
                System.out.println("[TestSigma] Run case map empty (attempt " + (attempt + 1) + ") — retrying in 2s...");
                Thread.sleep(2000);
                runCaseMap = TestSigmaClient.getTestCaseRunMap(projectId, TestContext.getRunId());
            }

            for (String id : testCases) {

                String uuid = id;
                if (id.startsWith("PO-")) {
                    uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, id);
                }

                System.out.println("[TestSigma] Reporting " + id + " (" + uuid + ") → " + status);

                TestSigmaClient.updateTestCaseRun(
                        projectId, TestContext.getRunId(), uuid,
                        status.id(), TestSigmaConfig.userId(), duration);

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
            System.err.println("[TestSigma] ⚠️  Failed to update status for "
                    + result.getName() + ": " + e.getMessage());
        }
    }
}
