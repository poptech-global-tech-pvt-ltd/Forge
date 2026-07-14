package com.popclub.testsigma;

import com.popclub.api.dto.TestSigmaLoginRequestDto;
import com.popclub.api.enums.HttpMethod;
import com.popclub.api.impl.PopApiClient;
import com.popclub.api.impl.PopRequestSpecification;
import io.restassured.response.Response;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestSigmaSessionManager {

    private static final String LOGIN_HOST = "https://id.testsigma.com";
    private static final String APP_HOST = "https://test-management.testsigma.com";
    private static final String CLIENT_PATH = "72987";
    private static final String REDIRECT_TO = APP_HOST + "/ui/test_cases?generateTestCases=false";
    private static final String LOCAL_PROPS_PATH = "src/test/resources/config/testsigma-local.properties";

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

            PopRequestSpecification loginSpec = PopRequestSpecification.builder()
                    .baseUrl(LOGIN_HOST + "/login")
                    .headers(Map.of("accept", "application/json, text/plain, */*"))
                    .body(new TestSigmaLoginRequestDto(email, password))
                    .followRedirects(false)
                    .build();
            Response loginResp = PopApiClient.executeRequest(HttpMethod.POST, loginSpec);

            if (loginResp.getStatusCode() != 200) {
                System.out.println("[TestSigma] ⚠️  Auto-login failed (status " + loginResp.getStatusCode()
                        + ") — a captcha may be required, which this cannot solve. "
                        + "Falling back to existing session cookie.");
                return false;
            }
            mergeCookies(jar, loginResp);

            PopRequestSpecification authSpec = PopRequestSpecification.builder()
                    .baseUrl(LOGIN_HOST + "/callbacks/authorize/" + CLIENT_PATH
                            + "?redirectTo=" + URLEncoder.encode(REDIRECT_TO, StandardCharsets.UTF_8))
                    .headers(Map.of("Cookie", toCookieHeader(jar)))
                    .followRedirects(false)
                    .build();
            Response authResp = PopApiClient.executeRequest(HttpMethod.GET, authSpec);

            mergeCookies(jar, authResp);
            String html = authResp.getBody().asString();
            Matcher tokenMatcher = Pattern.compile("name=\"token\"[^>]*value=\"([^\"]*)\"").matcher(html);
            if (!tokenMatcher.find()) {
                System.out.println("[TestSigma] ⚠️  Auto-login: could not find the token field on the "
                        + "authorize page — TestSigma's login page may have changed.");
                return false;
            }
            String token = tokenMatcher.group(1);

            String formBody = "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                    + "&redirectTo=" + URLEncoder.encode(REDIRECT_TO, StandardCharsets.UTF_8);
            PopRequestSpecification exchangeSpec = PopRequestSpecification.builder()
                    .baseUrl(APP_HOST + "/identity/authorize_callback")
                    .contentType("application/x-www-form-urlencoded")
                    .body(formBody)
                    .headers(Map.of("Cookie", toCookieHeader(jar)))
                    .followRedirects(false)
                    .build();
            Response exchangeResp = PopApiClient.executeRequest(HttpMethod.POST, exchangeSpec);

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

    private static String toCookieHeader(Map<String, String> jar) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : jar.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
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
