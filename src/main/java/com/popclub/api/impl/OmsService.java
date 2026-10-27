package com.popclub.api.impl;

import com.popclub.api.dto.tuition.CreateTuitionOrderRequest;
import com.popclub.api.enums.Routes;
import com.popclub.api.util.ConfigManager;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    public List<String> listenToOrderStream(String orderNumber, String expectedStatus, Duration timeout)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Routes.OMS_STREAM.kongUrl(orderNumber)))
                .header("Authorization", "Bearer " + accessToken)
                .header("X-RequestId", UUID.randomUUID().toString())
                .header("Accept", "text/event-stream")
                .timeout(timeout)
                .GET()
                .build();

        HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        List<String> events = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while (System.currentTimeMillis() < deadline) {
                line = reader.readLine();
                if (line == null) break;
                if (line.isBlank()) continue;
                events.add(line);
                System.out.println("[SSE] " + line);
                if (expectedStatus != null && (line.contains(expectedStatus) || line.contains("FAILED"))) break;
            }
        } catch (IOException e) {
            System.out.println("[SSE] stream closed/timed out: " + e.getMessage());
        }
        return events;
    }

    /**
     * Scans raw SSE event lines (as collected by {@link #listenToOrderStream})
     * for the first state_change event carrying a payment_link, and returns it.
     * Returns null if no event in the list has one.
     */
    public static String extractPaymentLink(List<String> events) {
        for (String event : events) {
            if (!event.startsWith("data:") || !event.contains("\"payment_link\"")) {
                continue;
            }
            String json = event.substring(event.indexOf('{'));
            String link = JsonPath.from(json).getString("data.payment_details.payment_link");
            if (link != null) {
                return link;
            }
        }
        return null;
    }
}
