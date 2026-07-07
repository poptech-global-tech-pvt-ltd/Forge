package com.popclub.testsigma;

import com.popclub.core.TestContext;
import com.popclub.model.TestCase;
import com.popclub.parser.YamlParser;
import org.testng.*;

import java.io.File;
import java.util.*;

/**
 * TestNG listener that reports YAML-driven Forge test results to TestSigma.
 *
 * Flow:
 *  1. onStart(ISuite)  — scan YAML files, resolve testCaseIds → UUIDs, create a TestSigma run
 *  2. onTestSuccess / onTestFailure — read TestContext.getResults() and update each test case in TestSigma
 *  3. onFinish(ISuite) — mark run FINISHED
 *
 * Enable:  add <listener class-name="com.popclub.testsigma.TestSigmaYamlListener"/> to testng.xml
 * Disable: pass -Dtestsigma.report.off=true on the mvn command line
 */
public class TestSigmaYamlListener implements ISuiteListener, ITestListener {

    private static final String ANDROID_TESTS_ROOT = "src/test/java/com/popclub/androidTests";
    private static final int MAX_RUN_CREATE_RETRIES = 20;

    private boolean enabled;
    private String runId;
    private String projectId;

    /** humanId (e.g. "PO-15308") → TestSigma UUID */
    private final Map<String, String> humanIdToUuid = new LinkedHashMap<>();

    /** testCaseRunId map: UUID → testCaseRun id (needed for updateTestCaseRun) */
    private Map<String, String> testCaseRunMap = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Suite start — scan YAMLs, create TestSigma run
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onStart(ISuite suite) {
        enabled = !Boolean.parseBoolean(System.getProperty("testsigma.report.off", "false"));
        if (!enabled) {
            System.out.println("[TestSigma] Reporting disabled (-Dtestsigma.report.off=true)");
            return;
        }

        // Single-file runs shouldn't create a TestSigma run either — only a group of
        // tests does: a Forge UI "Run Folder" batch (-DbatchRun=true), or a tag-based
        // run (-Dtag=..., e.g. in CI/CD), since tags are themselves a grouping
        // mechanism. See TestListener.onStart for the full rationale.
        String tagParam = System.getProperty("tag", suite.getParameter("tag"));
        boolean tagRun = tagParam != null && !tagParam.isEmpty();
        if (!Boolean.parseBoolean(System.getProperty("batchRun", "false")) && !tagRun) {
            System.out.println("[TestSigma] Single-file run — skipping TestSigma run creation (YAML listener).");
            enabled = false;
            return;
        }

        try {
            projectId = TestSigmaConfig.projectId();

            // 1. Collect testCaseIds from YAML files that will run
            List<String> humanIds = collectTestCaseIds(suite);

            if (humanIds.isEmpty()) {
                System.out.println("[TestSigma] No testCaseIds found in YAML files — skipping run creation");
                enabled = false;
                return;
            }

            System.out.println("[TestSigma] Resolving " + humanIds.size() + " test case IDs…");

            // 2. Resolve humanId → UUID
            for (String humanId : humanIds) {
                try {
                    String uuid = TestSigmaClient.getTestCaseIdByHumanId(projectId, humanId);
                    humanIdToUuid.put(humanId, uuid);
                    System.out.println("[TestSigma]   " + humanId + " → " + uuid);
                } catch (Exception e) {
                    System.out.println("[TestSigma]   WARNING: Could not resolve " + humanId + ": " + e.getMessage());
                }
            }

            if (humanIdToUuid.isEmpty()) {
                System.out.println("[TestSigma] No UUIDs resolved — skipping run creation");
                enabled = false;
                return;
            }

            // 3. Create run
            // Title: use -DrunTitle or suite param "runTitle", fall back to testsigma.properties prefix
            String runTitle = System.getProperty("runTitle");
            if (runTitle == null || runTitle.isEmpty()) {
                try { runTitle = suite.getParameter("runTitle"); } catch (Exception ignored) {}
            }
            String prefix = (runTitle != null && !runTitle.isEmpty())
                    ? runTitle
                    : TestSigmaConfig.runTitlePrefix();

            String tags   = TestSigmaConfig.runTags();
            List<String> uuids = new ArrayList<>(humanIdToUuid.values());

            int nextNumber = getNextRunNumber(projectId, prefix);
            String title   = null;
            int attempts   = 0;

            while (runId == null && attempts < MAX_RUN_CREATE_RETRIES) {
                title = prefix + " #" + nextNumber;
                runId = TestSigmaClient.createRun(title, projectId, tags, uuids);
                if (runId == null) nextNumber++;
                attempts++;
            }

            if (runId == null) {
                System.out.println("[TestSigma] Failed to create run after " + MAX_RUN_CREATE_RETRIES + " attempts");
                enabled = false;
                return;
            }

            System.out.println("[TestSigma] Run created: " + title + " (" + runId + ")");

            // 4. Build testCaseRun map (UUID / humanId → testCaseRun id)
            testCaseRunMap = TestSigmaClient.getTestCaseRunMap(projectId, runId);

        } catch (Exception e) {
            System.out.println("[TestSigma] Initialization failed: " + e.getMessage());
            e.printStackTrace();
            enabled = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-test reporting
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onTestSuccess(ITestResult result) {
        reportResults(result);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        reportResults(result);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        reportResults(result);
    }

    /**
     * Read TestContext.getResults() (populated by TestExecutor per-step testCaseId tracking)
     * and push each result to TestSigma.
     */
    private void reportResults(ITestResult result) {
        if (!enabled || runId == null) return;

        long duration = result.getEndMillis() - result.getStartMillis();
        TestCaseStatus overallStatus = result.getStatus() == ITestResult.SUCCESS
                ? TestCaseStatus.PASSED : TestCaseStatus.FAILED;

        // Always report top-level testCaseIds with the overall pass/fail
        Object[] params = result.getParameters();
        if (params != null && params.length > 0 && params[0] instanceof com.popclub.model.TestCase) {
            com.popclub.model.TestCase tc = (com.popclub.model.TestCase) params[0];
            if (tc.testCaseIds != null) {
                for (String raw : tc.testCaseIds) {
                    for (String humanId : raw.split(",")) {
                        humanId = humanId.trim();
                        if (!humanId.isEmpty()) updateOne(humanId, overallStatus, duration);
                    }
                }
            }
        }

        // Additionally report per-step testCaseIds with their individual pass/fail
        Map<String, String> results = TestContext.getResults();
        if (results == null || results.isEmpty()) return;

        for (Map.Entry<String, String> entry : results.entrySet()) {
            String rawId = entry.getKey();
            TestCaseStatus status = "FAILED".equals(entry.getValue())
                    ? TestCaseStatus.FAILED : TestCaseStatus.PASSED;
            // rawId may be "PO-15308,PO-11057" if step testCaseId had multiple IDs
            for (String humanId : rawId.split(",")) {
                humanId = humanId.trim();
                if (!humanId.isEmpty()) updateOne(humanId, status, duration);
            }
        }
    }

    private void updateOne(String humanId, TestCaseStatus status, long duration) {
        try {
            String uuid = humanIdToUuid.get(humanId);
            if (uuid == null) {
                System.out.println("[TestSigma] No UUID for " + humanId + " — skipping update");
                return;
            }

            TestSigmaClient.updateTestCaseRun(
                    projectId, runId, uuid,
                    status.id(),
                    TestSigmaConfig.userId(),
                    duration
            );

            System.out.println("[TestSigma] " + humanId + " → " + status.name());

        } catch (Exception e) {
            System.out.println("[TestSigma] Failed to update " + humanId + ": " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Suite finish
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onFinish(ISuite suite) {
        if (!enabled || runId == null) return;
        try {
            TestSigmaClient.updateRunStatus(projectId, runId, RunStatus.FINISHED);
            System.out.println("[TestSigma] Run marked FINISHED: " + runId);
        } catch (Exception e) {
            System.out.println("[TestSigma] Failed to finish run: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scan YAML files (respecting -DtestFile filter) and collect all testCaseIds.
     * Also collects step-level testCaseId fields.
     */
    private List<String> collectTestCaseIds(ISuite suite) {
        String testFileParam = System.getProperty("testFile");
        if (testFileParam == null || testFileParam.isEmpty()) {
            // Try suite parameter
            try {
                testFileParam = suite.getParameter("testFile");
            } catch (Exception ignored) {}
        }

        List<File> yamlFiles = new ArrayList<>();
        File root = new File(ANDROID_TESTS_ROOT);

        if (testFileParam != null && !testFileParam.isEmpty()) {
            // Only scan specified files
            Map<String, File> index = new HashMap<>();
            collectYamlFiles(root, index);
            for (String name : testFileParam.split(",")) {
                File f = index.get(name.trim().toLowerCase());
                if (f != null) yamlFiles.add(f);
            }
        } else {
            // Scan everything
            Map<String, File> index = new LinkedHashMap<>();
            collectYamlFiles(root, index);
            yamlFiles.addAll(index.values());
        }

        Set<String> ids = new LinkedHashSet<>();
        for (File f : yamlFiles) {
            try {
                TestCase tc = YamlParser.parse(f.getPath());
                // Top-level testCaseIds — split each entry on comma in case user wrote "PO-1,PO-2" as one item
                if (tc.testCaseIds != null) {
                    for (String raw : tc.testCaseIds) {
                        for (String id : raw.split(",")) {
                            String trimmed = id.trim();
                            if (!trimmed.isEmpty()) ids.add(trimmed);
                        }
                    }
                }
                // Step-level testCaseId
                if (tc.steps != null) {
                    for (com.popclub.model.Step step : tc.steps) {
                        if (step.testCaseId != null) ids.add(step.testCaseId.trim());
                    }
                }
            } catch (Exception e) {
                System.out.println("[TestSigma] Could not parse " + f.getName() + ": " + e.getMessage());
            }
        }

        return new ArrayList<>(ids);
    }

    private void collectYamlFiles(File dir, Map<String, File> result) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        Arrays.sort(entries);
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectYamlFiles(entry, result);
            } else if (entry.getName().endsWith(".yaml")) {
                result.put(entry.getName().toLowerCase(), entry);
            }
        }
    }

    private int getNextRunNumber(String projectId, String prefix) {
        try {
            List<Map<String, Object>> runs = TestSigmaClient.getTestRuns(projectId, prefix);
            int max = 0;
            for (Map<String, Object> run : runs) {
                String title = (String) run.get("title");
                if (title != null && title.startsWith(prefix + " #")) {
                    try {
                        int num = Integer.parseInt(title.substring((prefix + " #").length()).trim());
                        if (num > max) max = num;
                    } catch (NumberFormatException ignored) {}
                }
            }
            return max + 1;
        } catch (Exception e) {
            return 1;
        }
    }
}
