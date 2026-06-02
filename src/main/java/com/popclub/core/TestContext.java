package com.popclub.core;

import com.popclub.clp.ClpSection;
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
    private static ThreadLocal<Boolean> noReset        = ThreadLocal.withInitial(() -> false);
    private static ThreadLocal<Boolean> loginRequired  = ThreadLocal.withInitial(() -> true);
    private static ThreadLocal<File> videoFile = new ThreadLocal<>();
    private static ThreadLocal<String> currentTestCase = new ThreadLocal<>();
    private static ThreadLocal<String> userToken = new ThreadLocal<>();

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

    public static void setLoginRequired(boolean value) {
        loginRequired.set(value);
    }

    public static boolean isLoginRequired() {
        return loginRequired.get();
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

    // ---------------- USER TOKEN (captured after login) ----------------

    public static void setUserToken(String token) { userToken.set(token); }

    public static String getUserToken() { return userToken.get(); }

    // ---------------- CLP DATA (fetched by verifyCLP, reusable by tapByText) ----------------

    /** Key = page name upper-case (HOME / SHOP / CARD), value = ordered section list. */
    private static ThreadLocal<Map<String, List<ClpSection>>> clpData =
            ThreadLocal.withInitial(HashMap::new);

    public static void setClpData(String page, List<ClpSection> sections) {
        clpData.get().put(page.toUpperCase(), sections);
    }

    public static List<ClpSection> getClpData(String page) {
        return clpData.get().getOrDefault(page.toUpperCase(), Collections.emptyList());
    }

    /**
     * Returns a flat list of ALL text strings (section titles, subtitles, item titles,
     * item subtitles) for the given page — ready for assertion or click lookup.
     */
    public static List<String> getAllClpTexts(String page) {
        List<String> texts = new ArrayList<>();
        for (ClpSection s : getClpData(page)) {
            if (!s.sectionTitle.isEmpty())    texts.add(s.sectionTitle);
            if (!s.sectionSubtitle.isEmpty()) texts.add(s.sectionSubtitle);
            texts.addAll(s.itemTitles);
            texts.addAll(s.itemSubtitles);
        }
        return texts;
    }

    /**
     * Sections that are banners — stored separately because banners may have no
     * section title but do carry tappable CTA buttons ("Explore", "Shop now").
     */
    private static ThreadLocal<Map<String, List<ClpSection>>> clpBanners =
            ThreadLocal.withInitial(HashMap::new);

    public static void setClpBanners(String page, List<ClpSection> banners) {
        clpBanners.get().put(page.toUpperCase(), banners);
    }

    public static List<ClpSection> getClpBanners(String page) {
        return clpBanners.get().getOrDefault(page.toUpperCase(), Collections.emptyList());
    }

    // ---------------- SCALAR STORE (captureText / assertStoredText) ----------------

    /**
     * Generic key-value store for values captured at runtime from the screen
     * (e.g. product titles, prices).  Used by captureText + assertStoredText actions.
     */
    private static ThreadLocal<Map<String, String>> scalarStore =
            ThreadLocal.withInitial(HashMap::new);

    public static void setScalarData(String key, String value) {
        scalarStore.get().put(key, value);
    }

    public static String getScalarData(String key) {
        return scalarStore.get().getOrDefault(key, "");
    }

    public static Map<String, String> getAllScalarData() {
        return Collections.unmodifiableMap(scalarStore.get());
    }

    // ---------------- CLEANUP ----------------

    public static void clear() {
        results.remove();
        platform.remove();
        freshLaunch.remove();
        testCaseId.remove();
        runId = null;
        videoFile.remove();
        clpData.remove();
        clpBanners.remove();
        scalarStore.remove();
    }


}