package com.popclub.testsigma;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;
import com.popclub.api.dto.*;

public class TestSigmaReportListener implements ISuiteListener, ITestListener {

    private static final String MAPPING_FILE = "config/testsigma-mapping.json";
    private static final int MAX_CREATE_RETRIES = 20;

    private String runId;
    private Map<String, MappingEntry> methodMapping;
    private boolean enabled;

    @Override
    public void onStart(ISuite suite) {
        enabled = !Boolean.parseBoolean(System.getProperty("testsigma.report.off", "false"));
        if (!enabled) {
            System.out.println("[TestSigma] Reporting disabled via -Dtestsigma.report.off=true");
            return;
        }

        try {
            methodMapping = loadMapping();
            if (methodMapping.isEmpty()) {
                System.out.println("[TestSigma] No mapping found in " + MAPPING_FILE
                        + ". Run TestSigmaSync first (mvn test -Dtest=TestSigmaSync).");
                enabled = false;
                return;
            }

            List<String> uuids = new ArrayList<>();
            for (MappingEntry entry : methodMapping.values()) {
                if (entry.uuid != null) uuids.add(entry.uuid);
            }

            String projectId = TestSigmaConfig.projectId();
            String prefix = TestSigmaConfig.runTitlePrefix();
            String tags = TestSigmaConfig.runTags();

            int nextNumber = getNextRunNumber(projectId, prefix);
            String title = null;
            int attempts = 0;
            while (runId == null && attempts < MAX_CREATE_RETRIES) {
                title = prefix + " #" + nextNumber;
                runId = TestSigmaClient.createRun(title, projectId, tags, uuids);
                if (runId == null) nextNumber++;
                attempts++;
            }

            if (runId == null) {
                System.out.println("[TestSigma] Failed to create run after " + MAX_CREATE_RETRIES + " attempts");
                enabled = false;
                return;
            }

            System.out.println("[TestSigma] Created run: " + title
                    + " (" + runId + ") with " + uuids.size() + " test cases");

        } catch (Exception e) {
            System.out.println("[TestSigma] Failed to initialize: " + e.getMessage());
            enabled = false;
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        if (!enabled || runId == null) return;

        try {
            TestSigmaClient.updateRunStatus(TestSigmaConfig.projectId(), runId, RunStatus.FINISHED);
            System.out.println("[TestSigma] Run marked FINISHED: " + runId);
        } catch (Exception e) {
            System.out.println("[TestSigma] Failed to finish run: " + e.getMessage());
        }
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        reportStatus(result, TestCaseStatus.PASSED);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        reportStatus(result, TestCaseStatus.FAILED);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        reportStatus(result, TestCaseStatus.SKIPPED);
    }

    private void reportStatus(ITestResult result, TestCaseStatus status) {
        if (!enabled || runId == null) return;

        try {
            String methodName = result.getMethod().getMethodName();
            String uuid = resolveUuid(result);

            if (uuid == null) {
                System.out.println("[TestSigma] No mapping for method: " + methodName);
                return;
            }

            long duration = result.getEndMillis() - result.getStartMillis();
            TestSigmaClient.updateTestCaseRun(
                    TestSigmaConfig.projectId(), runId, uuid, status.id(),
                    TestSigmaConfig.userId(), duration);

            System.out.println("[TestSigma] " + methodName + " → " + status.name());

        } catch (Exception e) {
            System.out.println("[TestSigma] Failed to report "
                    + result.getMethod().getMethodName() + ": " + e.getMessage());
        }
    }

    private String resolveUuid(ITestResult result) {

        String methodName = result.getMethod().getMethodName();
        Object[] params = result.getParameters();

        try {

            // SSO
            if ("sso_invalidMobile".equals(methodName)) {

                String mobile = (String) params[0];

                if (mobile == null) {
                    methodName = "sso_nullMobileNumber";
                } else if (mobile.isEmpty()) {
                    methodName = "sso_emptyMobileNumber";
                } else if (mobile.length() < 10) {
                    methodName = "sso_shortMobileNumber";
                } else if (mobile.matches("[a-zA-Z]+")) {
                    methodName = "sso_alphabeticMobileNumber";
                } else if (mobile.matches(".*[^0-9].*")) {
                    methodName = "sso_specialCharsMobileNumber";
                } else if (mobile.length() > 10) {
                    methodName = "sso_tooLongMobileNumber";
                }
            }

            // PAN
            else if ("pan_invalidInput".equals(methodName)) {

                UserDetailsPanRequestDto dto =
                        (UserDetailsPanRequestDto) params[0];

                if (dto.getPan() == null) {
                    methodName = "pan_nullPan";
                } else if ("".equals(dto.getPan())) {
                    methodName = "pan_emptyPan";
                } else if (dto.getPan().matches(".*[^a-zA-Z0-9].*")) {
                    methodName = "pan_specialCharsPan";
                } else if (dto.getPan().length() > 10) {
                    methodName = "pan_tooLongPan";
                } else if (!dto.getPan().matches("[A-Z]{5}[0-9]{4}[A-Z]")) {
                    methodName = "pan_invalidFormat";
                } else if (dto.getPinCode() == null) {
                    methodName = "pan_nullPincode";
                } else if ("".equals(dto.getPinCode())) {
                    methodName = "pan_emptyPincode";
                }
            }

            // PINCODE
            else if ("pincode_invalidFormat".equals(methodName)) {

                String pincode = (String) params[0];

                if ("".equals(pincode)) {
                    methodName = "pincode_empty";
                } else if (pincode.matches("[a-zA-Z]+")) {
                    methodName = "pincode_invalidLetters";
                } else if (pincode.matches(".*[^0-9].*")) {
                    methodName = "pincode_specialChars";
                } else if (pincode.length() < 6) {
                    methodName = "pincode_tooShort";
                } else if (pincode.length() > 6) {
                    methodName = "pincode_tooLong";
                }
            }

            // BASIC DETAILS
            else if ("basicDetails_invalidInput".equals(methodName)) {

                UserDetailsBasicRequestDto dto =
                        (UserDetailsBasicRequestDto) params[0];

                if (dto.getFirstName() == null) {
                    methodName = "basicDetails_missingFirstName";
                } else if (dto.getLastName() == null) {
                    methodName = "basicDetails_missingLastName";
                } else if (dto.getEmail() == null) {
                    methodName = "basicDetails_missingEmail";
                } else if (dto.getDob() == null) {
                    methodName = "basicDetails_missingDob";
                } else if (dto.getEmail() != null &&
                        !dto.getEmail().contains("@")) {
                    methodName = "basicDetails_invalidEmail";
                } else {
                    methodName = "basicDetails_invalidDob";
                }
            }

            // CONSENT MOBILE
            else if ("popConsentsGet_invalidMobile".equals(methodName)) {

                String mobile = (String) params[0];

                if ("".equals(mobile)) {
                    methodName = "popConsentsGet_emptyMobile";
                } else {
                    methodName = "popConsentsGet_invalidMobile";
                }
            }

            // CURRENT ADDRESS
            else if ("address_invalidInput".equals(methodName)) {

                AddressRequestDto dto =
                        (AddressRequestDto) params[0];

                if (dto.getAddressLine1() == null) {
                    methodName = "address_missingAddressLine1";
                } else if (dto.getCity() == null) {
                    methodName = "address_missingCity";
                } else if (dto.getState() == null) {
                    methodName = "address_missingState";
                } else {
                    methodName = "address_invalidPincode";
                }
            }

            // OFFICE ADDRESS
            else if ("officeAddress_invalidInput".equals(methodName)) {

                AddressRequestDto dto =
                        (AddressRequestDto) params[0];

                if (dto.getCity() == null) {
                    methodName = "officeAddress_missingCity";
                } else {
                    methodName = "officeAddress_invalidPincode";
                }
            }

            // DELIVERY ADDRESS
            else if ("deliveryAddress_invalidType".equals(methodName)) {

                AddressRequestDto dto =
                        (AddressRequestDto) params[0];

                if (dto.getAddressType() == null) {
                    methodName = "deliveryAddress_nullType";
                } else {
                    methodName = "deliveryAddress_invalidType";
                }
            }

            // PERSONAL DETAILS
            else if ("personalDetails_missingRequiredField".equals(methodName)) {

                PersonalDetailsRequestDto dto =
                        (PersonalDetailsRequestDto) params[0];

                if (dto.getNameOnCard() == null) {
                    methodName = "personalDetails_missingNameOnCard";
                } else {
                    methodName = "personalDetails_missingFatherName";
                }
            }

            // PROFESSIONAL DETAILS
            else if ("professionalDetails_invalidInput".equals(methodName)) {

                ProfessionalDetailsRequestDto dto =
                        (ProfessionalDetailsRequestDto) params[0];

                if (dto.getCompanyName() == null) {
                    methodName = "professionalDetails_missingCompanyName";
                } else if (dto.getDesignation() == null) {
                    methodName = "professionalDetails_missingDesignation";
                } else if ("0".equals(dto.getAnnualIncome())) {
                    methodName = "professionalDetails_zeroIncome";
                } else {
                    methodName = "professionalDetails_negativeIncome";
                }
            }

            // SERVER
            else if ("server_gracefullyHandlesBadRequest".equals(methodName)) {

                String contentType = (String) params[0];
                String body = (String) params[1];
                String path = (String) params[2];

                boolean malformed = body.contains("{");

                if (path.contains("user-details")) {

                    if (!contentType.equals("application/json")) {
                        methodName = "server_invalidContentType_userDetails";
                    } else if (body.isEmpty()) {
                        methodName = "server_emptyBody_userDetails";
                    } else {
                        methodName = "server_malformedJsonBody_userDetails";
                    }

                } else if (path.contains("ybl/consents")) {

                    if (!contentType.equals("application/json")) {
                        methodName = "server_invalidContentType_yblConsents";
                    } else if (body.isEmpty()) {
                        methodName = "server_emptyBody_yblConsents";
                    } else {
                        methodName = "server_malformedJsonBody_yblConsents";
                    }

                } else if (path.contains("ybl/address")) {

                    if (body.isEmpty()) {
                        methodName = "server_emptyBody_yblAddress";
                    } else {
                        methodName = "server_malformedJsonBody_yblAddress";
                    }

                } else if (path.contains("ybl/personal-details")) {

                    methodName = "server_malformedJsonBody_yblPersonalDetails";

                } else if (path.contains("ybl/professional-details")) {

                    methodName = "server_malformedJsonBody_yblProfessionalDetails";
                }
            }

            MappingEntry entry = methodMapping.get(methodName);

            if (entry != null) {
                return entry.uuid;
            }

            System.out.println("[TestSigma] No mapping found for: " + methodName);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private MappingEntry findByHumanId(String humanId) {
        for (MappingEntry entry : methodMapping.values()) {
            if (humanId.equals(entry.humanId)) return entry;
        }
        return null;
    }

    private int getNextRunNumber(String projectId, String prefix) {
        try {
            List<Map<String, Object>> runs = TestSigmaClient.getTestRuns(projectId, prefix);
            int max = 0;
            for (Map<String, Object> run : runs) {
                String title = (String) run.get("title");
                if (title != null && title.startsWith(prefix + " #")) {
                    String numStr = title.substring((prefix + " #").length()).trim();
                    try {
                        int num = Integer.parseInt(numStr);
                        if (num > max) max = num;
                    } catch (NumberFormatException ignored) {}
                }
            }
            return max + 1;
        } catch (Exception e) {
            System.out.println("[TestSigma] Could not fetch runs for numbering, defaulting to 1");
            return 1;
        }
    }

    private Map<String, MappingEntry> loadMapping() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(MAPPING_FILE);
            if (is == null) return Collections.emptyMap();

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(is, new TypeReference<Map<String, MappingEntry>>() {});
        } catch (Exception e) {
            System.out.println("[TestSigma] Failed to load mapping: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    public static class MappingEntry {
        public String humanId;
        public String uuid;
    }
}
