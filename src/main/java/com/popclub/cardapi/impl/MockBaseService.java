package com.popclub.cardapi.impl;

import com.popclub.cardapi.util.ConfigManager;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class MockBaseService {

    protected RequestSpecification buildMockSpec(String scenario) {
        RequestSpecification spec = RestAssured.given()
                .filter(new AllureRestAssured())
                .baseUri(ConfigManager.getMockBaseUrl())
                .contentType(ContentType.JSON)
                .header("X-Mock-Service", "cardhub")
                .header("X-Mock-Enabled", "true")
                .header("X-Mock-Env", "preprod-api");

        if (scenario != null && !scenario.isEmpty()) {
            spec.header("X-Mock-Scenario", scenario);
        }
        return spec;
    }

    protected Response post(String path, Object body, String scenario) {
        return buildMockSpec(scenario)
                .body(body)
                .when().post(path)
                .then().log().ifError()
                .extract().response();
    }
}
