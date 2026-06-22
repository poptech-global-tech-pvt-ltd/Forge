package com.popclub.api.actions;

import com.popclub.android.actions.Action;
import com.popclub.core.LoggerUtil;
import com.popclub.core.TestContext;
import com.popclub.model.Step;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * fetchApi — makes an HTTP request using REST Assured and stores the response
 * (or extracted fields) as TestContext variables for use in later steps.
 *
 * YAML syntax:
 *
 *   # GET — store full response body
 *   - action: fetchApi
 *     url: "http://10.0.2.2:8080/api/v1/cart"
 *     variable: cart_json
 *
 *   # POST with JSON body
 *   - action: fetchApi
 *     url: "http://10.0.2.2:8080/api/v1/order"
 *     method: POST
 *     body: '{"item_id": "${item_id}"}'
 *     variable: order_response
 *
 *   # Extract specific fields via JsonPath (dot-notation)
 *   - action: fetchApi
 *     url: "http://10.0.2.2:8080/api/v1/user"
 *     extract:
 *       user_name: "data.name"
 *       user_id:   "data.id"
 *       first_item: "items[0].title"
 *
 *   # With custom headers
 *   - action: fetchApi
 *     url: "http://10.0.2.2:8080/api/v1/orders"
 *     headers:
 *       Authorization: "Bearer ${auth_token}"
 *     extract:
 *       order_id: "orders[0].id"
 */
public class FetchApiAction implements Action {

    @Override
    public void perform(Step step) {

        String url = interpolate(step.url);
        if (url == null || url.isBlank())
            throw new RuntimeException("fetchApi: 'url' is required");

        String method  = step.method != null ? step.method.toUpperCase() : "GET";
        String bodyStr = step.body != null ? interpolate(step.body) : null;

        LoggerUtil.step("[fetchApi] " + method + " " + url);

        // Build request spec
        RequestSpecification req = RestAssured.given()
                .header("Accept", "application/json");

        // Auto-inject auth token captured during login — skip if caller already set Authorization
        boolean callerSetAuth = step.headers != null &&
                step.headers.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("Authorization"));
        if (!callerSetAuth) {
            String token = TestContext.getUserToken();
            if (token != null && !token.isBlank()) {
                req.header("Authorization", "Bearer " + token);
            }
        }

        if (bodyStr != null) {
            req.contentType("application/json").body(bodyStr);
        }

        if (step.headers != null) {
            step.headers.forEach((k, v) -> req.header(k, interpolate(v)));
        }

        // Execute
        Response response = switch (method) {
            case "POST"   -> req.post(url);
            case "PUT"    -> req.put(url);
            case "DELETE" -> req.delete(url);
            case "PATCH"  -> req.patch(url);
            default       -> req.get(url);
        };

        int    status       = response.statusCode();
        String responseBody = response.getBody().asString();

        LoggerUtil.step("[fetchApi] status=" + status
                + "  length=" + responseBody.length() + " chars");

        if (status < 200 || status >= 300) {
            throw new RuntimeException(
                "fetchApi: HTTP " + status + " from " + url
                + "\n" + truncate(responseBody, 400));
        }

        // Store full response body in variable if requested
        if (step.variable != null && !step.variable.isBlank()) {
            TestContext.setScalarData(step.variable, responseBody);
            System.out.println("[fetchApi] stored full response → ${" + step.variable + "}");
        }

        // Extract individual fields via REST Assured's built-in JsonPath
        if (step.extract != null && !step.extract.isEmpty()) {
            for (Map.Entry<String, String> entry : step.extract.entrySet()) {
                String varName  = entry.getKey();
                String jsonPath = entry.getValue();
                try {
                    Object raw   = response.jsonPath().get(jsonPath);
                    String value = raw != null ? String.valueOf(raw) : "";
                    TestContext.setScalarData(varName, value);
                    System.out.printf("[fetchApi] extract ${%s} = \"%s\"  (path: %s)%n",
                            varName, value, jsonPath);
                } catch (Exception e) {
                    System.out.printf("[fetchApi] ⚠ extract ${%s}: path \"%s\" not found — set to \"\"  (%s)%n",
                            varName, jsonPath, e.getMessage());
                    TestContext.setScalarData(varName, "");
                }
            }
        }

        // Always log the response so it's visible in Forge log panel
        System.out.println("[fetchApi] response: " + truncate(responseBody, 600));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String interpolate(String s) {
        if (s == null || !s.contains("${")) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int start = s.indexOf("${", i);
            if (start < 0) { sb.append(s, i, s.length()); break; }
            sb.append(s, i, start);
            int end = s.indexOf("}", start);
            if (end < 0) { sb.append(s, start, s.length()); break; }
            String key   = s.substring(start + 2, end);
            String value = TestContext.getScalarData(key);
            sb.append(value != null ? value : "");
            i = end + 1;
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "(null)";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
