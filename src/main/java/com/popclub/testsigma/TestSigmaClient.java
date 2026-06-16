package com.popclub.testsigma;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;

import java.util.*;

import static io.restassured.RestAssured.given;

public class TestSigmaClient {

    static {
        RestAssured.baseURI = TestSigmaConfig.baseUrl();
        RestAssured.config = RestAssuredConfig.config()
                .sslConfig(SSLConfig.sslConfig().relaxedHTTPSValidation());
    }

    private static String authHeader() {
        return "Bearer " + TestSigmaConfig.token();
    }

    public static String createRun(String title,
                                   String projectId,
                                   String tags,
                                   List<String> testCaseIds) {

        long startTime = System.currentTimeMillis() / 1000;

        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "Automation Run");
        body.put("status", RunStatus.ACTIVE.value());
        body.put("project_id", projectId);
        body.put("start_date", startTime);
        body.put("label_names", Arrays.asList(tags.split(",")));
        body.put("selection_type", "STATIC");
        body.put("static_selection_filters", List.of(
                Map.of("field", "id", "operator", "IN", "values", testCaseIds)
        ));

        Response response = given()
                .header("Authorization", authHeader())
                .contentType("application/json")
                .body(body)
                .post("/projects/" + projectId + "/test_runs");

        if (response.getStatusCode() >= 400) {
            System.out.println("[TestSigma] Create run failed: " + response.jsonPath().getString("message"));
            return null;
        }

        return response.jsonPath().getString("data.test_run.id");
    }

    public static void updateRunStatus(String projectId, String runId, RunStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());

        given()
                .header("Authorization", authHeader())
                .contentType("application/json")
                .body(body)
                .patch("/projects/" + projectId + "/test_runs/" + runId);
    }

    public static String getTestCaseIdByHumanId(String projectId, String humanId) {
        Response response = given()
                .header("Authorization", authHeader())
                .queryParam("search", humanId)
                .get("/projects/" + projectId + "/test_cases");

        List<Map<String, Object>> list = response.jsonPath().getList("data.test_cases");

        if (list == null) {
            System.err.println("[TestSigma] API returned null for data.test_cases. " +
                    "Status: " + response.getStatusCode() +
                    " | Body: " + response.asPrettyString());
            throw new RuntimeException("TestCase not found (null response): " + humanId);
        }

        for (Map<String, Object> tc : list) {
            if (humanId.equalsIgnoreCase((String) tc.get("human_id"))) {
                return (String) tc.get("id");
            }
        }

        throw new RuntimeException("TestCase not found: " + humanId);
    }

    public static Map<String, String> getTestCaseRunMap(String runId) {
        Map<String, String> map = new HashMap<>();

        Response response = given()
                .header("Authorization", authHeader())
                .get("/test_runs/" + runId + "/test_case_runs");

        List<Map<String, Object>> list = response.jsonPath().getList("data.test_case_runs");
        if (list == null) return map;

        for (Map<String, Object> item : list) {
            String id = (String) item.get("id");
            @SuppressWarnings("unchecked")
            Map<String, Object> tc = (Map<String, Object>) item.get("test_case");
            String humanId = (String) tc.get("human_id");
            String uuid = (String) tc.get("id");

            map.put(humanId, id);
            if (uuid != null) map.put(uuid, id);
        }

        return map;
    }

    public static void updateTestCaseRun(String projectId,
                                         String runId,
                                         String testCaseId,
                                         String statusId,
                                         String userId,
                                         long duration) {
        try {
            Map<String, Object> caseObj = new HashMap<>();
            caseObj.put("test_case_id", testCaseId);
            caseObj.put("test_run_status_id", statusId);
            caseObj.put("user_id", userId);
            caseObj.put("description", "Automation Execution");

            Map<String, Object> body = new HashMap<>();
            body.put("test_run_cases", List.of(caseObj));

            Response response = given()
                    .header("Authorization", authHeader())
                    .contentType("multipart/form-data")
                    .multiPart("data", new ObjectMapper().writeValueAsString(body))
                    .put("/projects/" + projectId + "/test_runs/" + runId + "/test_cases");

            System.out.println("[TestSigma] Update Response Code: " + response.getStatusCode());

            if (response.getStatusCode() >= 400) {
                System.out.println("[TestSigma] FAILED UPDATE");
                System.out.println(response.getBody().asString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void uploadAttachment(String testCaseRunId, java.io.File file) {
        given()
                .header("Authorization", authHeader())
                .multiPart("file", file)
                .post("/test_case_runs/" + testCaseRunId + "/attachments");
    }

    public static List<Map<String, Object>> getTestRuns(String projectId, String search) {
        Response response = given()
                .header("Authorization", authHeader())
                .queryParam("search", search)
                .get("/projects/" + projectId + "/test_runs");

        List<Map<String, Object>> runs = response.jsonPath().getList("data.test_runs");
        return runs != null ? runs : Collections.emptyList();
    }

    public static Map<String, String> createTestCase(String projectId,
                                                     String title,
                                                     String description,
                                                     List<String> labelIds,
                                                     String folderId,
                                                     String preconditions,
                                                     String steps,
                                                     String expectedResults) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("template_type", "TCD");
        body.put("type_id", TestSigmaConfig.typeId());
        body.put("status_id", TestSigmaConfig.statusId());
        body.put("automation_type_id", TestSigmaConfig.automationTypeId());
        body.put("priority_id", TestSigmaConfig.priorityId());
        body.put("folder_id", folderId != null ? folderId : TestSigmaConfig.folderNegative());
        body.put("project_id", projectId);
        body.put("preconditions", preconditions != null ? preconditions : "");
        body.put("steps", steps != null ? steps : "");
        body.put("expected_results", expectedResults != null ? expectedResults : "");
        if (labelIds != null && !labelIds.isEmpty()) {
            body.put("label_ids", labelIds);
        }

        Response response = given()
                .header("Authorization", authHeader())
                .contentType("application/json")
                .body(body)
                .post("/projects/" + projectId + "/test_cases");

        System.out.println("[TestSigma] Create TC [" + response.getStatusCode() + "]: " + title);
        if (response.getStatusCode() >= 400) {
            System.out.println("[TestSigma]   ERROR: " + response.jsonPath().getString("errors.message"));
        }

        Map<String, String> result = new HashMap<>();
        result.put("humanId", response.jsonPath().getString("data.test_case.human_id"));
        result.put("uuid", response.jsonPath().getString("data.test_case.id"));
        return result;
    }

    public static boolean updateTestCase(String projectId,
                                         String uuid,
                                         String humanId,
                                         String title,
                                         String description,
                                         List<String> labelIds,
                                         String folderId,
                                         String preconditions,
                                         String steps,
                                         String expectedResults) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("folder_id", folderId);
        body.put("preconditions", preconditions != null ? preconditions : "");
        body.put("steps", steps != null ? steps : "");
        body.put("expected_results", expectedResults != null ? expectedResults : "");
        if (labelIds != null && !labelIds.isEmpty()) {
            body.put("label_ids", labelIds);
        }

        Response response = given()
                .header("Authorization", authHeader())
                .contentType("application/json")
                .body(body)
                .put("/projects/" + projectId + "/test_cases/" + uuid);

        if (response.getStatusCode() >= 400) {
            System.out.println("[TestSigma] Update failed " + humanId + " [" + response.getStatusCode() + "]: "
                    + response.getBody().asString());
            return false;
        }
        return true;
    }

    public static Map<String, String> lookupHumanId(String projectId, String humanId) {
        Response response = given()
                .header("Authorization", authHeader())
                .queryParam("search", humanId)
                .get("/projects/" + projectId + "/test_cases");

        List<Map<String, Object>> list = response.jsonPath().getList("data.test_cases");
        if (list == null) return null;

        for (Map<String, Object> tc : list) {
            if (humanId.equalsIgnoreCase((String) tc.get("human_id"))) {
                Map<String, String> result = new HashMap<>();
                result.put("humanId", humanId);
                result.put("uuid", (String) tc.get("id"));
                return result;
            }
        }
        return null;
    }

    public static List<Map<String, Object>> getTestCaseSteps(String projectId, String testCaseId) {
        Response response = given()
                .header("Authorization", authHeader())
                .get("/projects/" + projectId + "/test_cases/" + testCaseId);

        return response.jsonPath().getList("data.test_steps");
    }

    public static List<String> extractStepTexts(List<Map<String, Object>> steps) {
        List<String> list = new ArrayList<>();
        if (steps == null) return list;
        for (Map<String, Object> step : steps) {
            String text = (String) step.get("description");
            if (text != null) list.add(text);
        }
        return list;
    }

    public static List<String> getTestCaseStepsText(String projectId, String testCaseId) {
        Response response = given()
                .header("Authorization", authHeader())
                .get("/projects/" + projectId + "/test_cases/" + testCaseId);

        String steps = response.jsonPath().getString("data.test_case.steps");
        List<String> list = new ArrayList<>();
        if (steps == null) return list;
        for (String s : steps.split("\\n")) {
            if (!s.trim().isEmpty()) list.add(s.trim());
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public static List<String> getLabels(String projectId, String testCaseId) {
        Response response = given()
                .header("Authorization", authHeader())
                .get("/projects/" + projectId + "/test_cases/" + testCaseId);

        List<Map<String, Object>> labels = response.jsonPath().getList("data.test_case.labels");
        List<String> list = new ArrayList<>();
        if (labels == null) return list;
        for (Map<String, Object> l : labels) {
            list.add((String) l.get("name"));
        }
        return list;
    }

    public static void updateTestCaseStatus(String runId, String testCaseResultId, TestCaseStatus status) {
        List<Map<String, Object>> body = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("testCaseResultId", testCaseResultId);
        item.put("comment", "Automation Update");
        item.put("result", status.id());
        body.add(item);

        given()
                .header("Authorization", authHeader())
                .contentType("application/json")
                .body(body)
                .put("/executionresults/" + runId + "/override");
    }

    public static String getTitle(String projectId, String testCaseId) {
        Response response = given()
                .header("Authorization", authHeader())
                .get("/projects/" + projectId + "/test_cases/" + testCaseId);

        return response.jsonPath().getString("data.test_case.title");
    }
}
