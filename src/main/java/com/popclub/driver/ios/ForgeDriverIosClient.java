package com.popclub.driver.ios;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * ForgeDriverIosClient — talks to the XCTest HTTP server running on the iOS device/simulator.
 *
 * The UITest runner (ForgeDriverUITests) starts an HTTP server on port 7791.
 * On simulators the port is directly reachable on localhost.
 * On real devices, install.sh sets up iproxy forwarding to the same local port.
 *
 * Usage:
 *   ForgeDriverIosClient driver = new ForgeDriverIosClient();
 *   driver.tap("shop_add_to_cart_button");
 *   driver.waitUntilPresent("cart_item_price_0", 10_000);
 *
 * Architecture:
 *   Forge Java → HTTP → localhost:7791 → XCTest UITest → XCUIApplication → device
 */
public class ForgeDriverIosClient {

    private static final String BASE_URL   = "http://localhost:7791";
    private static final int    CONNECT_MS = 3_000;
    private static final int    REQUEST_MS = 30_000;

    private final HttpClient   http;
    private final ObjectMapper json = new ObjectMapper();

    public ForgeDriverIosClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(CONNECT_MS))
                .build();
    }

    // ── Health check ──────────────────────────────────────────────────────────

    public boolean isAlive() {
        try {
            Map<?, ?> res = get("/ping");
            return "ok".equals(res.get("status"));
        } catch (Exception e) {
            return false;
        }
    }

    // ── Stop server (signals XCTest runner to exit) ───────────────────────────

    public void stop() throws IOException {
        post("/stop", Map.of());
    }

    // ── Element presence ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Integer> waitUntilPresent(String tag, long timeoutMillis) {
        try {
            Map<?, ?> res = get("/present?tag=" + encode(tag) + "&timeout=" + timeoutMillis);
            if (Boolean.TRUE.equals(res.get("present"))) {
                return (Map<String, Integer>) res.get("bounds");
            }
        } catch (Exception e) {
            System.out.println("[ForgeDriveriOS] waitUntilPresent error: " + e.getMessage());
        }
        return null;
    }

    public boolean isPresent(String tag) {
        return waitUntilPresent(tag, 0) != null;
    }

    // ── Tap ──────────────────────────────────────────────────────────────────

    public void tap(String tag) throws IOException {
        post("/tap", Map.of("tag", tag));
    }

    public void tapByText(String text) throws IOException {
        post("/tap", Map.of("text", text));
    }

    public void tapByCoords(int x, int y) throws IOException {
        post("/tap", Map.of("x", x, "y", y));
    }

    // ── Type ─────────────────────────────────────────────────────────────────

    public void type(String text) throws IOException {
        post("/type", Map.of("text", text));
    }

    public void clearAndType(String text) throws IOException {
        post("/type", Map.of("text", text, "clear", true));
    }

    // ── Swipe ────────────────────────────────────────────────────────────────

    public void swipe(String direction) throws IOException {
        post("/swipe", Map.of("direction", direction));
    }

    public void swipe(int x1, int y1, int x2, int y2) throws IOException {
        post("/swipe", Map.of("x1", x1, "y1", y1, "x2", x2, "y2", y2));
    }

    // ── Keys ─────────────────────────────────────────────────────────────────

    public void pressBack()   throws IOException { post("/key", Map.of("key", "back"));   }
    public void pressHome()   throws IOException { post("/key", Map.of("key", "home"));   }
    public void pressEnter()  throws IOException { post("/key", Map.of("key", "enter"));  }
    public void pressSearch() throws IOException { post("/key", Map.of("key", "search")); }
    public void pressDelete() throws IOException { post("/key", Map.of("key", "delete")); }

    // ── UI source ─────────────────────────────────────────────────────────────

    public String getSource() throws IOException {
        Map<?, ?> res = get("/source");
        return (String) res.get("xml");
    }

    // ── Screenshot ────────────────────────────────────────────────────────────

    public byte[] screenshot() throws IOException {
        Map<?, ?> res = get("/screenshot");
        String b64 = (String) res.get("screenshot");
        return Base64.getDecoder().decode(b64);
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> search(String tag) throws IOException {
        Map<?, ?> res = get("/search?tag=" + encode(tag));
        return (java.util.List<Map<String, Object>>) res.get("nodes");
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private Map<?, ?> get(String path) throws IOException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofMillis(REQUEST_MS))
                .GET()
                .build();
        try {
            String body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
            return json.readValue(body, Map.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private Map<?, ?> post(String path, Map<?, ?> data) throws IOException {
        String reqBody = json.writeValueAsString(data);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofMillis(REQUEST_MS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();
        try {
            String resBody = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
            return json.readValue(resBody, Map.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
