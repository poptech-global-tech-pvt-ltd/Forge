package com.popclub.mobile.cloud;

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

/**
 * Minimal REST client for OpenSTF / DeviceFarmer.
 *
 * API reference: https://github.com/DeviceFarmer/stf/blob/master/doc/API.md
 *
 * Endpoints used:
 *   GET  /api/v1/devices                              – list all devices
 *   POST /api/v1/user/devices                         – reserve a device
 *   POST /api/v1/user/devices/{serial}/remoteConnect  – get ADB-over-TCP URL
 *   DELETE /api/v1/user/devices/{serial}              – release a device
 */
public class STFClient {

    private final String baseUrl;
    private final String token;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public STFClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.replaceAll("/$", ""); // strip trailing slash
        this.token   = token;
        this.http    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .sslContext(trustAllSslContext())   // accept self-signed certs on internal farms
                .build();
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

        } catch (Exception e) {
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
