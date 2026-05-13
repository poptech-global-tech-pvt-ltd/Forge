package com.popclub.api.impl;

import com.popclub.api.enums.HttpMethod;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class PopApiClient {

    private static final Logger log = LoggerFactory.getLogger(PopApiClient.class);

    static { RestAssured.useRelaxedHTTPSValidation(); }

    public static Response executeRequest(HttpMethod method, PopRequestSpecification spec) {
        RequestSpecBuilder builder = new RequestSpecBuilder();

        if (spec.getContentType() != null) {
            builder.setContentType(spec.getContentType());
        }

        if (spec.getBody() != null) {
            builder.setBody(spec.getBody());
        }

        Map<String, String> headers = new HashMap<>();
        if (spec.getHeaders() != null) {
            headers.putAll(spec.getHeaders());
        }
        if (spec.getAuthorization() != null && spec.getAuthorization().header() != null) {
            headers.put("Authorization", spec.getAuthorization().header());
        }
        if (!headers.isEmpty()) {
            builder.addHeaders(headers);
        }

        RequestSpecification reqSpec = builder.build();

        log.info("[PopApiClient] {} {}", method, spec.getBaseUrl());

        return execute(method, spec.getBaseUrl(), reqSpec);
    }

    private static Response execute(HttpMethod method, String url, RequestSpecification spec) {
        try {
            switch (method) {
                case GET:    return RestAssured.given().spec(spec).get(url).then().log().ifError().extract().response();
                case POST:   return RestAssured.given().spec(spec).post(url).then().log().ifError().extract().response();
                case PUT:    return RestAssured.given().spec(spec).put(url).then().log().ifError().extract().response();
                case PATCH:  return RestAssured.given().spec(spec).patch(url).then().log().ifError().extract().response();
                case DELETE: return RestAssured.given().spec(spec).delete(url).then().log().ifError().extract().response();
                default: throw new IllegalArgumentException("Unknown method: " + method);
            }
        } catch (Exception e) {
            log.error("[PopApiClient] Request failed: {} {}", method, url, e);
            throw new RuntimeException("API call failed: " + method + " " + url, e);
        }
    }
}
