package com.popclub.testsigma;

import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;

/**
 * Automates TestSigma's 3-hop SSO login (id.testsigma.com → callbacks/authorize →
 * test-management.testsigma.com/identity/authorize_callback) to obtain a fresh
 * X-TMS-SESSION-ID session cookie at the start of a run.
 *
 * This exists because attachment uploads (TestSigmaClient.uploadAttachment) can only
 * authenticate via this session cookie, not the Bearer API token, and the cookie
 * expires — previously requiring a manual DevTools copy-paste before every run.
 *
 * Ported from the manually-verified scripts/testsigma_login.sh shell script.
 */
public class TestSigmaSessionManager {

    private static final String LOGIN_HOST = "https://id.testsigma.com";
    private static final String APP_HOST = "https://test-management.testsigma.com";
    private static final String CLIENT_PATH = "72987";
    private static final String REDIRECT_TO = APP_HOST + "/ui/test_cases?generateTestCases=false";
    private static final String LOCAL_PROPS_PATH = "src/test/resources/config/testsigma-local.properties";

    // Corporate networks (e.g. Zscaler) intercept HTTPS with a proxy cert the JDK
    // doesn't trust by default, causing PKIX path errors. TestSigmaClient already
    // relaxes this for its own calls, but only once that class is loaded — which
    // happens AFTER this login flow runs in TestListener.onStart(). Apply the same
    // relaxed config here directly so it doesn't depend on class-load order.
    private static final RestAssuredConfig RELAXED_SSL_CONFIG =
            RestAssuredConfig.config().sslConfig(SSLConfig.sslConfig().relaxedHTTPSValidation());

    /**
     * Attempts to log in and refresh the session cookie. On success, the fresh cookie
     * is both applied to this JVM run (via a system property, so it takes effect
     * immediately for the current test run) and persisted to the local properties
     * file for future runs. Never throws — any failure (missing credentials, captcha,
     * network error, unexpected page structure) is logged and returns false, leaving
     * whatever session cookie was already configured in place.
     */
    public static boolean refreshSessionCookie() {
        String email = TestSigmaConfig.loginEmail();
        String password = TestSigmaConfig.loginPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            System.out.println("[TestSigma] testsigma.login.email/password not configured — "
                    + "skipping automatic session login (existing session cookie, if any, will be used).");
            return false;
        }

        try {
            Map<String, String> jar = new LinkedHashMap<>();

            // Step 1: POST /login — capture SESSION cookie
            Map<String, String> loginBody = new LinkedHashMap<>();
            loginBody.put("username", email);
            loginBody.put("password", password);

            Response loginResp = given()
                    .config(RELAXED_SSL_CONFIG)
                    .baseUri(LOGIN_HOST)
                    .header("accept", "application/json, text/plain, */*")
                    .contentType("application/json")
                    .body(loginBody)
                    .redirects().follow(false)
                    .post("/login");

            if (loginResp.getStatusCode() != 200) {
                System.out.println("[TestSigma] ⚠️  Auto-login failed (status " + loginResp.getStatusCode()
                        + ") — a captcha may be required, which this cannot solve. "
                        + "Falling back to existing session cookie.");
                return false;
            }
            mergeCookies(jar, loginResp);

            // Step 2: GET the authorize page — extract the "token" form field
            Response authResp = given()
                    .config(RELAXED_SSL_CONFIG)
                    .baseUri(LOGIN_HOST)
                    .cookies(jar)
                    .redirects().follow(false)
                    .queryParam("redirectTo", REDIRECT_TO)
                    .get("/callbacks/authorize/" + CLIENT_PATH);

            mergeCookies(jar, authResp);
            String html = authResp.getBody().asString();
            Matcher tokenMatcher = Pattern.compile("name=\"token\"[^>]*value=\"([^\"]*)\"").matcher(html);
            if (!tokenMatcher.find()) {
                System.out.println("[TestSigma] ⚠️  Auto-login: could not find the token field on the "
                        + "authorize page — TestSigma's login page may have changed.");
                return false;
            }
            String token = tokenMatcher.group(1);

            // Step 3: exchange the token for the X-TMS-SESSION-ID cookie
            Response exchangeResp = given()
                    .config(RELAXED_SSL_CONFIG)
                    .baseUri(APP_HOST)
                    .cookies(jar)
                    .redirects().follow(false)
                    .formParam("token", token)
                    .formParam("redirectTo", REDIRECT_TO)
                    .post("/identity/authorize_callback");

            String sessionCookie = extractCookieValue(exchangeResp, "X-TMS-SESSION-ID");
            if (sessionCookie == null) {
                System.out.println("[TestSigma] ⚠️  Auto-login: X-TMS-SESSION-ID cookie not found in the "
                        + "final response — login may have failed silently.");
                return false;
            }

            System.setProperty("testsigma.session.cookie", sessionCookie);
            persistSessionCookie(sessionCookie);
            System.out.println("[TestSigma] ✅ Fresh session cookie obtained automatically and saved.");
            return true;

        } catch (Exception e) {
            System.out.println("[TestSigma] ⚠️  Auto-login failed: " + e.getMessage());
            return false;
        }
    }

    private static void mergeCookies(Map<String, String> jar, Response response) {
        List<String> setCookies = response.getHeaders().getValues("Set-Cookie");
        for (String setCookie : setCookies) {
            String pair = setCookie.split(";", 2)[0];
            int idx = pair.indexOf('=');
            if (idx > 0) {
                jar.put(pair.substring(0, idx).trim(), pair.substring(idx + 1).trim());
            }
        }
    }

    private static String extractCookieValue(Response response, String cookieName) {
        List<String> setCookies = response.getHeaders().getValues("Set-Cookie");
        for (String setCookie : setCookies) {
            String pair = setCookie.split(";", 2)[0];
            if (pair.startsWith(cookieName + "=")) {
                return pair.substring((cookieName + "=").length());
            }
        }
        return null;
    }

    /** Updates testsigma.session.cookie in the local properties file, preserving everything else. */
    private static void persistSessionCookie(String cookieValue) {
        try {
            File file = new File(LOCAL_PROPS_PATH);
            List<String> lines = file.exists()
                    ? Files.readAllLines(file.toPath())
                    : new ArrayList<>();

            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("testsigma.session.cookie=")) {
                    lines.set(i, "testsigma.session.cookie=" + cookieValue);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) lines.add("testsigma.session.cookie=" + cookieValue);

            Files.write(file.toPath(), lines);
        } catch (Exception e) {
            System.out.println("[TestSigma] ⚠️  Could not persist session cookie to "
                    + LOCAL_PROPS_PATH + ": " + e.getMessage());
        }
    }
}
