package com.popclub.clp;

import com.popclub.api.enums.Authorization;
import com.popclub.api.enums.HttpMethod;
import com.popclub.api.impl.PopApiClient;
import com.popclub.api.impl.PopRequestSpecification;
import com.popclub.api.util.ApiConstants;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * AuthApiClient — obtains a user JWT via the v3/login API.
 *
 * Works entirely over REST — no device, no logcat, CI-safe.
 *
 * Flow:
 *   1. POST v3/login  { api_type: SEND_OTP,    mobile_number: +91... }
 *   2. POST v3/login  { api_type: VERIFY_OTP,  mobile_number: +91..., otp: ... }
 *      → response body contains access_token
 *
 * Test phone numbers (+911234560001–9999) accept fixed OTP "560102".
 *
 * Usage:
 *   String token = AuthApiClient.login("1234561122", "560102");
 *   // → "eyJhbGci..."
 */
public class AuthApiClient {

    private static final Logger log = LoggerFactory.getLogger(AuthApiClient.class);

    private static final String LOGIN_URL =
            ApiConstants.APP_BASE_URL + "/api/v3/login/";

    private static final String DEVICE_ID  = "b1bee75238a76999";
    private static final String PLATFORM   = "android";
    private static final String APP_VERSION = "89";

    /**
     * Full login: sends OTP then verifies it.
     *
     * @param phone       digits only, no country code e.g. "1234561122"
     * @param otp         OTP to verify e.g. "560102"
     * @return            access_token string, never null
     * @throws RuntimeException if either step fails or token is missing
     */
    public static String login(String phone, String otp) {
        String normalised = phone.startsWith("+91") ? phone : "+91" + phone;

        log.info("[AuthApiClient] SEND_OTP → {}", normalised);
        sendOtp(normalised);

        log.info("[AuthApiClient] VERIFY_OTP → {}", normalised);
        String token = verifyOtp(normalised, otp);

        log.info("[AuthApiClient] Login successful — token obtained");
        return token;
    }

    // ── Step 1: Send OTP ──────────────────────────────────────────────────────

    private static void sendOtp(String mobile) {
        Response r = post(Map.of(
                "api_type",      "SEND_OTP",
                "mobile_number", mobile
        ));

        if (r.statusCode() != 200) {
            throw new RuntimeException(
                    "SEND_OTP failed HTTP " + r.statusCode() + ": " + r.body().asString());
        }

        boolean ok = r.jsonPath().getBoolean("is_success");
        if (!ok) {
            throw new RuntimeException(
                    "SEND_OTP returned is_success=false: " + r.body().asString());
        }
    }

    // ── Step 2: Verify OTP → extract token ────────────────────────────────────

    private static String verifyOtp(String mobile, String otp) {
        Response r = post(Map.of(
                "api_type",      "VERIFY_OTP",
                "mobile_number", mobile,
                "otp",           otp,
                "permissions",   Map.of("whatsapp", false)
        ));

        if (r.statusCode() != 200) {
            throw new RuntimeException(
                    "VERIFY_OTP failed HTTP " + r.statusCode() + ": " + r.body().asString());
        }

        // access_token is at the root of the response
        String token = r.jsonPath().getString("access_token");
        if (token != null && !token.isBlank()) return token;

        // Fallback: data.jwt_access_token (new auth system)
        token = r.jsonPath().getString("data.jwt_access_token");
        if (token != null && !token.isBlank()) return token;

        // Fallback: data.token (legacy)
        token = r.jsonPath().getString("data.token");
        if (token != null && !token.isBlank()) return token;

        throw new RuntimeException(
                "VERIFY_OTP succeeded but no token found in response: "
                + r.body().asString().substring(0, Math.min(500, r.body().asString().length())));
    }

    // ── Shared request builder ────────────────────────────────────────────────

    private static Response post(Map<String, Object> body) {
        PopRequestSpecification spec = PopRequestSpecification.builder()
                .baseUrl(LOGIN_URL)
                .authorization(Authorization.APP_API)
                .headers(Map.of(
                        "Accept",      "*/*",
                        "device_id",   DEVICE_ID,
                        "platform",    PLATFORM,
                        "app_version", APP_VERSION
                ))
                .body(body)
                .build();

        return PopApiClient.executeRequest(HttpMethod.POST, spec);
    }
}
