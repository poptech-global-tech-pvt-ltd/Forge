package com.popclub.core;

import java.io.File;
import java.util.*;

public class TestContext {

    private static ThreadLocal<String> platform = new ThreadLocal<>();

    // Must be shared across TestNG worker threads; `ThreadLocal` caused the
    // listener callbacks to read `null` and skip Testsigma status updates.
    private static String runId;
    private static ThreadLocal<Long> startTime = new ThreadLocal<>();
    private static ThreadLocal<Boolean> freshLaunch =
            ThreadLocal.withInitial(() -> false);
    private static ThreadLocal<String> testCaseId = new ThreadLocal<>();
    private static ThreadLocal<Map<String, String>> results =
            ThreadLocal.withInitial(HashMap::new);

    private static final ThreadLocal<List<String>> testCaseIds = new ThreadLocal<>();

    private static ThreadLocal<Set<String>> failedTestCases =
            ThreadLocal.withInitial(HashSet::new);

    private static ThreadLocal<String> executionMode = new ThreadLocal<>();
    private static ThreadLocal<Boolean> noReset = ThreadLocal.withInitial(() -> false);
    private static ThreadLocal<File> videoFile = new ThreadLocal<>();
    private static ThreadLocal<String> currentTestCase = new ThreadLocal<>();

    public static void setCurrentTestCase(String id) {
        currentTestCase.set(id);
    }

    public static String getCurrentTestCase() {
        return currentTestCase.get();
    }

    public static void setVideoFile(File file) {
        videoFile.set(file);
    }

    public static File getVideoFile() {
        return videoFile.get();
    }

    public static void setExecutionMode(String mode) {
        executionMode.set(mode);
    }

    public static String getExecutionMode() {
        return executionMode.get();
    }

    public static void setNoReset(boolean value) {
        noReset.set(value);
    }

    public static boolean isNoReset() {
        return noReset.get();
    }

    public static void markFailed(String testCaseId) {
        results.get().put(testCaseId, "FAILED");
        failedTestCases.get().add(testCaseId); // 🔥 track failure
    }

    public static Set<String> getFailedTestCases() {
        return failedTestCases.get();
    }

    public static void setTestCaseIds(List<String> ids) {
        testCaseIds.set(ids);
    }

    public static List<String> getTestCaseIds() {
        return testCaseIds.get();
    }



    public static void setPlatform(String p) {
        platform.set(p);
    }

    public static String getPlatform() {
        return platform.get();
    }
    public static void setRunId(String id) {
        runId = id;
    }

    public static String getRunId() {
        return runId;
    }



    // ---------------- FRESH LAUNCH ----------------

    public static void setFreshLaunch(boolean value) {
        freshLaunch.set(value);
    }

    public static boolean isFreshLaunch() {
        return freshLaunch.get();
    }

    // ---------------- TEST CASE ID ----------------

    public static void setTestCaseId(String id) {
        testCaseId.set(id);
    }

    public static String getTestCaseId() {
        return testCaseId.get();
    }



    public static void setStartTime(long time) {
        startTime.set(time);
    }

    public static long getStartTime() {
        return startTime.get();
    }

    // ---------------- RESULT TRACKING ----------------

    // PASS (only if not already failed)
    public static void markPassed(String testCaseId) {

        if (testCaseId == null) return;

        results.get().putIfAbsent(testCaseId, "PASSED");
    }


    public static Map<String, String> getResults() {
        return results.get();
    }

    //  Utility: mark blocked if not executed
    public static void markBlockedIfMissing(Iterable<String> allTestCaseIds) {

        if (allTestCaseIds == null) return;

        for (String tc : allTestCaseIds) {

            if (!results.get().containsKey(tc)) {
                results.get().put(tc, "BLOCKED");
            }
        }
    }

    // ---------------- CLEANUP ----------------

    public static void clear() {
        results.remove();
        platform.remove();
        freshLaunch.remove();
        testCaseId.remove();
        runId = null;
        videoFile.remove();
    }


}