package com.popclub.listener;

import com.popclub.core.TestContext;
import com.popclub.core.VideoUtil;
import com.popclub.mobile.cloud.CloudConfig;
import com.popclub.mobile.driver.DriverManager;
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
        CloudConfig.setDeviceSerialFromTestNG(suite.getParameter("deviceSerial"));

        long start = System.currentTimeMillis() / 1000;
        TestContext.setStartTime(start);

        // TODO: Re-enable TestSigma integration once token is refreshed
//        System.out.println("Creating Test Run...");
//
//        List<String> allTestCaseIds = new ArrayList<>();
//
//        File folder = new File("src/test/resources/testdata");
//        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yaml"));
//
//        if (files != null) {
//            for (File file : files) {
//                TestCase tc = YamlParser.parse(file.getPath());
//                if (tc.testCaseIds != null) {
//                    allTestCaseIds.addAll(tc.testCaseIds);
//                }
//            }
//        }
//
//        if (allTestCaseIds.isEmpty()) {
//            TestContext.setRunId(null);
//            return;
//        }
//
//        try {
//            List<String> uuidList = new ArrayList<>();
//            for (String id : allTestCaseIds) {
//                if (id.startsWith("PO-")) {
//                    String uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, id);
//                    uuidList.add(uuid);
//                } else {
//                    uuidList.add(id);
//                }
//            }
//            String runId = TestSigmaClient.createRun(title, projectId, tags, uuidList);
//            TestContext.setRunId(runId);
//            System.out.println("Run created: " + runId);
//        } catch (Exception e) {
//            System.err.println("[TestSigma] WARNING: Failed to create run. Error: " + e.getMessage());
//            TestContext.setRunId(null);
//        }
    }

    @Override
    public void onFinish(ISuite suite) {

        // TODO: Re-enable TestSigma integration once token is refreshed
//        if (TestContext.getRunId() == null) return;
//        TestSigmaClient.updateRunStatus(projectId, TestContext.getRunId(), RunStatus.FINISHED);
//        TestContext.clear();
//        System.out.println("Run completed");
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        // TODO: Re-enable TestSigma integration once token is refreshed
        // updateStatus(result, TestCaseStatus.PASSED);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        // TODO: Re-enable TestSigma integration once token is refreshed
        // updateStatus(result, TestCaseStatus.FAILED);

//        AppiumDriver driver = DriverManager.getDriver();
//        File video = VideoUtil.stopAndSave(driver, result.getName());
//        if (video == null) return;
//        List<String> testCases = TestContext.getTestCaseIds();
//        for (String id : testCases) {
//            String uuid = id;
//            if (id.startsWith("PO-")) {
//                uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, id);
//            }
//            TestSigmaClient.uploadAttachment(uuid, video);
//        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        // TODO: Re-enable TestSigma integration once token is refreshed
        // updateStatus(result, TestCaseStatus.SKIPPED);
    }

    // ===============================
    // UPDATE STATUS
    // ===============================
//    private void updateStatus(ITestResult result, TestCaseStatus status) {
//
//        if (TestContext.getRunId() == null) return;
//
//        List<String> testCases = TestContext.getTestCaseIds();
//        if (testCases == null || testCases.isEmpty()) return;
//
//        long duration = (result.getEndMillis() - result.getStartMillis()) / 1000;
//
//        Map<String, String> runCaseMap = TestSigmaClient.getTestCaseRunMap(TestContext.getRunId());
//        if (runCaseMap.isEmpty()) {
//            try {
//                System.out.println("Run case map empty; retrying fetch in 2s...");
//                Thread.sleep(2000);
//            } catch (InterruptedException ignored) {}
//            runCaseMap = TestSigmaClient.getTestCaseRunMap(TestContext.getRunId());
//        }
//
//        for (String id : testCases) {
//            String uuid = id;
//            if (id.startsWith("PO-")) {
//                uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, id);
//            }
//            System.out.println("ProjectId=" + projectId + " RunId=" + TestContext.getRunId());
//            System.out.println("Updating TestSigma: " + id + " -> " + uuid + " status=" + status);
//            TestSigmaClient.updateTestCaseRun(
//                    projectId, TestContext.getRunId(), uuid,
//                    status.id(), TestSigmaClient.USER_ID, duration
//            );
//            String runCaseId = runCaseMap.get(id);
//            if (runCaseId == null) runCaseId = runCaseMap.get(uuid);
//            if (runCaseId != null) {
//                TestSigmaClient.updateTestCaseStatus(TestContext.getRunId(), runCaseId, status);
//            } else {
//                System.out.println("Warning: No test_case_run id found for: " + id);
//            }
//        }
//    }
}
