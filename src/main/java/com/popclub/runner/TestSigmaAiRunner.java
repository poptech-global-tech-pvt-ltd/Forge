package com.popclub.runner;

import com.popclub.testsigma.TestSigmaClient;
import org.testng.annotations.Test;

import java.io.FileWriter;
import java.util.*;
import java.util.regex.*;

public class TestSigmaAiRunner {

    String projectId = "d8f4a221-bc6d-47d8-9448-0834f5d012ec";
    String testCaseHumanId = "PO-7105";

    @Test
    public void generateYamlFromTestSigma() throws Exception {

        String testCaseId =
                TestSigmaClient.getTestCaseIdByHumanId(
                        projectId,
                        testCaseHumanId
                );

        List<String> stepTexts =
                TestSigmaClient.getTestCaseStepsText(
                        projectId,
                        testCaseId
                );

        List<String> tags =
                TestSigmaClient.getLabels(
                        projectId,
                        testCaseId
                );

        String testName =
                TestSigmaClient.getTitle(
                        projectId,
                        testCaseId
                );

        List<Map<String,String>> yamlSteps = new ArrayList<>();

        for (String step : stepTexts) {

            System.out.println("STEP: " + step);

            for (Intent intent : parseStep(step)) {
                yamlSteps.add(convertToYamlStep(intent));
            }
        }

        writeYaml(testName, tags, yamlSteps);
    }

    // ===============================
    // PARSER
    // ===============================

    private List<Intent> parseStep(String step) {

        String s = step.toLowerCase();

        // Launch app
        if (s.contains("launch"))
            return List.of(new Intent("launchApp", "app"));

        // Compound: navigate by entering a credential field and requesting action
        // e.g. "Navigate to OTP screen by entering mobile number and requesting OTP"
        if (s.contains("navigate") && s.contains("entering")
                && (s.contains("mobile") || s.contains("phone") || s.contains("number"))) {
            return List.of(
                new Intent("enterText", "login_input_phone", extractPhoneNumber(step)),
                new Intent("tap", deriveButtonName(step, "get_otp_button"))
            );
        }

        // Simple navigate / go to screen
        if (s.contains("navigate") || s.contains("go to"))
            return List.of(new Intent("tap", deriveButtonName(step, "continue_button")));

        // Enter / type text into a field
        if (s.contains("enter") || s.contains("type") || s.contains("input")) {
            return List.of(new Intent(
                    "enterText",
                    deriveFieldName(step),
                    extractInputValue(step)
            ));
        }

        // Click / tap a button
        if (s.contains("click") || s.contains("tap") || s.contains("press"))
            return List.of(new Intent("tap", deriveButtonName(step, null)));

        // Verify / assert element or screen
        if (s.contains("verify") || s.contains("assert")
                || s.contains("success") || s.contains("redirect")
                || s.contains("should"))
            return List.of(new Intent("verifyElement", deriveElementName(step)));

        // Default: treat as a tap
        return List.of(new Intent("tap", deriveElementName(step)));
    }

    // ===============================
    // ELEMENT NAME DERIVATION
    // ===============================

    /**
     * Derives a button element name from the step.
     * Priority: quoted label in step → keyword → fallback.
     */
    private String deriveButtonName(String step, String fallback) {

        // Use the first quoted non-numeric label as the button name
        String label = extractQuotedLabel(step);
        if (label != null)
            return toSnakeCase(label) + "_button";

        String s = step.toLowerCase();

        if (s.contains("verify otp") || (s.contains("verify") && s.contains("otp")))
            return "verify_otp_button";
        if (s.contains("get otp") || (s.contains("request") && s.contains("otp")))
            return "get_otp_button";
        if (s.contains("submit"))
            return "submit_button";
        if (s.contains("continue"))
            return "continue_button";
        if (s.contains("login") || s.contains("sign in"))
            return "login_button";
        if (s.contains("home"))
            return "home_button";

        return fallback != null ? fallback : deriveElementName(step);
    }

    /**
     * Derives an input field name from the step.
     * Priority: quoted label → keyword → fallback.
     */
    private String deriveFieldName(String step) {

        String s = step.toLowerCase();

        if (s.contains("otp"))
            return "otp_input";
        if (s.contains("phone") || s.contains("mobile"))
            return "login_input_phone";
        if (s.contains("password"))
            return "password_input";
        if (s.contains("email"))
            return "email_input";
        if (s.contains("name"))
            return "name_input";

        // Fall back to quoted label if present
        String label = extractQuotedLabel(step);
        if (label != null)
            return toSnakeCase(label) + "_input";

        return deriveElementName(step);
    }

    /**
     * Generic element name from step text — used for verifyElement and fallbacks.
     */
    private String deriveElementName(String step) {

        String s = step.toLowerCase();

        if (s.contains("home screen") || s.contains("home page"))
            return "home_screen";
        if (s.contains("otp"))
            return "otp_input";
        if (s.contains("phone") || s.contains("mobile"))
            return "login_input_phone";

        // Derive from quoted label if present
        String label = extractQuotedLabel(step);
        if (label != null)
            return toSnakeCase(label);

        // Last resort: sanitize the whole step text
        return toSnakeCase(step);
    }

    // ===============================
    // VALUE EXTRACTION
    // ===============================

    /**
     * Extracts the value to enter into a field.
     * Quoted numeric strings (OTP, codes) take priority.
     * Phone-number-length digits (10+) are also detected.
     */
    private String extractInputValue(String step) {

        // Quoted numbers: '123456' or "123456"
        Matcher quoted = Pattern.compile("['\"]([0-9]+)['\"]").matcher(step);
        if (quoted.find())
            return quoted.group(1);

        // Phone-number-length digits (10+ digits)
        Matcher phone = Pattern.compile("\\b(\\d{10,})\\b").matcher(step);
        if (phone.find())
            return phone.group(1);

        // OTP-length digits (4–8 digits)
        Matcher otp = Pattern.compile("\\b(\\d{4,8})\\b").matcher(step);
        if (otp.find())
            return otp.group(1);

        // Defaults by field type
        String s = step.toLowerCase();
        if (s.contains("otp"))         return "123456";
        if (s.contains("phone") || s.contains("mobile")) return "9876543210";
        if (s.contains("password"))    return "Password@1";
        if (s.contains("email"))       return "user@example.com";

        return "AUTO";
    }

    /**
     * Extracts a phone number from the step (10+ digits or default).
     */
    private String extractPhoneNumber(String step) {
        Matcher m = Pattern.compile("\\b(\\d{10,})\\b").matcher(step);
        if (m.find()) return m.group(1);
        return "9876543210";
    }

    // ===============================
    // HELPERS
    // ===============================

    /**
     * Extracts the first quoted non-numeric label from the step.
     * Ignores purely numeric quoted strings like '123456'.
     */
    private String extractQuotedLabel(String step) {
        Matcher m = Pattern.compile("['\"]([^0-9'\"][^'\"]*)['\"]").matcher(step);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    /**
     * Converts any string to lowercase_snake_case.
     */
    private String toSnakeCase(String text) {
        return text
                .replaceAll("[^a-zA-Z0-9 ]", "")
                .trim()
                .replaceAll("\\s+", "_")
                .toLowerCase();
    }

    // ===============================
    // YAML CONVERTER
    // ===============================

    private Map<String,String> convertToYamlStep(Intent intent) {

        Map<String,String> step = new LinkedHashMap<>();

        step.put("action", intent.action);

        if (!intent.action.equals("launchApp"))
            step.put("element", intent.target);

        if (intent.value != null)
            step.put("value", intent.value);

        return step;
    }

    // ===============================
    // YAML WRITER
    // ===============================

    private void writeYaml(
            String testName,
            List<String> tags,
            List<Map<String,String>> steps
    ) throws Exception {

        StringBuilder yaml = new StringBuilder();

        yaml.append("testName: ").append(testName).append("\n");
        yaml.append("platform: android\n\n");

        yaml.append("tags:\n");
        for (String tag : tags)
            yaml.append("  - ").append(tag).append("\n");

        yaml.append("\nsteps:\n\n");

        for (Map<String,String> step : steps) {

            yaml.append("  - action: ").append(step.get("action")).append("\n");

            if (step.containsKey("element"))
                yaml.append("    element: ").append(step.get("element")).append("\n");

            if (step.containsKey("value"))
                yaml.append("    value: ").append(step.get("value")).append("\n");

            yaml.append("\n");
        }

        FileWriter writer = new FileWriter("testsigma-ai.yaml");
        writer.write(yaml.toString());
        writer.close();

        System.out.println("✅ YAML generated: testsigma-ai.yaml");
    }

    // ===============================
    // MODEL
    // ===============================

    static class Intent {

        String action;
        String target;
        String value;

        Intent(String a, String t) {
            action = a;
            target = t;
        }

        Intent(String a, String t, String v) {
            action = a;
            target = t;
            value = v;
        }
    }
}