package com.popclub.api.rcbp.impl;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public abstract class RcbpBaseService {

    protected RequestSpecification buildSpecWithUserId() {
        return base()
                .header("X-Userid", RcbpConfigManager.getUserId());
    }

    protected RequestSpecification buildSpecWithCcInputId() {
        return base()
                .header("X-Userid", RcbpConfigManager.getCcInputUserId());
    }

    protected RequestSpecification buildSpecWithOperatorId() {
        return base()
                .header("X-Userid", RcbpConfigManager.getOperatorUserid());
    }

    protected RequestSpecification buildRechargeSpec(String xUserid) {
        return base()
                .header("X-Userid", xUserid);
    }

    protected RequestSpecification buildApiSpecWithOperatorId() {
        return apiBase()
                .header("X-Userid", RcbpConfigManager.getOperatorUserid());
    }

    private RequestSpecification base() {
        return RestAssured.given()
                .baseUri(RcbpConfigManager.getBaseUrl())
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter());
    }

    private RequestSpecification apiBase() {
        return RestAssured.given()
                .baseUri(RcbpConfigManager.getApiBaseUrl())
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter());
    }

    // ── Invalid auth spec builders (for negative tests) ──────────────────────

    /**
     * Builds a spec with a bogus X-Userid on rcbp.base.url.
     * Used exclusively by negative tests to exercise 401 responses.
     */
    protected RequestSpecification buildSpecWithInvalidAuth() {
        return base()
                .header("X-Userid", "00000000-0000-0000-0000-000000000000");
    }

    /**
     * Builds a spec with a bogus X-Userid on rcbp.api.base.url.
     * Used exclusively by negative tests to exercise 401 responses on
     * the alternate base URL (postpaid/prepaid bills and payment).
     */
    protected RequestSpecification buildApiSpecWithInvalidAuth() {
        return apiBase()
                .header("X-Userid", "00000000-0000-0000-0000-000000000000");
    }

    // ── Negative test assertions ──────────────────────────────────────────────

    /**
     * Asserts that a response represents a client error (Option A, invalid input /
     * missing required fields):
     *   - HTTP status is 4xx (400–499)
     *   - is_success is false or absent
     *   - at least one of message / error is present and non-empty
     *   - data is null or absent (no resource created)
     */
    public static void assertClientError(Response response, String context) {
        int status = response.statusCode();
        assertTrue(status >= 400 && status < 500,
                context + " — expected 4xx status, got: " + status);

        Object isSuccess = response.jsonPath().get("is_success");
        assertTrue(isSuccess == null || Boolean.FALSE.equals(isSuccess),
                context + " — is_success must be false or absent in error response");

        String message = response.jsonPath().getString("message");
        String error   = response.jsonPath().getString("error");
        assertTrue((message != null && !message.isEmpty()) || (error != null && !error.isEmpty()),
                context + " — at least one of message or error must be present and non-empty");

        Object data = response.jsonPath().get("data");
        assertNull(data, context + " — data must be null or absent in error response");
    }

    /**
     * Asserts that a response represents an authentication failure:
     *   - HTTP status is exactly 401
     *   - data is null or absent (no business operation performed)
     */
    public static void assertUnauthorized(Response response, String context) {
        assertEquals(response.statusCode(), 401,
                context + " — expected 401 Unauthorized, got: " + response.statusCode());

        try {
            Object data = response.jsonPath().get("data");
            assertNull(data, context + " — data must be null in 401 response");
        } catch (Exception ignored) {
            // response body may not be JSON on 401 — acceptable
        }
    }

    // ── Legacy aliases (kept to avoid breaking existing callers) ─────────────

    /** @deprecated use buildSpecWithUserId() */
    protected RequestSpecification buildCreditCardSpec()                { return buildSpecWithUserId(); }

    /** @deprecated use buildSpecWithOperatorId() or buildApiSpecWithOperatorId() */
    protected RequestSpecification buildMobilePostpaidSpec()            { return buildSpecWithUserId(); }

    /** @deprecated use buildSpecWithUserId() */
    protected RequestSpecification buildMobilePostpaidInputFieldsSpec() { return buildSpecWithUserId(); }

    public static void assertStatus(Response response, int expected, String method, String path) {
        int actual = response.statusCode();
        if (actual != expected) {
            String body = response.body().asString();
            String message;
            try {
                message = response.jsonPath().getString("message");
            } catch (Exception e) {
                message = null;
            }
            if (message == null || message.isEmpty()) message = body;
            throw new AssertionError(method + " " + path + " | " + actual + " | " + message);
        }
    }
}
