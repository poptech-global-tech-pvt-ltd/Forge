package com.popclub.model;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TestCase {
    public String testName;
    public String platform;
    public List<String> features;
    public List<Step> steps;
    public List<String> tags;   // ✅ ADD THIS
    public int retry;


    @JsonProperty("testCaseIds")   // 🔥 ADD THIS
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<String> testCaseIds;

    public boolean noReset = false;

    /**
     * loginRequired (default: true)
     * Set to false to skip all login steps — the app is launched but no OTP
     * login is attempted.  Useful for tests that don't need an authenticated
     * session (e.g. browse-only, onboarding, deep-link tests).
     */
    public boolean loginRequired = true;

    public String mapping = "TEST";

    /**
     * Default poll timeout (seconds) for every step in this test.
     * Individual steps can override with their own {@code timeout:} field.
     * Mirrors Maestro's per-flow timeout setting (Maestro default = 60s).
     * Forge default = 30s — matches current WaitUtil hardcoded value.
     */
    public int defaultTimeout = 30;

    /** Steps executed before the main test body. */
    public List<Step> onFlowStart;
    /** Steps executed after the main test body (success path). */
    public List<Step> onFlowComplete;

    /** Set at load time by TestRunnerTest — not in YAML, used for self-healing write-back. */
    public transient String sourceFile;
}