package com.popclub.testsigma;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.io.File;
import java.util.*;

import static io.restassured.RestAssured.given;

public class TestSigmaClient {

    public static final String USER_ID =
            "05782b39-ed88-4bd5-84e2-705bb1610da2";

    private static final String BASE_URL = "https://test-management.testsigma.com/api/v1";
    private static final String TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJUTVMiLCJzdWIiOiI3NDZkNDE2Yy0yZGUwLTQwMDUtOTI4My00MmYzMWFjZjA4ZGQiLCJuYmYiOjE3NzQzNTk4NDIsImlhdCI6MTc3NDM1OTg0MiwiaWRfc2Vzc2lvbl9yZV92YWxpZGF0ZSI6MH0.tjioTl0IlMsJK9fpU0HqivYMBQRTab8qyf2B5_sr-yk";

    static {
        RestAssured.baseURI = BASE_URL;
    }

    // ===============================
    // CREATE RUN
    // ===============================
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
                Map.of(
                        "field", "id",
                        "operator", "IN",
                        "values", testCaseIds
                )
        ));

        Response response =
                given()
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .body(body)
                        .post("/projects/" + projectId + "/test_runs");

        System.out.println("Create Run Response: " + response.asPrettyString());

        return response.jsonPath().getString("data.test_run.id");
    }

    // ===============================
    // UPDATE RUN STATUS
    // ===============================
    public static void updateRunStatus(String projectId,
                                       String runId,
                                       RunStatus status) {

        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());

        Response response =
                given()
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .body(body)
                        .patch("/projects/" + projectId + "/test_runs/" + runId);

        System.out.println("Update Run Response: " + response.asPrettyString());
    }

    // ===============================
    // GET TEST CASE UUID FROM HUMAN ID
    // PO-6184 -> UUID
    // ===============================
    public static String getTestCaseIdByHumanId(String projectId, String humanId) {

        Response response =
                given()
                        .header("Authorization", "Bearer " + TOKEN)
                        .queryParam("search", humanId)
                        .get("/projects/" + projectId + "/test_cases");

        List<Map<String, Object>> list =
                response.jsonPath().getList("data.test_cases");

        for (Map<String, Object> tc : list) {

            String human = (String) tc.get("human_id");
            String id = (String) tc.get("id");

            if (humanId.equalsIgnoreCase(human)) {
                return id;
            }
        }

        throw new RuntimeException("TestCase not found: " + humanId);
    }



    public static Map<String, String> getTestCaseRunMap(String runId) {

        Map<String, String> map = new HashMap<>();

        System.out.println("Fetching test_case_runs for runId=" + runId);
        Response response =
                given()
                        .header("Authorization", "Bearer " + TOKEN)
                        .get("/test_runs/" + runId + "/test_case_runs");

        System.out.println("test_case_runs status: " + response.getStatusCode());
        System.out.println("test_case_runs response: " + response.asPrettyString());

        List<Map<String, Object>> list =
                response.jsonPath().getList("data.test_case_runs");

        if (list == null) return map;

        for (Map<String, Object> item : list) {

            String id = (String) item.get("id");

            Map<String,Object> tc =
                    (Map<String, Object>) item.get("test_case");

            String humanId =
                    (String) tc.get("human_id");
            String uuid =
                    (String) tc.get("id");

            map.put(humanId, id);
            if (uuid != null) {
                map.put(uuid, id);
            }

            System.out.println("Mapped: " + humanId + " → " + id);
        }

        return map;
    }

    public static void updateTestCaseRun(
            String projectId,
            String runId,
            String testCaseId,
            String statusId,
            String userId,
            long duration
    ) {
        try {
            System.out.println("Test case update called");

            String url = BASE_URL +
                    "/projects/" + projectId +
                    "/test_runs/" + runId +
                    "/test_cases";

            Map<String, Object> caseObj = new HashMap<>();
            caseObj.put("test_case_id", testCaseId);
            caseObj.put("test_run_status_id", statusId);
            caseObj.put("user_id", userId);
            caseObj.put("description", "Automation Execution");

            List<Map<String, Object>> cases = new ArrayList<>();
            cases.add(caseObj);

            Map<String, Object> body = new HashMap<>();
            body.put("test_run_cases", cases);

            Response response =
                    given()
                            .header("Authorization", "Bearer " + TOKEN)
                            .contentType("multipart/form-data")
                            .multiPart("data", new ObjectMapper().writeValueAsString(body))
                            .put(url);

            System.out.println("Update Test Case Status: " + response.getStatusCode());
            System.out.println("Update Test Case Response: " + response.asPrettyString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    // ===============================
    // UPLOAD ATTACHMENT
    // ===============================
    public static void uploadAttachment(String testCaseRunId, File file) {

        Response response =
                given()
                        .header("Authorization", "Bearer " + TOKEN)
                        .multiPart("file", file)
                        .post("/test_case_runs/" + testCaseRunId + "/attachments");

        System.out.println("Upload Response: " + response.asPrettyString());
    }

    public static void updateTestCaseStatus(
            String runId,
            String testCaseResultId,
            TestCaseStatus status
    ) {

        List<Map<String, Object>> body = new ArrayList<>();

        Map<String, Object> item = new HashMap<>();
        item.put("testCaseResultId", testCaseResultId);
        item.put("comment", "Automation Update");
        item.put("result", status.id());

        body.add(item);

        Response response =
                given()
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .body(body)
                        .put("/executionresults/" + runId + "/override");

        System.out.println("Update TestCase: " + response.asPrettyString());
    }

    // ===============================
// GET TEST CASE STEPS
// ===============================
    public static List<Map<String, Object>> getTestCaseSteps(
            String projectId,
            String testCaseId
    ) {

        Response response =
                given()
                        .header("Authorization", "Bearer " + TOKEN)
                        .get("/projects/" + projectId + "/test_cases/" + testCaseId);

        System.out.println("TestCase Steps Response: " + response.asPrettyString());

        return response.jsonPath().getList("data.test_steps");
    }

    // ===============================
// EXTRACT STEP TEXT
// ===============================
    public static List<String> extractStepTexts(
            List<Map<String, Object>> steps
    ) {

        List<String> list = new ArrayList<>();

        if (steps == null) return list;

        for (Map<String, Object> step : steps) {

            String text =
                    (String) step.get("description");

            if (text != null) {
                list.add(text);
            }
        }

        return list;
    }

    // ===============================
// GET TEST CASE STEPS (STRING)
// ===============================
    public static List<String> getTestCaseStepsText(
            String projectId,
            String testCaseId
    ) {

        Response response =
                given()
                        .header("Authorization", "Bearer " + TOKEN)
                        .get("/projects/" + projectId + "/test_cases/" + testCaseId);

        String steps =
                response.jsonPath()
                        .getString("data.test_case.steps");

        List<String> list = new ArrayList<>();

        if (steps == null) return list;

        String[] split = steps.split("\\n");

        for (String s : split) {
            if (!s.trim().isEmpty()) {
                list.add(s.trim());
            }
        }

        return list;
    }

    public static List<String> getLabels(
            String projectId,
            String testCaseId
    ) {

        Response response =
                given()
                        .header("Authorization", "Bearer " + TOKEN)
                        .get("/projects/" + projectId + "/test_cases/" + testCaseId);

        List<Map<String,Object>> labels =
                response.jsonPath()
                        .getList("data.test_case.labels");

        List<String> list = new ArrayList<>();

        if (labels == null) return list;

        for (Map<String,Object> l : labels) {
            list.add((String) l.get("name"));
        }

        return list;
    }

    public static String getTitle(
            String projectId,
            String testCaseId
    ) {

        Response response =
                given()
                        .header("Authorization", "Bearer " + TOKEN)
                        .get("/projects/" + projectId + "/test_cases/" + testCaseId);

        return response
                .jsonPath()
                .getString("data.test_case.title");
    }


}