package com.popclub.api.actions;

import com.popclub.api.dto.LoginResult;
import com.popclub.api.impl.PopService;
import com.popclub.api.impl.SearchService;
import com.popclub.api.auth.TokenExtractor;
import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.core.TestContext;
import io.restassured.response.Response;

import java.util.Map;

/**
 * Maps YAML service names → existing service implementations.
 *
 * To expose any existing service method to YAML:
 *   1. Add one case below that calls your method
 *   2. Done — no new class needed
 *
 * Auth:
 *   - Public endpoints (buildPublicSpec): instantiate service directly, no loginFull needed
 *   - Auth endpoints (buildSpec / AppService): call auth(params) first, then attachTokens / attachToken
 *
 * YAML:
 *   - action: callService
 *     service: pop.consents
 *     params:
 *       mobile_number: "1234561122"
 *     extract:
 *       consents_given: "data.consents_given"
 */
public class ServiceRegistry {

    public static Response call(String serviceName, Map<String, String> params) {
        return switch (serviceName) {

            // ── PopService — public (no auth) ─────────────────────────────────
            case "pop.consents" -> {
                PopService s = new PopService();
                yield s.getPopConsentsWithMobile(require(params, "mobile_number", serviceName));
            }

            // ── SearchService — needs app auth ────────────────────────────────
            case "search.plp" -> {
                SearchService s = new SearchService();
                s.attachTokens(appAuth(params));
                yield s.searchPlp(
                    require(params, "query", serviceName),
                    params.getOrDefault("pincode", ""),
                    parseInt(params.getOrDefault("page", "1"))
                );
            }

            // ── Add more cases here as you build them ──────────────────────────
            // Pattern for public endpoint (no auth):
            //   case "pop.verify_pincode" -> new PopService().verifyPincode(params.get("pincode"));
            //
            // Pattern for auth endpoint:
            //   case "pop.user_details" -> {
            //       PopService s = new PopService();
            //       s.attachToken(cardAuth(params));   // BaseService token
            //       yield s.getUserDetails();
            //   }

            default -> throw new RuntimeException(
                "callService: unknown service \"" + serviceName + "\".\n"
                + "Add a case for it in ServiceRegistry.java");
        };
    }

    // ── Auth helpers — call only when the service needs it ────────────────────

    /**
     * Auth for AppService-based services (SearchService etc.).
     *
     * Token resolution order:
     *   1. Already in TestContext (captured by loginIfNeeded right after login)
     *   2. Retry TokenExtractor from logcat now — works if other app requests have
     *      run since login and their X-Access-Token header appeared in logcat
     *
     * Throws only if both attempts fail.
     */
    private static LoginResult appAuth(Map<String, String> params) {
        String token = TestContext.getUserToken();

        if (token == null || token.isBlank()) {
            System.out.println("[ServiceRegistry] Token not in TestContext — retrying from logcat…");
            token = fetchTokenFromLogcat();
            if (token != null && !token.isBlank()) {
                TestContext.setUserToken(token);
                TestContext.setScalarData("auth_token", token);
                System.out.println("[ServiceRegistry] Token captured from logcat on retry ✓");
            }
        }

        if (token == null || token.isBlank())
            throw new RuntimeException(
                "callService: no auth token found.\n"
                + "Ensure 'action: loginIfNeeded' ran before this step.");

        return new LoginResult(null, token);   // JWT → X-Access-Token header
    }

    private static String fetchTokenFromLogcat() {
        try {
            String deviceSerial = "";
            try {
                var info = AppiumDriverManager.getDeviceInfo();
                if (info != null) deviceSerial = info.udid;
            } catch (Exception ignored) {}
            return TokenExtractor.get(deviceSerial);
        } catch (Exception e) {
            System.out.println("[ServiceRegistry] Logcat token retry failed: " + e.getMessage());
            return null;
        }
    }

    // ── Param helpers ─────────────────────────────────────────────────────────

    private static String require(Map<String, String> params, String key, String service) {
        String v = params.get(key);
        if (v == null || v.isBlank())
            throw new RuntimeException("callService[" + service + "]: param \"" + key + "\" is required");
        return v;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 1; }
    }
}
