package com.popclub.api.impl;

import com.popclub.api.dto.tuition.CreateTuitionOrderRequest;
import com.popclub.api.enums.Routes;
import com.popclub.api.util.ConfigManager;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.UUID;

public class OmsService {

    private String accessToken;

    public void attachToken(String token) { this.accessToken = token; }

    private RequestSpecification buildSpec() {
        RequestSpecification spec = RestAssured.given()
                .baseUri(ConfigManager.getKongBaseUrl())
                .contentType(ContentType.JSON)
                .header("X-RequestId", UUID.randomUUID().toString())
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter());
        if (accessToken != null) {
            spec.header("Authorization", "Bearer " + accessToken);
        }
        return spec;
    }

    public Response createTuitionOrder(CreateTuitionOrderRequest req) {
        return buildSpec().body(req).post(Routes.OMS_ORDERS.getPath());
    }

    public Response listOrders() {
        return buildSpec().get(Routes.OMS_ORDERS.getPath());
    }

    public Response getOrder(String orderNumber) {
        return buildSpec().get(Routes.OMS_GET_ORDER.kongUrl(orderNumber));
    }

    public Response getOrderStatus(String orderNumber) {
        return buildSpec().get(Routes.OMS_ORDER_STATUS.kongUrl(orderNumber));
    }

    public Response respondToOrder(String orderNumber, Object body) {
        return buildSpec().body(body).post(Routes.OMS_ACCEPTANCE_RESPONSE.kongUrl(orderNumber));
    }

    public Response pollOrderStatus(String orderNumber, String expectedStatus, int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            Response r = getOrderStatus(orderNumber);
            String status = r.jsonPath().getString("data.status");
            if (expectedStatus.equals(status)) return r;
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        }
        return getOrderStatus(orderNumber);
    }
}
