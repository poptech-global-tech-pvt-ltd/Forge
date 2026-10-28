package com.popclub.cardapi.impl;

import com.popclub.cardapi.util.ConfigManager;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class BaseService {

    private String token;

    // Common setup for every request
    protected RequestSpecification buildSpec() {
        RequestSpecification spec = RestAssured.given()
                .filter(new AllureRestAssured())
                .baseUri(ConfigManager.getBaseUrl())
                .header("X-Source-Api-Key", ConfigManager.getXSourceApiKey())
                .contentType(ContentType.JSON);

        if (token != null) {
            spec.header("X-Auth-Token", token);
        }
        return spec;
    }

    protected Response post(String path, Object body) {
        return buildSpec().body(body).when().post(path).then().log().ifError().extract().response();
    }

    protected Response get(String path) {
        return buildSpec().when().get(path).then().log().ifError().extract().response();
    }

    public void attachToken(String token) { this.token = token; }

    public void reset() { this.token = null; }

    protected Response postRaw(String path, String rawBody) {
        return buildSpec().body(rawBody).when().post(path).then().log().ifError().extract().response();
    }
}