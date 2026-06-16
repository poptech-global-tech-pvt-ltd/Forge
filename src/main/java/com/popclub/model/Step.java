package com.popclub.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.popclub.core.Locator;
import java.util.List;
import java.util.Map;

public class Step {
    public String action;
    public String locator;      // accessibilityId (qaTestTag) — first priority
    public String element;      // ElementRepository key
    public String text;         // visible text fallback
    public String resourceId;   // resource-id fallback
    public String bounds;       // "[x1,y1][x2,y2]" fallback
    public int    x;            // coordinate tap fallback
    public int    y;
    public String value;
    public String direction;    // for scroll/swipe
    public List<Locator> locators;
    public int retry;
    public String testCaseId;

    // ── Conditional logic (ifPresent / ifNotPresent / ifVarEquals …) ────────────
    /** Nested steps executed when the condition is met (then-branch). */
    public List<Step> steps;
    /** Nested steps executed when the condition is NOT met (else-branch). */
    @JsonProperty("else")
    public List<Step> elseBranch;

    // ── Variable capture ──────────────────────────────────────────────────────
    /** Key name to store captured text under (used by captureText). */
    public String variable;

    // ── Reusable flow call ────────────────────────────────────────────────────
    /** Flow file name (without .yaml) in the flows/ directory. */
    public String flow;
    /** Optional key-value pairs injected as variables before running the flow. */
    public Map<String, String> params;

    // ── API call ──────────────────────────────────────────────────────────────
    /** Full URL to call (supports ${var} interpolation). */
    public String url;
    /** HTTP method: GET (default), POST, PUT, DELETE, PATCH. */
    public String method;
    /** Request body (for POST/PUT). Supports ${var} interpolation. */
    public String body;
    /** Request headers as key-value pairs. */
    public Map<String, String> headers;
    /**
     * JSON field extraction map: variableName → dot-path (e.g. "data.user.id").
     * Each matched value is stored as a TestContext variable.
     */
    public Map<String, String> extract;
}
