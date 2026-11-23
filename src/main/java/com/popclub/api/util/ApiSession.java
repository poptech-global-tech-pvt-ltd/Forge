package com.popclub.api.util;

import io.restassured.response.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-thread session state for YAML-driven API steps.
 * Stores base URL, headers, and the last response so steps can chain requests.
 */
public class ApiSession {

    private static final ThreadLocal<String>              baseUrl      = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, String>> headers      = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Response>            lastResponse = new ThreadLocal<>();

    public static void setBaseUrl(String url)         { baseUrl.set(url); }
    public static String getBaseUrl()                 { return baseUrl.get() != null ? baseUrl.get() : ""; }

    public static void setHeader(String key, String value) { headers.get().put(key, value); }
    public static Map<String, String> getHeaders()         { return new HashMap<>(headers.get()); }

    public static void setLastResponse(Response r)    { lastResponse.set(r); }
    public static Response getLastResponse()          { return lastResponse.get(); }

    public static void clear() {
        baseUrl.remove();
        headers.remove();
        lastResponse.remove();
    }
}
