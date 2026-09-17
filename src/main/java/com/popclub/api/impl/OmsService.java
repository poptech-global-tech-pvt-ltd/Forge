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

    /**
     * Opens the OMS SSE stream for an order and collects raw event lines until
     * either a line containing expectedStatus is seen, or timeout elapses.
     */
    public java.util.List<String> listenToOrderStream(String orderNumber, String expectedStatus, java.time.Duration timeout) throws java.io.IOException, InterruptedException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(Routes.OMS_STREAM.kongUrl(orderNumber)))
                .header("Authorization", "Bearer " + accessToken)
                .header("X-RequestId", UUID.randomUUID().toString())
                .header("Accept", "text/event-stream")
                .timeout(timeout)
                .GET()
                .build();

        java.net.http.HttpResponse<java.io.InputStream> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());

        java.util.List<String> events = new java.util.ArrayList<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while (System.currentTimeMillis() < deadline) {
                line = reader.readLine();
                if (line == null) break;
                if (line.isBlank()) continue;
                events.add(line);
                System.out.println("[SSE] " + line);
                if (expectedStatus != null && (line.contains(expectedStatus) || line.contains("FAILED"))) break;
            }
        } catch (java.io.IOException e) {
            System.out.println("[SSE] stream closed/timed out: " + e.getMessage());
        }
        return events;
    }
}
