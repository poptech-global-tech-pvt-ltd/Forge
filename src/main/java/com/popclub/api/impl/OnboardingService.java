package com.popclub.api.impl;

import com.popclub.api.dto.tuition.*;
import com.popclub.api.enums.Routes;
import com.popclub.api.util.ConfigManager;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public class OnboardingService {

    private String hashiraToken;

    public void attachToken(String token) { this.hashiraToken = token; }

    private RequestSpecification buildPublicSpec() {
        return RestAssured.given()
                .baseUri(ConfigManager.getKongBaseUrl())
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter());
    }

    private RequestSpecification buildSpec() {
        RequestSpecification spec = buildPublicSpec();
        if (hashiraToken != null) {
            spec.header("Authorization", "Bearer " + hashiraToken);
        }
        return spec;
    }

    public Response sendOtp(SendOtpRequest req) {
        return buildPublicSpec().body(req).post(Routes.ONBOARDING_SEND_OTP.getPath());
    }

    public Response verifyOtp(VerifyOtpRequest req) {
        return buildPublicSpec().body(req).post(Routes.ONBOARDING_VERIFY_OTP.getPath());
    }

    public Response getPayeeDetails(String inviteCode) {
        return buildSpec()
                .queryParam("code", inviteCode)
                .get(Routes.ONBOARDING_PAYEE_DETAILS.getPath());
    }

    public Response payeeConsent(PayeeConsentRequest req) {
        return buildSpec().body(req).post(Routes.ONBOARDING_PAYEE_CONSENT.getPath());
    }
}
