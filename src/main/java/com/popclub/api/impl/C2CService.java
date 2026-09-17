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

import java.util.UUID;

public class C2CService {

    private String accessToken;

    public void attachToken(String token) { this.accessToken = token; }

    private RequestSpecification buildSpec() {
        RequestSpecification spec = RestAssured.given()
                .baseUri(ConfigManager.getKongBaseUrl())
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter());

        if (accessToken != null) {
            spec.header("Authorization", "Bearer " + accessToken);
        }

        spec.header("X-Userid", "ee17b17a8ba7533a93189013508ac3b6");

        return spec;
    }

    public Response getPayeeList(String search) {
        return buildSpec()
                .queryParam("page", 1)
                .queryParam("limit", 10)
                .queryParam("search", search)
                .queryParam("search_field", "mobile")
                .get(Routes.CSVC_PAYEE_LIST.getPath());
    }

    public Response getPayeeBasicInfo(PayeeBasicInfoRequest req) {
        return buildSpec().body(req).post(Routes.CSVC_PAYEE_BASIC_INFO.getPath());
    }

    public Response verifyPayee(VerifyPayeeRequestDto req) {
        return buildSpec().body(req).post(Routes.CSVC_PAYEE_VERIFICATION.getPath());

    }

    public Response getPaymentSummary(String payeeDisplayId, double amount) {
        return buildSpec()
                .queryParam("payee_display_id", payeeDisplayId)
                .queryParam("amount", amount)
                .get(Routes.CSVC_PAYMENT_SUMMARY.getPath());
    }

    public Response generatePaymentIntent(GeneratePaymentIntentRequest req) {
        return buildSpec()
                .header("request-id", UUID.randomUUID().toString())
                .header("X-RequestId", UUID.randomUUID().toString())
                .body(req)
                .post(Routes.CSVC_PAYMENT_INTENT.getPath());
    }

    public Response verifyPaymentIntent(String paymentIntentId, Object body) {
        return buildSpec().body(body)
                .post(Routes.CSVC_PAYMENT_INTENT_VERIFY.kongUrl(paymentIntentId));
    }

    public Response getPaymentIntentStatus(String paymentIntentId) {
        return buildSpec().get(Routes.CSVC_PAYMENT_INTENT_STATUS.kongUrl(paymentIntentId));
    }

    public Response reserveLimit(ReserveLimitRequest req) {
        return buildSpec().body(req).post(Routes.CSVC_LIMIT_RESERVE.getPath());
    }

    public Response releaseLimit(LimitActionRequest req) {
        return buildSpec().body(req).post(Routes.CSVC_LIMITS_RELEASE.getPath());
    }

    public Response consumeLimit(LimitActionRequest req) {
        return buildSpec().body(req).post(Routes.CSVC_LIMITS_CONSUME.getPath());
    }
}
