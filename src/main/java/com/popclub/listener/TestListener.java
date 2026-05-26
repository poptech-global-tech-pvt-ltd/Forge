package com.popclub.listener;

import com.popclub.core.TestContext;
import com.popclub.core.VideoUtil;
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

        long start = System.currentTimeMillis() / 1000;
        TestContext.setStartTime(start);

        System.out.println("Creating Test Run...");

        List<String> allTestCaseIds = new ArrayList<>();

        File folder = new File("src/test/resources/testdata");
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yaml"));

        if (files != null) {
            for (File file : files) {

                TestCase tc = YamlParser.parse(file.getPath());

                if (tc.testCaseIds != null) {
                    allTestCaseIds.addAll(tc.testCaseIds);
                }
            }
        }

        if (allTestCaseIds.isEmpty()) {
            TestContext.setRunId(null);
            return;
        }

        // PO -> UUID conversion
        List<String> uuidList = new ArrayList<>();

        for (String id : allTestCaseIds) {

            if (id.startsWith("PO-")) {

                String uuid =
                        TestSigmaClient.getTestCaseIdByHumanId(
                                projectId,
                                id
                        );

                uuidList.add(uuid);

            } else {
                uuidList.add(id);
            }
        }

        String runId = TestSigmaClient.createRun(
                title,
                projectId,
                tags,
                uuidList
        );

        TestContext.setRunId(runId);

        System.out.println("Run created: " + runId);
    }

    @Override
    public void onFinish(ISuite suite) {

        if (TestContext.getRunId() == null) return;

        TestSigmaClient.updateRunStatus(
                projectId,
                TestContext.getRunId(),
                RunStatus.FINISHED
        );

        TestContext.clear();

        System.out.println("Run completed");
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        updateStatus(result, TestCaseStatus.PASSED);
    }

    @Override
    public void onTestFailure(ITestResult result) {

        updateStatus(result, TestCaseStatus.FAILED);

        AppiumDriver driver = DriverManager.getDriver();
        File video = VideoUtil.stopAndSave(driver, result.getName());

        if (video == null) return;

        List<String> testCases = TestContext.getTestCaseIds();

        for (String id : testCases) {

            String uuid = id;

            if (id.startsWith("PO-")) {
                uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, id);
            }

            TestSigmaClient.uploadAttachment(
                    uuid,
                    video
            );
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        updateStatus(result, TestCaseStatus.SKIPPED);
    }

    // ===============================
    // UPDATE STATUS
    // ===============================
    private void updateStatus(ITestResult result,
                              TestCaseStatus status) {

        if (TestContext.getRunId() == null) return;

        List<String> testCases = TestContext.getTestCaseIds();

        if (testCases == null || testCases.isEmpty()) return;

        long duration =
                (result.getEndMillis() - result.getStartMillis()) / 1000;

        // Build a map of test case identifiers (human id and uuid) to test_case_run ids
        Map<String, String> runCaseMap =
                TestSigmaClient.getTestCaseRunMap(TestContext.getRunId());
        if (runCaseMap.isEmpty()) {
            try {
                System.out.println("Run case map empty; retrying fetch in 2s...");
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}
            runCaseMap = TestSigmaClient.getTestCaseRunMap(TestContext.getRunId());
        }

        for (String id : testCases) {

            String uuid = id;

            // PO -> UUID
            if (id.startsWith("PO-")) {
                uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, id);
            }

            System.out.println("ProjectId=" + projectId + " RunId=" + TestContext.getRunId());
            System.out.println(
                    "Updating TestSigma: "
                            + id + " -> " + uuid +
                            " status=" + status
            );

            TestSigmaClient.updateTestCaseRun(
                    projectId,
                    TestContext.getRunId(),
                    uuid,
                    status.id(),
                    TestSigmaConfig.userId(),
                    duration
            );

            // Fallback: also update via override API using test_case_run id, if available
            String runCaseId = runCaseMap.get(id);
            if (runCaseId == null) {
                runCaseId = runCaseMap.get(uuid);
            }
            if (runCaseId != null) {
                TestSigmaClient.updateTestCaseStatus(
                        TestContext.getRunId(),
                        runCaseId,
                        status
                );
            } else {
                System.out.println("Warning: No test_case_run id found for: " + id);
            }
        }
    }
}