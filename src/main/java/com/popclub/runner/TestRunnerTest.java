package com.popclub.runner;

import com.popclub.core.TestContext;
import com.popclub.mobile.driver.AppiumDriverManager;
import com.popclub.mobile.driver.AppiumServerManager;
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

        File folder = new File("src/test/resources/testdata");

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yaml"));

        if (files == null || files.length == 0) {
            throw new RuntimeException("No YAML files found in testdata folder");
        }

        String tagParam      = context.getCurrentXmlTest().getParameter("tag");
        String testFileParam = context.getCurrentXmlTest().getParameter("testFile");

        // Build an ordered name list from the comma-separated testFile param
        List<String> orderedNames = new ArrayList<>();
        if (testFileParam != null && !testFileParam.isEmpty()) {
            for (String name : testFileParam.split(",")) {
                orderedNames.add(name.trim().toLowerCase());
            }
        }

        // Index files by lowercase name for fast lookup
        Map<String, File> fileIndex = new java.util.HashMap<>();
        for (File file : files) {
            fileIndex.put(file.getName().toLowerCase(), file);
        }

        // Collect in declared order (or natural order if no filter)
        List<File> orderedFiles = new ArrayList<>();
        if (!orderedNames.isEmpty()) {
            for (String name : orderedNames) {
                File f = fileIndex.get(name);
                if (f != null) {
                    orderedFiles.add(f);
                } else {
                    System.out.println("Warning: testFile '" + name + "' not found in testdata folder");
                }
            }
        } else {
            orderedFiles.addAll(List.of(files));
        }

        List<Object[]> filtered = new ArrayList<>();

        for (File file : orderedFiles) {

            // Skip files not in the filter (already handled by orderedFiles, but kept for tag-only runs)
            if (!orderedNames.isEmpty() && !orderedNames.contains(file.getName().toLowerCase())) {
                System.out.println("Skipping: " + file.getName() + " (not in testFile filter)");
                continue;
            }

            TestCase testCase = YamlParser.parse(file.getPath());

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
            throw new RuntimeException("No tests matched given tags");
        }

        return filtered.toArray(new Object[0][0]);
    }

    @Test(dataProvider = "testData")
    public void runTest(TestCase testCase) {

        try {
            TestContext.setTestCaseIds(testCase.testCaseIds);
            new TestExecutor().execute(testCase);
        } catch (Exception e) {
            throw new RuntimeException("Test failed: " + testCase.testName, e);
        } finally {
            //  ALWAYS CLEAN DRIVER
            AppiumDriverManager.quitDriver();
        }
    }

    // TODO: Re-enable once TestSigma token is refreshed
//    @Test
//    public void loginTest() {
//        stepTexts.forEach(System.out::println);
//    }



    @AfterSuite
    public void tearDown() {
        System.out.println("Stopping all Appium servers...");
        AppiumServerManager.stopAll();
    }
}