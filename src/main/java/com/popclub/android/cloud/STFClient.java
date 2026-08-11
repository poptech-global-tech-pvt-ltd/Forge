package com.popclub.android.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class STFClient {

    private final String baseUrl;
    private String token;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Creates a client and auto-generates a fresh token by calling the mock-auth endpoint.
     * This ensures the token is always valid regardless of server restarts.
     *
     * @param baseUrl   e.g. https://10.25.11.235
     * @param authEmail e.g. testdevices@popclub.co
     * @param authName  e.g. Forge
     */
    // Token cache: keyed by "baseUrl|email" — shared across all STFClient instances in the JVM
    // and persisted to disk so the same token is reused across Maven test runs.
    private static final java.util.concurrent.ConcurrentHashMap<String, String> TOKEN_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public STFClient(String baseUrl, String authEmail, String authName) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .sslContext(trustAllSslContext())
                .build();
        this.token = acquireToken(authEmail, authName);
    }

    /**
     * Returns a cached token if available (in-memory or on-disk), otherwise fetches a new one.
     * Persisting to disk lets the same STF session release devices reserved by a previous run.
     */
    private String acquireToken(String email, String name) {
        String cacheKey = this.baseUrl + "|" + email;

        // 1. In-memory cache (same JVM / parallel threads)
        String cached = TOKEN_CACHE.get(cacheKey);
        if (cached != null) return cached;

        // 2. Disk cache — reuse across Maven test runs so we keep the same STF session
        java.io.File tokenFile = tokenCacheFile(cacheKey);
        if (tokenFile.exists()) {
            try {
                String saved = new String(java.nio.file.Files.readAllBytes(tokenFile.toPath())).trim();
                if (!saved.isBlank() && isTokenValid(saved)) {
                    System.out.println("[STF] Reusing saved session token for " + email);
                    TOKEN_CACHE.put(cacheKey, saved);
                    return saved;
                }
            } catch (Exception ignored) {}
        }

        // 3. Fetch a fresh token and persist it
        String fresh = fetchToken(email, name);
        TOKEN_CACHE.put(cacheKey, fresh);
        try {
            tokenFile.getParentFile().mkdirs();
            java.nio.file.Files.write(tokenFile.toPath(), fresh.getBytes());
        } catch (Exception ignored) {}
        return fresh;
    }

    private java.io.File tokenCacheFile(String cacheKey) {
        // Safe filename: replace non-alphanumeric with underscore
        String safeName = cacheKey.replaceAll("[^a-zA-Z0-9]", "_");
        return new java.io.File(System.getProperty("java.io.tmpdir"), "stf_token_" + safeName + ".txt");
    }

    /** Quick validity check: a JWT has 3 parts separated by dots. */
    private boolean isTokenValid(String token) {
        return token.split("\\.").length == 3;
    }

    /** Obtains a fresh JWT from the mock-auth endpoint. */
    private String fetchToken(String email, String name) {
        try {
            String body = String.format("{\"email\":\"%s\",\"name\":\"%s\"}", email, name);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/auth/api/v1/mock"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(response.body());
            String jwt = json.path("jwt").asText(null);
            if (jwt == null || jwt.isBlank()) {
                throw new RuntimeException("Auth response did not contain a JWT: " + response.body());
            }
            System.out.println("[STF] Authenticated as " + email);
            return jwt;
        } catch (Exception e) {
            throw new RuntimeException("[STF] Failed to authenticate with device farm at " + baseUrl, e);
        }
    }

    /** Builds an SSL context that trusts all certificates (safe for internal/intranet servers). */
    private static SSLContext trustAllSslContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new java.security.SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust-all SSL context", e);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns serials of devices that are present, ready, and not currently in use.
     * Optionally filtered by Android platform version.
     */
    public List<String> getAvailableDevices(String platformVersion) {
        try {
            String url = baseUrl + "/api/v1/devices?present=true&ready=true&using=false";
            JsonNode root = get(url);
            JsonNode devices = root.path("devices");

            List<String> serials = new ArrayList<>();
            for (JsonNode device : devices) {
                // Must be physically present and ready
                if (!device.path("present").asBoolean(false)) continue;
                if (!device.path("ready").asBoolean(false)) continue;

                // Skip non-Android
                String platform = device.path("platform").asText("");
                if (!"Android".equalsIgnoreCase(platform)) continue;

                // Optional platform-version filter
                if (platformVersion != null) {
                    String ver = device.path("version").asText("");
                    if (!ver.startsWith(platformVersion)) continue;
                }

                serials.add(device.path("serial").asText());
            }

            System.out.println("[STF] Available devices: " + serials);
            return serials;

        } catch (java.net.http.HttpConnectTimeoutException e) {
            System.err.println("[STF] Cannot reach device farm at " + baseUrl
                    + " — is pop-device-farm running? (./start.sh)");
            throw new RuntimeException("[STF] Device farm unreachable: " + baseUrl, e);
        } catch (Exception e) {
            System.err.println("[STF] Failed to list devices — cause: " + e.getClass().getName() + ": " + e.getMessage());
            throw new RuntimeException("[STF] Failed to list devices", e);
        }
    }

    /**
     * Returns platform name and version for a specific device e.g. ["Android", "14"]
     */
    public String[] getDevicePlatformInfo(String serial) {
        try {
            JsonNode root = get(baseUrl + "/api/v1/devices/" + serial);
            JsonNode device = root.path("device");
            String platform = device.path("platform").asText("Android");
            String version  = device.path("version").asText("");
            System.out.println("[STF] Device " + serial + " → platform: " + platform + " version: " + version);
            return new String[]{platform, version};
        } catch (Exception e) {
            System.err.println("[STF] Could not fetch platform info for " + serial + ": " + e.getMessage());
            return new String[]{"Android", ""};
        }
    }

    /**
     * Reserves the device with the given serial for the current user.
     *
     * @param serial  device serial / UDID
     * @param timeoutMs  how long to hold the reservation (milliseconds)
     */
    public void reserveDevice(String serial, long timeoutMs) {
        try {
            String body = String.format(
                    "{\"serial\":\"%s\"}", serial);
            JsonNode response = post(baseUrl + "/api/v1/user/devices", body);

            // STF returns { "description": "...", "success": true }
            boolean success = response.path("success").asBoolean(false);
            if (!success) {
                String desc = response.path("description").asText("unknown error");
                throw new RuntimeException("[STF] Device reservation failed: " + desc);
            }

            System.out.println("[STF] Reserved device: " + serial);

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("[STF] Failed to reserve device: " + serial, e);
        }
    }

    /**
     * Requests a remote ADB connection for the reserved device.
     *
     * @return  ADB-over-TCP address, e.g. "10.25.11.235:7400"
     */
    public String getRemoteConnectUrl(String serial) {
        try {
            JsonNode response = post(
                    baseUrl + "/api/v1/user/devices/" + serial + "/remoteConnect", "{}");

            boolean success = response.path("success").asBoolean(false);
            if (!success) {
                String desc = response.path("description").asText("unknown error");
                throw new RuntimeException("[STF] remoteConnect failed: " + desc);
            }

            String remoteConnectUrl = response.path("remoteConnectUrl").asText();
            System.out.println("[STF] Remote connect URL for " + serial + ": " + remoteConnectUrl);
            return remoteConnectUrl;

        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("[STF] Failed to get remote connect URL for: " + serial, e);
        }
    }

    /**
     * Releases the reserved device so other users can pick it up.
     */
    public void releaseDevice(String serial) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/user/devices/" + serial))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .DELETE()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("[STF] Released device: " + serial
                    + " (HTTP " + response.statusCode() + ")");

        } catch (Exception e) {
            // Non-fatal — log and continue so teardown always completes
            System.err.println("[STF] Warning: failed to release device " + serial + ": " + e.getMessage());
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private JsonNode get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString());

        assertOk(response, url);
        return mapper.readTree(response.body());
    }

    private JsonNode post(String url, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString());

        assertOk(response, url);
        return mapper.readTree(response.body());
    }

    private void assertOk(HttpResponse<String> response, String url) {
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new RuntimeException(
                    "[STF] HTTP " + code + " from " + url + " → " + response.body());
        }
    }
}
