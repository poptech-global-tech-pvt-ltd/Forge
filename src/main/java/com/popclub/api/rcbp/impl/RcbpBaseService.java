package com.popclub.api.rcbp.impl;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Base service for RCBP APIs.
 *
 * All requests share {{pop-rcbp-base}} as the base URL.
 * The Authorization header ({{rcbp_token}}) is DISABLED on every request in the
 * collection — no token is sent. Auth is done exclusively via X-Userid headers,
 * whose variable name differs per request group:
 *
 *   buildCreditCardSpec()       → X-Userid: {{user_id}}
 *   buildMobilePostpaidSpec()   → X-Userid: {{userid}}
 *   buildMobilePostpaidInputFieldsSpec() → phone: {{phone}} + X-Userid: {{userid}}
 *   buildRechargeSpec(userid)   → X-Userid: supplied by caller ({{X-Userid}} or hardcoded)
 *   buildNoAuthSpec()           → X-Userid: {{X-Userid}}, Postman auth type = "noauth"
 *                                 (used for fetch operators/states — no Authorization token)
 */
public abstract class RcbpBaseService {

    // ── Spec builders ─────────────────────────────────────────────────────────

    /** Credit card requests — X-Userid: {{user_id}} */
    protected RequestSpecification buildCreditCardSpec() {
        return base()
                .header("X-Userid", RcbpConfigManager.getUserId());
    }

    /** Mobile postpaid billers + fetch bill — X-Userid: {{userid}} */
    protected RequestSpecification buildMobilePostpaidSpec() {
        return base()
                .header("X-Userid", RcbpConfigManager.getUserid());
    }

    /**
     * Mobile postpaid input fields — phone header + X-Userid: {{userid}}.
     * The collection sends a "phone" request header (not a query param) on this
     * specific request, alongside X-Userid.
     */
    protected RequestSpecification buildMobilePostpaidInputFieldsSpec() {
        return base()
                .header("phone",    RcbpConfigManager.getPhone())
                .header("X-Userid", RcbpConfigManager.getUserid());
    }

    /**
     * Recharge spec — X-Userid supplied by caller.
     * Used because the collection uses different X-Userid values per recharge request:
     *   - fetch operators/states → {{X-Userid}}
     *   - operator-circle        → hardcoded UUID (019cfef7-10b7-7cdc-8077-b96cf69e39d6)
     *   - fetch plans            → {{X-Userid}}
     */
    protected RequestSpecification buildRechargeSpec(String xUserid) {
        return base()
                .header("X-Userid", xUserid);
    }

    private RequestSpecification base() {
        return RestAssured.given()
                .baseUri(RcbpConfigManager.getBaseUrl())
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter());
    }

    // ── Status assertion (mirrors BaseService pattern) ────────────────────────

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
