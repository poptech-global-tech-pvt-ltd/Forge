package com.popclub.api.rcbp.impl;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

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
