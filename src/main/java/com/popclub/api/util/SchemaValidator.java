package com.popclub.api.util;

import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;

public class SchemaValidator {

    private static final String SCHEMA_DIR = "schemas/";

    public static void validate(Response response, String schemaFile) {
        response.then().assertThat()
                .body(JsonSchemaValidator.matchesJsonSchemaInClasspath(SCHEMA_DIR + schemaFile));
    }
}
