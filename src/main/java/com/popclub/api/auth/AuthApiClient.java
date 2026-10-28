package com.popclub.api.auth;

import com.popclub.api.dto.LoginResult;
import com.popclub.api.enums.Authorization;
import com.popclub.api.enums.HttpMethod;
import com.popclub.api.impl.PopApiClient;
import com.popclub.api.impl.PopRequestSpecification;
import com.popclub.api.util.ApiConstants;
import com.popclub.api.util.ConfigManager;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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

    private static final String DEVICE_ID  = "4dad8298141de331";
    private static final String PLATFORM   = "android";
    private static final String APP_VERSION = "237";

    // Device integrity token — all-zero flags = clean device (full flag set from real device)
    private static final String X_TSIG_TOKEN =
            "djE6dmRkOjAscnRkOjAsYmJiOjAsc3ViOjAsZnJkOjAsaG9rOjAscHh5OjAsc3NwOjAsc3B2OjAsZGJnOjAscmV2OjAsaXN2OjAsc3RkOjAsdGpwOjAsdnBuOjAsY29iOjAsbmRrOjAsbWNpOjAscHNtOjAsc292OjAsdWRlOjAsZG1lOjAsZHNsOjAsbWxjOjAsZGFwOjAsdGltOjAsdXdkOjAsYWNwOjAsYXVjOjAsYXdkOjAsaWNsOjAsa2xnOjAsaGFwOjAsYXZjOjAscmFwOjAsc2J4OjAsc3BmOjAsZGJsOjAsb2ZhOjAsdHBrOjAscnYzOjAsZGljOjAsYWFkOjAsaHZsOjAsaGsyOjAsaGszOjA=";

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

    /**
     * Full login returning both legacy token and JWT.
     * Use this for app API tests (search, shop, home) that need both headers.
     *
     * @param phone  digits only e.g. "1234561122"
     * @param otp    e.g. "560102"
     * @return LoginResult with legacyToken + jwtToken
     */
    public static LoginResult loginFull(String phone, String otp) {
        String normalised = phone.startsWith("+91") ? phone : "+91" + phone;
        log.info("[AuthApiClient] SEND_OTP → {}", normalised);
        sendOtp(normalised);
        log.info("[AuthApiClient] VERIFY_OTP → {}", normalised);
        return verifyOtpFull(normalised, otp);
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

    // ── Step 2b: Verify OTP → return both tokens ─────────────────────────────

    private static LoginResult verifyOtpFull(String mobile, String otp) {
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

        // Legacy token — DRF token used in Authorization: Token <legacy>
        String legacy = r.jsonPath().getString("data.token");
        if (legacy == null || legacy.isBlank())
            legacy = r.jsonPath().getString("access_token");

        // JWT — used in X-Access-Token: Token <jwt>
        String jwt = r.jsonPath().getString("data.jwt_access_token");
        if (jwt == null || jwt.isBlank())
            jwt = r.jsonPath().getString("access_token");

        if ((legacy == null || legacy.isBlank()) && (jwt == null || jwt.isBlank())) {
            throw new RuntimeException(
                    "VERIFY_OTP succeeded but no token found: "
                    + r.body().asString().substring(0, Math.min(500, r.body().asString().length())));
        }

        log.info("[AuthApiClient] loginFull — legacy={}, jwt={}",
                preview(legacy), preview(jwt));
        return new LoginResult(legacy, jwt);
    }

    // ── SSO-based login (no x-tsig-token needed) ──────────────────────────────

    /**
     * Login via SSO verify — works without device integrity token.
     * Returns a LoginResult where the single SSO token is used as both legacy + JWT.
     *
     * Use this when v3/login is blocked by x-tsig-token requirement.
     *
     * @param phone digits only e.g. "1234561122"
     * @return LoginResult with same token for both legacy and JWT
     */
    public static LoginResult loginViaSSO(String phone) {
        // SSO verify expects bare number without +91 prefix
        String normalised = phone.startsWith("+91") ? phone.substring(3) : phone;

        log.info("[AuthApiClient] SSO verify → {}", normalised);

        // Use ConfigManager base URL (cardstack-sit.popclub.co.in from sit.properties)
        String ssoUrl = ConfigManager.getBaseUrl() + "/sso/verify";
        log.info("[AuthApiClient] SSO URL: {}", ssoUrl);

        PopRequestSpecification spec = PopRequestSpecification.builder()
                .baseUrl(ssoUrl)
                .authorization(Authorization.NONE)
                .headers(Map.of(
                        "Content-Type", "application/json",
                        "X-Source-Api-Key", ConfigManager.getXSourceApiKey()
                ))
                .body(Map.of("mobile_number", normalised))
                .build();

        Response r = PopApiClient.executeRequest(HttpMethod.POST, spec);

        if (r.statusCode() != 200) {
            throw new RuntimeException(
                    "SSO verify failed HTTP " + r.statusCode() + ": " + r.body().asString());
        }

        boolean ok = r.jsonPath().getBoolean("is_success");
        if (!ok) {
            throw new RuntimeException(
                    "SSO verify returned is_success=false: " + r.body().asString());
        }

        String token = r.jsonPath().getString("data.token");
        if (token == null || token.isBlank()) {
            throw new RuntimeException(
                    "SSO verify succeeded but no token found: " + r.body().asString());
        }

        String userId = r.jsonPath().getString("data.user_id");
        log.info("[AuthApiClient] SSO login successful — token={}, userId={}", preview(token), userId);

        // SSO returns JWT. For app APIs that need legacy DRF token, use APP_TOKEN from config.
        String legacyToken = ApiConstants.APP_TOKEN;
        if (legacyToken == null || legacyToken.isBlank()) {
            log.warn("[AuthApiClient] APP_TOKEN not set — using SSO JWT as legacy token (may fail for some APIs)");
            legacyToken = token;
        }

        return new LoginResult(legacyToken, token, userId);
    }

    private static String preview(String t) {
        if (t == null || t.isBlank()) return "(none)";
        return t.length() > 12 ? t.substring(0, 8) + "…" + t.substring(t.length() - 4) : t;
    }

    // ── Shared request builder ────────────────────────────────────────────────

    private static Response post(Map<String, Object> body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept",          "*/*");
        headers.put("device_id",       DEVICE_ID);
        headers.put("x-device-id",     DEVICE_ID);
        headers.put("platform",        PLATFORM);
        headers.put("app_version",     APP_VERSION);
        headers.put("x-app-version",   APP_VERSION);
        headers.put("x-tsig-token",    X_TSIG_TOKEN);
        headers.put("x-device-meta",   "c2Ftc3VuZzpTTS1TOTExQjoxNjphbmRyb2lk"); // samsung:SM-S911B:16:android
        headers.put("x-transport-type", "wifi");
        headers.put("user-agent",      "okhttp/5.1.0");

        PopRequestSpecification spec = PopRequestSpecification.builder()
                .baseUrl(LOGIN_URL)
                .authorization(Authorization.NONE)
                .headers(headers)
                .body(body)
                .build();

        return PopApiClient.executeRequest(HttpMethod.POST, spec);
    }
}
