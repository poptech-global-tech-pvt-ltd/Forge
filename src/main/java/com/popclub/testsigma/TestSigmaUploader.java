package com.popclub.testsigma;

import java.io.File;

import static io.restassured.RestAssured.given;

public class TestSigmaUploader {
    public static void upload(File file, int testCaseId) {

        given()
                .baseUri("https://api.testsigma.com/api/v1")
                .header("Authorization", "Bearer YOUR_API_KEY")
                .multiPart("file", file)
                .multiPart("test_case_id", testCaseId)
                .when()
                .post("/attachments")
                .then()
                .statusCode(200);

        System.out.println("Uploaded: " + file.getName());
    }
}
