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
 * Two base URLs are used across the collections:
 *   rcbp.base.url     → catalogue, recharge, CC, prepaid initiate  (base())
 *   rcbp.api.base.url → postpaid+prepaid bill fetch / payment       (apiBase())
 *
 * Authorization is DISABLED on every request. Auth is via X-Userid only.
 *
 * Spec builder → X-Userid mapping:
 *   buildSpecWithUserId()       → X-Userid: {{user_id}}  (CC list billers, postpaid catalogue, prepaid initiate)
 *   buildSpecWithCcInputId()    → X-Userid: 019a97c6-... (CC input fields, bills, initiate, confirm)
 *   buildSpecWithOperatorId()   → X-Userid: 019cfef7-... (operator-circle, prepaid plans)
 *   buildApiSpecWithOperatorId()→ X-Userid: 019cfef7-... + rcbp.api.base.url (postpaid+prepaid bills/payment)
 *   buildRechargeSpec(userid)   → X-Userid: supplied by caller (legacy — {{X-Userid}} for operators/states)
 */
public abstract class RcbpBaseService {

    // ── Spec builders (rcbp.base.url) ─────────────────────────────────────────

    /**
     * X-Userid: {{user_id}}.
     * Used by: CC list billers, postpaid list billers + input fields, prepaid initiate.
     */
    protected RequestSpecification buildSpecWithUserId() {
        return base()
                .header("X-Userid", RcbpConfigManager.getUserId());
    }

    /**
     * X-Userid: 019a97c6-d10a-745c-b303-e39b963e540d (CC-specific hardcoded UUID).
     * Used by: CC input fields, CC fetch bills, CC initiate payment, CC confirm payment.
     */
    protected RequestSpecification buildSpecWithCcInputId() {
        return base()
                .header("X-Userid", RcbpConfigManager.getCcInputUserId());
    }

    /**
     * X-Userid: 019cfef7-10b7-7cdc-8077-b96cf69e39d6 (operator UUID).
     * Used by: operator-circle, prepaid fetch plans.
     */
    protected RequestSpecification buildSpecWithOperatorId() {
        return base()
                .header("X-Userid", RcbpConfigManager.getOperatorUserid());
    }

    /**
     * Recharge spec — X-Userid supplied by caller.
     * Kept for: fetch operators/states (X-Userid: {{X-Userid}}).
     */
    protected RequestSpecification buildRechargeSpec(String xUserid) {
        return base()
                .header("X-Userid", xUserid);
    }

    // ── Spec builders (rcbp.api.base.url) ────────────────────────────────────

    /**
     * Uses rcbp.api.base.url + X-Userid: 019cfef7-10b7-7cdc-8077-b96cf69e39d6.
     * Used by: postpaid+prepaid bill fetch, payment initiate, payment confirm.
     */
    protected RequestSpecification buildApiSpecWithOperatorId() {
        return apiBase()
                .header("X-Userid", RcbpConfigManager.getOperatorUserid());
    }

    // ── Private base helpers ──────────────────────────────────────────────────

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

    // ── Legacy aliases (kept to avoid breaking existing callers) ─────────────

    /** @deprecated use buildSpecWithUserId() */
    protected RequestSpecification buildCreditCardSpec()             { return buildSpecWithUserId(); }

    /** @deprecated use buildSpecWithOperatorId() or buildApiSpecWithOperatorId() */
    protected RequestSpecification buildMobilePostpaidSpec()         { return buildSpecWithUserId(); }

    /** @deprecated use buildSpecWithUserId() */
    protected RequestSpecification buildMobilePostpaidInputFieldsSpec() { return buildSpecWithUserId(); }

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
