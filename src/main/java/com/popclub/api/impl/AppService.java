package com.popclub.api.impl;

import com.popclub.api.dto.LoginResult;
import com.popclub.api.util.ApiConstants;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Base service for POP app API tests (search, shop, home, UPI, …).
 *
 * Auth headers (from Pluto cURL):
 *   Authorization:   Token <legacyToken>      — DRF session token
 *   X-Access-Token:  Token <jwtToken>         — Hashira JWT
 *
 * App headers sent on every request:
 *   platform:        android
 *   x-app-version:   (configurable, defaults to current stable)
 *
 * Base URL: APP_API_URL from ApiConstants (https://app.popclub.co.in/api/)
 *
 * Usage:
 *   SearchService search = new SearchService();
 *   search.attachTokens(AuthApiClient.loginFull("1234561122", "560102"));
 */
public class AppService {

    private static final String APP_VERSION = "237";

    private String legacyToken;
    private String jwtToken;

    // ── Auth ──────────────────────────────────────────────────────────────────

    public void attachTokens(LoginResult result) {
        this.legacyToken = result.legacyToken;
        this.jwtToken    = result.jwtToken;
    }

    public void reset() {
        this.legacyToken = null;
        this.jwtToken    = null;
    }

    // ── Spec builder ──────────────────────────────────────────────────────────

    protected RequestSpecification buildSpec() {
        RequestSpecification spec = RestAssured.given()
                .baseUri(ApiConstants.APP_API_URL)
                .contentType(ContentType.JSON)
                .header("platform",       "android")
                .header("x-app-version",  APP_VERSION)
                .header("app_version",    APP_VERSION)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter());

        if (legacyToken != null && !legacyToken.isBlank())
            spec.header("Authorization",  "Token " + legacyToken);

        if (jwtToken != null && !jwtToken.isBlank())
            spec.header("X-Access-Token", "Token " + jwtToken);

        return spec;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    protected Response get(String path) {
        return buildSpec().get(path);
    }

    protected Response post(String path, Object body) {
        return buildSpec().body(body).post(path);
    }

    protected Response put(String path, Object body) {
        return buildSpec().body(body).put(path);
    }

    protected Response delete(String path) {
        return buildSpec().delete(path);
    }
}
