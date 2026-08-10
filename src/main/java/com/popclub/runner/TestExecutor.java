package com.popclub.runner;

import com.popclub.core.*;
import com.popclub.heal.SelfHealingEngine;
import com.popclub.android.driver.DeviceKeepAlive;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import com.popclub.model.TestCase;
import com.popclub.android.actions.Action;
import com.popclub.android.actions.ActionFactory;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import com.popclub.core.LocatorUtil;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestExecutor {

    public void execute(TestCase testCase) {

        int stepIndex = 1;
        // retry: N in YAML means "total attempts per step"
        //   retry: 1 → 1 attempt, no retry
        //   retry: 2 → 2 attempts, 1 retry
        //   retry: 0 or unset → default 3 attempts (2 retries)
        // maxRetry = total - 1 (used as while(attempt <= maxRetry))
        int maxRetry = testCase.retry > 0 ? testCase.retry - 1 : 2;

        // fromStep: skip all steps before this index (1-based). When set, noReset
        // is forced so the running app is NOT killed/restarted — the device must
        // already be at the correct screen for step N.
        int fromStep = 1;
        String fromStepProp = System.getProperty("fromStep");
        if (fromStepProp != null && !fromStepProp.isEmpty()) {
            try {
                fromStep = Integer.parseInt(fromStepProp.trim());
                if (fromStep < 1) fromStep = 1;
            } catch (NumberFormatException ignored) {}
        }
        boolean resumeMode = fromStep > 1;

        // Platform
        TestContext.setPlatform(testCase.platform);
        // In resume mode, force noReset so Appium doesn't reinstall/reset the app
        TestContext.setNoReset(testCase.noReset || resumeMode);
        TestContext.setResumeMode(resumeMode);
        TestContext.setLoginRequired(testCase.loginRequired);
        TestContext.setTestSourceFile(testCase.sourceFile);

        // Zero-wait intelligence: publish the test-level default timeout so every
        // action's poll loop knows how long to wait.  Individual steps may override
        // this with their own `timeout:` field in YAML.
        int resolvedDefault = testCase.defaultTimeout > 0 ? testCase.defaultTimeout : 30;
        TestContext.setDefaultTimeout(resolvedDefault);
        System.out.println("Poll timeout : " + resolvedDefault + "s (defaultTimeout)");

        // Seed well-known API base URLs as interpolation variables so YAML steps
        // can use ${APP_API_URL}, ${UAT_API_URL}, ${BACKEND_API_URL} without hardcoding
        TestContext.setScalarData("APP_API_URL",     com.popclub.api.util.ApiConstants.APP_API_URL);
        TestContext.setScalarData("UAT_API_URL",     com.popclub.api.util.ApiConstants.UAT_API_URL);
        TestContext.setScalarData("BACKEND_API_URL", com.popclub.api.util.ApiConstants.BACKEND_API_URL);

        if (resumeMode) {
            System.out.println("Starting Test (resuming from step " + fromStep + "): " + testCase.testName);
        } else {
            System.out.println("Starting Test: " + testCase.testName);
        }

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

        // Run onFlowStart steps
        if (testCase.onFlowStart != null && !testCase.onFlowStart.isEmpty()) {
            System.out.println("[onFlowStart] Running " + testCase.onFlowStart.size() + " hook steps");
            resolveAndRunSteps(testCase.onFlowStart, maxRetry);
        }

        for (Step step : testCase.steps) {

            try {
                resolveDataRef(step);
            } catch (Exception e) {
                LoggerUtil.fail("Step " + stepIndex + " failed: " + e.getMessage());
                throw e;
            }

            // Resolve locators — priority: element → locator → resourceId → text → bounds/xy
            if (step.element != null) {

                // Interpolate ${varName} in element name before repository lookup
                if (step.element.contains("${")) {
                    step.element = interpolate(step.element);
                }

                // Try ElementRepository first; fall back to treating the value as a direct
                // accessibilityId for dynamic names (e.g. product_list_item_3) that are not
                // registered in elements.yaml because they are generated at runtime.
                try {
                    step.locators = ElementRepository.getLocators(
                            step.element,
                            TestContext.getPlatform()
                    );
                } catch (RuntimeException notFound) {
                    System.out.println("[locator] '" + step.element
                            + "' not in elements.yaml — using as direct accessibilityId");
                    Locator loc = new Locator();
                    loc.type  = "accessibilityId";
                    loc.value = step.element;
                    step.locators = List.of(loc);
                }

            } else if (step.locator != null) {

                // accessibilityId (qaTestTag) — best case
                Locator locator = new Locator();
                locator.type  = step.locator.contains(":id/") ? "id" : "accessibilityId";
                locator.value = step.locator;
                step.locators = List.of(locator);

            } else if (step.text != null) {

                // Interpolate ${varName} in text before building the locator so that
                // scrollUntilVisible / tapByText / verifyElement with text: "${var}" work correctly.
                if (step.text.contains("${")) {
                    step.text = interpolate(step.text);
                }
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

            // ── Variable interpolation — expand ${varName} in value, text, variable fields ──
            if (step.value != null && step.value.contains("${")) {
                step.value = interpolate(step.value);
            }
            if (step.text != null && step.text.contains("${")) {
                step.text = interpolate(step.text);
            }
            if (step.variable != null && step.variable.contains("${")) {
                step.variable = interpolate(step.variable);
            }
            // Also support captureText using `variable:` field as alias for `value:`
            if ("captureText".equalsIgnoreCase(step.action) && step.variable != null && step.value == null) {
                step.value = step.variable;
            }

            // ── Reusable flow call ─────────────────────────────────────────────
            if ("call".equalsIgnoreCase(step.action)) {
                if (step.flow == null || step.flow.isBlank())
                    throw new RuntimeException("call: 'flow' name is required");
                // Inject params as variables (available via ${varName} in flow steps)
                if (step.params != null) {
                    step.params.forEach((k, v) -> {
                        String resolved = (v != null && v.contains("${")) ? interpolate(v) : v;
                        TestContext.setScalarData(k, resolved != null ? resolved : "");
                        System.out.printf("  ↩  call param: %s = \"%s\"%n", k, resolved);
                    });
                }
                System.out.println("[call] flow: " + step.flow);
                List<Step> flowSteps = FlowLoader.load(step.flow);
                try {
                    resolveAndRunSteps(flowSteps, maxRetry, true);
                } catch (Exception e) {
                    LoggerUtil.fail("Step " + stepIndex + " failed: " + e.getMessage());
                    throw e;
                }
                stepIndex++;
                continue;
            }

            // ── Conditional steps (ifPresent / ifNotPresent) ──────────────────
            if ("ifPresent".equalsIgnoreCase(step.action)
                    || "ifNotPresent".equalsIgnoreCase(step.action)) {
                boolean wantPresent = "ifPresent".equalsIgnoreCase(step.action);
                boolean present     = isElementPresent(step);
                boolean takeThen    = present == wantPresent;
                System.out.printf("[conditional] %s: element %s → %s%n",
                        step.action, present ? "FOUND" : "NOT FOUND",
                        takeThen ? "THEN" : "ELSE");
                try {
                    if (takeThen && step.steps != null && !step.steps.isEmpty()) {
                        resolveAndRunSteps(step.steps, maxRetry);
                    } else if (!takeThen && step.elseBranch != null && !step.elseBranch.isEmpty()) {
                        resolveAndRunSteps(step.elseBranch, maxRetry);
                    }
                } catch (Exception e) {
                    LoggerUtil.fail("Step " + stepIndex + " failed: " + e.getMessage());
                    throw e;
                }
                stepIndex++;
                continue;
            }

            // ── Conditional steps (ifVarEmpty / ifVarNotEmpty) ────────────────
            if ("ifVarEmpty".equalsIgnoreCase(step.action)
                    || "ifVarNotEmpty".equalsIgnoreCase(step.action)) {
                String varName  = step.variable != null ? step.variable : step.element;
                String varVal   = varName != null ? TestContext.getScalarData(varName) : null;
                boolean isEmpty  = (varVal == null || varVal.trim().isEmpty());
                boolean wantEmpty = "ifVarEmpty".equalsIgnoreCase(step.action);
                boolean takeThen  = isEmpty == wantEmpty;
                System.out.printf("[conditional] %s: ${%s}=\"%s\" → %s%n",
                        step.action, varName, varVal != null ? varVal : "", takeThen ? "THEN" : "ELSE");
                try {
                    if (takeThen && step.steps != null && !step.steps.isEmpty()) {
                        resolveAndRunSteps(step.steps, maxRetry);
                    } else if (!takeThen && step.elseBranch != null && !step.elseBranch.isEmpty()) {
                        resolveAndRunSteps(step.elseBranch, maxRetry);
                    }
                } catch (Exception e) {
                    LoggerUtil.fail("Step " + stepIndex + " failed: " + e.getMessage());
                    throw e;
                }
                stepIndex++;
                continue;
            }

            // ── Conditional steps (ifVarEquals / ifVarNotEquals) ─────────────
            if ("ifVarEquals".equalsIgnoreCase(step.action)
                    || "ifVarNotEquals".equalsIgnoreCase(step.action)) {
                String varName  = step.variable != null ? step.variable : step.element;
                String varVal   = varName != null ? TestContext.getScalarData(varName) : null;
                String expected = interpolate(step.value != null ? step.value : "");
                boolean matches  = expected.equals(varVal != null ? varVal.trim() : "");
                boolean wantEq   = "ifVarEquals".equalsIgnoreCase(step.action);
                boolean takeThen = matches == wantEq;
                System.out.printf("[conditional] %s: ${%s}=\"%s\" %s \"%s\" → %s%n",
                        step.action, varName, varVal != null ? varVal : "",
                        matches ? "==" : "!=", expected, takeThen ? "THEN" : "ELSE");
                try {
                    if (takeThen && step.steps != null && !step.steps.isEmpty()) {
                        resolveAndRunSteps(step.steps, maxRetry);
                    } else if (!takeThen && step.elseBranch != null && !step.elseBranch.isEmpty()) {
                        resolveAndRunSteps(step.elseBranch, maxRetry);
                    }
                } catch (Exception e) {
                    LoggerUtil.fail("Step " + stepIndex + " failed: " + e.getMessage());
                    throw e;
                }
                stepIndex++;
                continue;
            }

            // ── repeat ─────────────────────────────────────────────────────────
            if ("repeat".equalsIgnoreCase(step.action)) {
                int times = step.times > 0 ? step.times
                        : (step.value != null && step.value.matches("\\d+") ? Integer.parseInt(step.value.trim()) : 1);
                System.out.println("[repeat] " + times + " times");
                for (int r = 0; r < times; r++) {
                    System.out.println("[repeat] iteration " + (r+1) + "/" + times);
                    if (step.steps != null && !step.steps.isEmpty()) {
                        resolveAndRunSteps(new ArrayList<>(step.steps), maxRetry);
                    }
                }
                stepIndex++;
                continue;
            }

            // ── logVar: print a variable's current value to the test log ─────
            if ("logVar".equalsIgnoreCase(step.action)) {
                String varName = step.variable != null ? step.variable : step.element;
                String varVal  = varName != null ? TestContext.getScalarData(varName) : null;
                String label   = step.value != null ? step.value : varName;
                String msg;
                if (label != null && label.contains("\n")) {
                    // Multiline template — each line needs its own [STEP] prefix
                    // so the Forge UI colours all lines (not just the first one).
                    LoggerUtil.step("[logVar] " + varName + " =");
                    for (String logLine : label.stripTrailing().split("\n")) {
                        LoggerUtil.step("  " + logLine.stripLeading());
                    }
                } else {
                    msg = String.format("[logVar] %s = \"%s\"", label, varVal != null ? varVal : "(not set)");
                    System.out.println(msg);
                    LoggerUtil.step(msg);
                }
                stepIndex++;
                continue;
            }

            // ── fromStep: handle steps before the resume point ────────────────────
            if (resumeMode && stepIndex < fromStep) {
                if ("launchApp".equalsIgnoreCase(step.action)) {
                    // Must still create the Appium driver session; noReset=true means
                    // the app won't be killed. Run LaunchAppAction silently.
                    System.out.println("[fromStep] Step " + stepIndex + ": launchApp (creating driver, app kept alive)");
                    LoggerUtil.step("[" + stepIndex + "] launchApp (resume — no restart)");
                    try {
                        ActionFactory.get(step.action).perform(step);
                    } catch (Exception e) {
                        System.out.println("[fromStep] launchApp setup warning: " + e.getMessage());
                    }
                } else {
                    System.out.println("[fromStep] Skipping step " + stepIndex + ": " + step.action);
                    LoggerUtil.step("[" + stepIndex + "] SKIPPED: " + step.action);
                }
                stepIndex++;
                continue;
            }
            // ─────────────────────────────────────────────────────────────────────

            int attempt = 0;
            boolean success = false;

            while (attempt <= maxRetry && !success) {

                try {

                    LoggerUtil.step(
                            "[" + stepIndex + "] " + step.action
                    );

                    Action action = ActionFactory.get(step.action);
                    action.perform(step);

                    if ("launchApp".equalsIgnoreCase(step.action)) {

                        AppiumDriver driver = DriverManager.getDriver();

                        VideoUtil.startRecording(driver);

                        if (TestContext.isFreshLaunch()) {
                            // Start screen keep-alive heartbeat (KEYCODE_WAKEUP every 25s)
                            DeviceKeepAlive.start(driver);

                            Thread.sleep(3000);

                            SystemPopupHandler.handle(driver);

                            TestContext.setFreshLaunch(false);
                        }
                    }

                    success = true;

                    LoggerUtil.pass("Step " + stepIndex + " passed");

                    if (step.testCaseId != null) {
                        TestContext.markPassed(step.testCaseId);
                    }

                } catch (Exception e) {

                    attempt++;

                    if (attempt > maxRetry) {

                        // ── Self-healing: scan screen and try to fix element mismatch ──
                        boolean healed = SelfHealingEngine.tryHeal(
                                step, TestContext.getPlatform(), e);

                        if (healed) {
                            System.out.println("[SelfHeal] Retrying step " + stepIndex
                                    + " with healed locator…");
                            try {
                                Action healAction = ActionFactory.get(step.action);
                                healAction.perform(step);
                                success = true;
                                LoggerUtil.pass("Step " + stepIndex
                                        + " passed after self-healing ✅");
                                break; // exit retry while-loop — step succeeded
                            } catch (Exception healEx) {
                                System.out.println("[SelfHeal] Healed locator still failed: "
                                        + healEx.getMessage());
                                // Fall through to normal failure path below
                            }
                        }

                        if (!success) {
                            String tcSuffix = step.testCaseId != null ? " (" + step.testCaseId + ")" : "";
                            LoggerUtil.fail(
                                    "Step " + stepIndex + tcSuffix +
                                            " failed: " + e.getMessage()
                            );

                            // Store failing element so TestListener can write tag report
                            String failingElem = step.element != null ? step.element : step.locator;
                            TestContext.setFailingElement(failingElem);

                            ScreenshotUtil.capture("step_" + stepIndex);

                            if (step.testCaseId != null) {
                                TestContext.markFailed(step.testCaseId);
                            }

                            throw new RuntimeException(e);
                        }
                    } else {
                        System.out.println("Retry step " + stepIndex +
                                " attempt " + attempt);
                    }
                }
            }

            stepIndex++;
        }

        // STOP KEEP-ALIVE + VIDEO
        DeviceKeepAlive.stop();
        AppiumDriver driver = DriverManager.getDriver();
        try {
            File video = VideoUtil.stopAndSave(driver, testCase.testName);
            TestContext.setVideoFile(video);
        } catch (Exception e) {
            System.out.println("[TestExecutor] ⚠️  Video save failed (non-fatal): " + e.getMessage());
        }

        // YAML MODE RESULT
        if ("YAML".equals(TestContext.getExecutionMode())) {

            for (String tc : allTestCaseIds) {
                TestContext.markPassed(tc);
            }
        }

        // 🔥 BLOCKED
        TestContext.markBlockedIfMissing(allTestCaseIds);

        System.out.println("Test Execution Completed");

        // Run onFlowComplete steps (success path)
        if (testCase.onFlowComplete != null && !testCase.onFlowComplete.isEmpty()) {
            System.out.println("[onFlowComplete] Running " + testCase.onFlowComplete.size() + " hook steps");
            try {
                resolveAndRunSteps(testCase.onFlowComplete, 0);
            } catch (Exception e) {
                System.out.println("[onFlowComplete] Hook step failed (ignored): " + e.getMessage());
            }
        }
    }

    // ── Variable interpolation ────────────────────────────────────────────────

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Replaces all {@code ${varName}} placeholders in {@code text} with the
     * value stored under {@code varName} in {@link TestContext#getScalarData}.
     * Unknown variables are left as-is so typos surface as test failures.
     */
    private static final Random RAND = new Random();
    private static final String[] RANDOM_WORDS = {"alpha","beta","gamma","delta","sigma","orbit","forge","blaze","nova","pixel"};
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static String resolveBuiltIn(String key) {
        switch (key) {
            case "randomEmail": {
                String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
                StringBuilder s = new StringBuilder("test_");
                for (int i = 0; i < 4; i++) s.append(chars.charAt(RAND.nextInt(chars.length())));
                s.append("@popclub.com");
                return s.toString();
            }
            case "randomPhone": {
                StringBuilder s = new StringBuilder("9");
                for (int i = 0; i < 9; i++) s.append(RAND.nextInt(10));
                return s.toString();
            }
            case "timestamp":
                return LocalDateTime.now().format(TS_FMT);
            case "randomWord":
                return RANDOM_WORDS[RAND.nextInt(RANDOM_WORDS.length)];
            case "randomInt":
                return String.valueOf(1000 + RAND.nextInt(9000));
            case "uuid":
                return UUID.randomUUID().toString().substring(0, 8);
            default:
                return null;
        }
    }

    private static String interpolate(String text) {
        Matcher m = VAR_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).trim();
            // Check built-in dynamic variables first
            String builtIn = resolveBuiltIn(key);
            if (builtIn != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(builtIn));
                System.out.printf("  ↩  interpolate: ${%s} = \"%s\" (built-in)%n", key, builtIn);
                continue;
            }
            String val = TestContext.getScalarData(key);
            if (val.isEmpty()) {
                System.out.printf("  ⚠️  interpolate: variable '${%s}' not set — leaving as-is%n", key);
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(val));
                System.out.printf("  ↩  interpolate: ${%s} = \"%s\"%n", key, val);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static void resolveDataRef(Step step) {
        if (step.dataRef == null || step.dataRef.isBlank()) return;

        int dotCount = step.dataRef.length() - step.dataRef.replace(".", "").length();

        if (dotCount == 2) {
            String val = TestDataRepository.resolve(step.dataRef);

            String fieldName = step.dataRef.substring(step.dataRef.lastIndexOf('.') + 1);
            TestContext.setScalarData(fieldName, val);
            if (step.value == null) step.value = val;

            System.out.printf("  ↩  dataRef: %s → \"%s\"%n", step.dataRef, val);
            return;
        }

        if (dotCount != 1) {
            throw new RuntimeException(
                    "Invalid dataRef (expected \"file.object\" or \"file.object.field\"): " + step.dataRef);
        }

        Map<String, String> resolved = TestDataRepository.resolveObject(step.dataRef);

        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();

            TestContext.setScalarData(key, val);

            switch (key) {
                case "login":
                    if (step.value == null) step.value = val;
                    break;
                case "otp":
                    if (step.text == null) step.text = val;
                    break;
            }
        }

        System.out.printf("  ↩  dataRef: %s → %s%n", step.dataRef, resolved.keySet());
    }

    // ── Conditional helpers ───────────────────────────────────────────────────

    /**
     * Returns {@code true} if the element described by {@code step}'s locators
     * is visible within a short probe timeout.
     */
    private static boolean isElementPresent(Step step) {
        AppiumDriver driver;
        try {
            driver = DriverManager.getDriver();
        } catch (Exception e) {
            return false;
        }
        if (step.locators == null || step.locators.isEmpty()) return false;
        for (com.popclub.core.Locator loc : step.locators) {
            try {
                new WebDriverWait(driver, Duration.ofSeconds(3))
                        .until(ExpectedConditions.visibilityOfElementLocated(
                                LocatorUtil.getLocator(loc)));
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Resolves locators for each nested step and then runs them with the same
     * retry logic as top-level steps.  Used by ifPresent / ifNotPresent / call / etc.
     *
     * Special actions (call, ifPresent, ifVarEquals, logVar …) are handled
     * inline here — identical to the main execution loop — so that nested
     * branches can themselves contain flows, conditionals, and logVar.
     */
    private void resolveAndRunSteps(List<Step> steps, int maxRetry) {
        resolveAndRunSteps(steps, maxRetry, false);
    }

    private void resolveAndRunSteps(List<Step> steps, int maxRetry, boolean isFlow) {
        int idx = 1;
        for (Step step : steps) {
            try {
                resolveDataRef(step);
            } catch (Exception e) {
                LoggerUtil.fail("Step " + idx + " failed: " + e.getMessage());
                throw e;
            }

            // Resolve locators (same logic as the main loop)
            // Wrap in try-catch so a missing element key surfaces with a clear message
            // instead of an untrapped RuntimeException that kills the session silently.
            if (step.element != null) {
                // Interpolate ${varName} in element name before repository lookup
                if (step.element.contains("${")) {
                    step.element = interpolate(step.element);
                }
                try {
                    step.locators = ElementRepository.getLocators(step.element, TestContext.getPlatform());
                } catch (RuntimeException notFound) {
                    // Dynamic element names (e.g. product_list_item_3) are not registered in
                    // elements.yaml — treat the value directly as an accessibilityId.
                    System.out.println("[locator] '" + step.element
                            + "' not in elements.yaml — using as direct accessibilityId");
                    com.popclub.core.Locator loc = new com.popclub.core.Locator();
                    loc.type  = "accessibilityId";
                    loc.value = step.element;
                    step.locators = List.of(loc);
                }
            } else if (step.locator != null) {
                com.popclub.core.Locator loc = new com.popclub.core.Locator();
                loc.type  = step.locator.contains(":id/") ? "id" : "accessibilityId";
                loc.value = step.locator;
                step.locators = List.of(loc);
            } else if (step.text != null) {
                if (step.text.contains("${")) {
                    step.text = interpolate(step.text);
                }
                com.popclub.core.Locator loc = new com.popclub.core.Locator();
                loc.type  = "text";
                loc.value = step.text;
                step.locators = List.of(loc);
            }
            // Variable interpolation
            if (step.value != null && step.value.contains("${")) {
                step.value = interpolate(step.value);
            }
            if (step.text != null && step.text.contains("${")) {
                step.text = interpolate(step.text);
            }
            if (step.variable != null && step.variable.contains("${")) {
                step.variable = interpolate(step.variable);
            }
            if ("captureText".equalsIgnoreCase(step.action) && step.variable != null && step.value == null) {
                step.value = step.variable;
            }

            // ── call ──────────────────────────────────────────────────────────
            if ("call".equalsIgnoreCase(step.action)) {
                if (step.flow == null || step.flow.isBlank())
                    throw new RuntimeException("call: 'flow' name is required");
                if (step.params != null) {
                    step.params.forEach((k, v) -> {
                        String resolved = (v != null && v.contains("${")) ? interpolate(v) : v;
                        TestContext.setScalarData(k, resolved != null ? resolved : "");
                        System.out.printf("  ↩  call param: %s = \"%s\"%n", k, resolved);
                    });
                }
                System.out.println("[call] flow: " + step.flow);
                List<Step> flowSteps = FlowLoader.load(step.flow);
                resolveAndRunSteps(flowSteps, maxRetry, true);
                idx++;
                continue;
            }

            // ── ifPresent / ifNotPresent ──────────────────────────────────────
            if ("ifPresent".equalsIgnoreCase(step.action) || "ifNotPresent".equalsIgnoreCase(step.action)) {
                boolean wantPresent = "ifPresent".equalsIgnoreCase(step.action);
                boolean present     = isElementPresent(step);
                boolean takeThen    = present == wantPresent;
                System.out.printf("[conditional] %s: element %s → %s%n", step.action, present ? "FOUND" : "NOT FOUND", takeThen ? "THEN" : "ELSE");
                if (takeThen && step.steps != null && !step.steps.isEmpty())
                    resolveAndRunSteps(step.steps, maxRetry);
                else if (!takeThen && step.elseBranch != null && !step.elseBranch.isEmpty())
                    resolveAndRunSteps(step.elseBranch, maxRetry);
                idx++;
                continue;
            }

            // ── ifVarEmpty / ifVarNotEmpty ────────────────────────────────────
            if ("ifVarEmpty".equalsIgnoreCase(step.action) || "ifVarNotEmpty".equalsIgnoreCase(step.action)) {
                String varName  = step.variable != null ? step.variable : step.element;
                String varVal   = varName != null ? TestContext.getScalarData(varName) : null;
                boolean isEmpty  = (varVal == null || varVal.trim().isEmpty());
                boolean takeThen = isEmpty == "ifVarEmpty".equalsIgnoreCase(step.action);
                System.out.printf("[conditional] %s: ${%s}=\"%s\" → %s%n", step.action, varName, varVal != null ? varVal : "", takeThen ? "THEN" : "ELSE");
                if (takeThen && step.steps != null && !step.steps.isEmpty())
                    resolveAndRunSteps(step.steps, maxRetry);
                else if (!takeThen && step.elseBranch != null && !step.elseBranch.isEmpty())
                    resolveAndRunSteps(step.elseBranch, maxRetry);
                idx++;
                continue;
            }

            // ── ifVarEquals / ifVarNotEquals ──────────────────────────────────
            if ("ifVarEquals".equalsIgnoreCase(step.action) || "ifVarNotEquals".equalsIgnoreCase(step.action)) {
                String varName  = step.variable != null ? step.variable : step.element;
                String varVal   = varName != null ? TestContext.getScalarData(varName) : null;
                String expected = interpolate(step.value != null ? step.value : "");
                boolean matches  = expected.equals(varVal != null ? varVal.trim() : "");
                boolean takeThen = matches == "ifVarEquals".equalsIgnoreCase(step.action);
                System.out.printf("[conditional] %s: ${%s}=\"%s\" %s \"%s\" → %s%n", step.action, varName, varVal != null ? varVal : "", matches ? "==" : "!=", expected, takeThen ? "THEN" : "ELSE");
                if (takeThen && step.steps != null && !step.steps.isEmpty())
                    resolveAndRunSteps(step.steps, maxRetry);
                else if (!takeThen && step.elseBranch != null && !step.elseBranch.isEmpty())
                    resolveAndRunSteps(step.elseBranch, maxRetry);
                idx++;
                continue;
            }

            // ── repeat ───────────────────────────────────────────────────────
            if ("repeat".equalsIgnoreCase(step.action)) {
                int times = step.times > 0 ? step.times
                        : (step.value != null && step.value.matches("\\d+") ? Integer.parseInt(step.value.trim()) : 1);
                System.out.println("[repeat] " + times + " times");
                for (int r = 0; r < times; r++) {
                    System.out.println("[repeat] iteration " + (r+1) + "/" + times);
                    if (step.steps != null && !step.steps.isEmpty()) {
                        resolveAndRunSteps(new ArrayList<>(step.steps), maxRetry, isFlow);
                    }
                }
                idx++;
                continue;
            }

            // ── logVar ────────────────────────────────────────────────────────
            if ("logVar".equalsIgnoreCase(step.action)) {
                String varName = step.variable != null ? step.variable : step.element;
                String varVal  = varName != null ? TestContext.getScalarData(varName) : null;
                String label   = step.value != null ? step.value : varName;
                String msg;
                if (label != null && label.contains("\n")) {
                    // Multiline template — each line needs its own [STEP] prefix
                    // so the Forge UI colours all lines (not just the first one).
                    LoggerUtil.step("[logVar] " + varName + " =");
                    for (String logLine : label.stripTrailing().split("\n")) {
                        LoggerUtil.step("  " + logLine.stripLeading());
                    }
                } else {
                    msg = String.format("[logVar] %s = \"%s\"", label, varVal != null ? varVal : "(not set)");
                    System.out.println(msg);
                    LoggerUtil.step(msg);
                }
                idx++;
                continue;
            }

            // ── Normal action ─────────────────────────────────────────────────
            int attempt = 0;
            boolean success = false;
            while (attempt <= maxRetry && !success) {
                try {
                    if (isFlow) {
                        LoggerUtil.step("[F:" + idx + "] " + step.action
                                + (step.element != null ? "  " + step.element : "")
                                + (step.value   != null ? "  \"" + step.value + "\"" : ""));
                    } else {
                        LoggerUtil.step("  [branch-" + idx + "] " + step.action);
                    }
                    ActionFactory.get(step.action).perform(step);
                    success = true;
                    if (isFlow) {
                        LoggerUtil.pass("F:" + idx + " passed");
                    } else {
                        LoggerUtil.pass("  branch step " + idx + " passed");
                    }
                } catch (Exception e) {
                    attempt++;
                    if (attempt > maxRetry) {
                        if (isFlow) {
                            LoggerUtil.fail("F:" + idx + " failed: " + e.getMessage());
                        }
                        throw new RuntimeException(
                            (isFlow ? "Flow" : "Conditional branch") + " step " + idx
                            + " (" + step.action + ") failed: " + e.getMessage(), e);
                    }
                    System.out.println("  Retry " + (isFlow ? "flow" : "branch") + " step " + idx + " attempt " + attempt);
                }
            }
            idx++;
        }
    }
}