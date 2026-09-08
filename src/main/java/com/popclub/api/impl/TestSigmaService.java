package com.popclub.api.impl;

import com.popclub.api.dto.TestSigmaLoginRequestDto;
import com.popclub.api.enums.Routes;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class TestSigmaService {

    private static final RestAssuredConfig RELAXED_SSL_CONFIG =
            RestAssuredConfig.config().sslConfig(SSLConfig.sslConfig().relaxedHTTPSValidation());

    public static Response login(String email, String password) {
        return given()
                .config(RELAXED_SSL_CONFIG)
                .header("accept", "application/json, text/plain, */*")
                .contentType("application/json")
                .body(new TestSigmaLoginRequestDto(email, password))
                .redirects().follow(false)
                .post(Routes.TESTSIGMA_LOGIN.testSigmaLoginUrl());
    }

    public static Response authorize(String redirectTo, String cookieHeader) {
        return given()
                .config(RELAXED_SSL_CONFIG)
                .header("Cookie", cookieHeader)
                .redirects().follow(false)
                .get(Routes.TESTSIGMA_AUTHORIZE.testSigmaLoginUrl()
                        + "?redirectTo=" + URLEncoder.encode(redirectTo, StandardCharsets.UTF_8));
    }

    public static Response exchangeToken(String token, String redirectTo, String cookieHeader) {
        String formBody = "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&redirectTo=" + URLEncoder.encode(redirectTo, StandardCharsets.UTF_8);

        return given()
                .config(RELAXED_SSL_CONFIG)
                .header("Cookie", cookieHeader)
                .contentType("application/x-www-form-urlencoded")
                .body(formBody)
                .redirects().follow(false)
                .post(Routes.TESTSIGMA_TOKEN_EXCHANGE.testSigmaAppUrl());
    }

    public static Response graphqlMultipart(String baseUrl, String sessionCookie,
                                             String operations, String map, java.io.File file) {
        String mimeType = detectMimeType(file);
        return given()
                .config(RELAXED_SSL_CONFIG)
                .baseUri(baseUrl)
                .header("Cookie", "X-TMS-SESSION-ID=" + sessionCookie)
                .multiPart("operations", operations)
                .multiPart("map", map)
                .multiPart("0", file, mimeType)
                .post("/private/graphql");
    }

    private static String detectMimeType(java.io.File file) {
        try {
            String detected = java.nio.file.Files.probeContentType(file.toPath());
            if (detected != null) return detected;
        } catch (Exception ignored) {}
        return "application/octet-stream";
    }

    public static Response graphqlMutation(String baseUrl, String sessionCookie, Map<String, Object> body) {
        return given()
                .config(RELAXED_SSL_CONFIG)
                .baseUri(baseUrl)
                .header("Cookie", "X-TMS-SESSION-ID=" + sessionCookie)
                .contentType("application/json")
                .body(body)
                .post("/private/graphql");
    }
}
