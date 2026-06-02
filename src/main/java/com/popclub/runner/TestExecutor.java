package com.popclub.runner;

import com.popclub.core.*;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import com.popclub.model.TestCase;
import com.popclub.mobile.actions.Action;
import com.popclub.mobile.actions.ActionFactory;
import io.appium.java_client.AppiumDriver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TestExecutor {

    public void execute(TestCase testCase) {

        int stepIndex = 1;
        int maxRetry = testCase.retry > 0 ? testCase.retry : 2;

        // Platform
        TestContext.setPlatform(testCase.platform);
        TestContext.setNoReset(testCase.noReset);
        TestContext.setLoginRequired(testCase.loginRequired);

        System.out.println("Starting Test: " + testCase.testName);

        // DECIDE MODE
        List<String> allTestCaseIds = new ArrayList<>();

        if (testCase.testCaseIds != null && !testCase.testCaseIds.isEmpty()) {

            //  YAML MODE
            TestContext.setExecutionMode("YAML");
            allTestCaseIds.addAll(testCase.testCaseIds);

        } else {

            //  STEP MODE
            TestContext.setExecutionMode("STEP");

            for (Step step : testCase.steps) {
                if (step.testCaseId != null) {
                    allTestCaseIds.add(step.testCaseId);
                }
            }
        }

        TestContext.setTestCaseIds(allTestCaseIds);

        System.out.println("Execution Mode: " + TestContext.getExecutionMode());

        // Load elements
        if (testCase.features != null && !testCase.features.isEmpty()) {
            ElementRepository.loadMultiple(testCase.features);
        }

        for (Step step : testCase.steps) {

            // Resolve locators — priority: element → locator → resourceId → text → bounds/xy
            if (step.element != null) {

                step.locators = ElementRepository.getLocators(
                        step.element,
                        TestContext.getPlatform()
                );

            } else if (step.locator != null) {

                // accessibilityId (qaTestTag) — best case
                Locator locator = new Locator();
                locator.type  = step.locator.contains(":id/") ? "id" : "accessibilityId";
                locator.value = step.locator;
                step.locators = List.of(locator);

            } else if (step.text != null) {

                // visible text fallback
                Locator locator = new Locator();
                locator.type  = "text";
                locator.value = step.text;
                step.locators = List.of(locator);

            } else if (step.bounds != null) {

                // bounds fallback — TapAction handles coordinate tap
                Locator locator = new Locator();
                locator.type  = "bounds";
                locator.value = step.bounds;
                step.locators = List.of(locator);

            }
            // x/y fallback is handled directly in TapAction when locators is null

            int attempt = 0;
            boolean success = false;

            while (attempt <= maxRetry && !success) {

                try {

                    LoggerUtil.step(
                            "[" + stepIndex + "] " + step.action
                    );

                    Action action = ActionFactory.get(step.action);
                    action.perform(step);

                    // 🔥 START VIDEO
                    if ("launchApp".equalsIgnoreCase(step.action)
                            && TestContext.isFreshLaunch()) {

                        AppiumDriver driver = DriverManager.getDriver();

                        VideoUtil.startRecording(driver);

                        Thread.sleep(3000);

                        SystemPopupHandler.handle(driver);

                        TestContext.setFreshLaunch(false);
                    }

                    success = true;

                    LoggerUtil.pass("Step " + stepIndex + " passed");

                    // STEP MODE ONLY
                    if ("STEP".equals(TestContext.getExecutionMode())) {

                        if (step.testCaseId != null) {
                            TestContext.markPassed(step.testCaseId);
                        }
                    }

                } catch (Exception e) {

                    attempt++;

                    if (attempt > maxRetry) {

                        LoggerUtil.fail(
                                "Step " + stepIndex +
                                        " failed: " + e.getMessage()
                        );

                        ScreenshotUtil.capture("step_" + stepIndex);

                        if ("STEP".equals(TestContext.getExecutionMode())) {

                            if (step.testCaseId != null) {
                                TestContext.markFailed(step.testCaseId);
                            }
                        }

                        throw new RuntimeException(e);
                    }

                    System.out.println("Retry step " + stepIndex +
                            " attempt " + attempt);
                }
            }

            stepIndex++;
        }

        // STOP VIDEO
        AppiumDriver driver = DriverManager.getDriver();
        File video = VideoUtil.stopAndSave(driver, testCase.testName);
        TestContext.setVideoFile(video);

        // YAML MODE RESULT
        if ("YAML".equals(TestContext.getExecutionMode())) {

            for (String tc : allTestCaseIds) {
                TestContext.markPassed(tc);
            }
        }

        // 🔥 BLOCKED
        TestContext.markBlockedIfMissing(allTestCaseIds);

        System.out.println("Test Execution Completed");
    }
}