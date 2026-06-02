package com.popclub.mobile.actions;

import com.popclub.clp.AuthApiClient;
import com.popclub.clp.TokenExtractor;
import com.popclub.core.TestContext;
import com.popclub.mobile.driver.AppiumDriverManager;
import com.popclub.model.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CaptureTokenAction — obtains the user JWT and stores it in TestContext
 * so verifyCLP and other API-driven actions can use it for the rest of the run.
 *
 * Priority (CI-safe):
 *   1. API login (AuthApiClient) — works in CI, no device needed
 *      Requires `phone` and `otp` on the step (or falls back to defaults)
 *   2. SharedPreferences on device (app already logged in)
 *   3. OkHttp logcat (only works locally, not in CI)
 *   4. USER_TOKEN from local.properties
 *
 * YAML usage — place after the OTP enterText step:
 *
 *   - action: captureToken
 *     value: "1234561122"     ← phone (digits only, no +91)
 *     text:  "560102"         ← OTP
 *
 * Or with defaults (uses PHONE_BASE from local.properties + OTP "560102"):
 *
 *   - action: captureToken
 */
public class CaptureTokenAction implements Action {

    private static final Logger log = LoggerFactory.getLogger(CaptureTokenAction.class);

    private static final String DEFAULT_OTP = "560102";

    @Override
    public void perform(Step step) {

        // Already captured in this run — skip
        if (TestContext.getUserToken() != null && !TestContext.getUserToken().isBlank()) {
            log.info("[captureToken] Token already in TestContext — skipping");
            return;
        }

        String token = null;

        // ── 1. API login (CI-safe) ────────────────────────────────────────────
        String phone = step.value;
        String otp   = step.text != null ? step.text : DEFAULT_OTP;

        if (phone != null && !phone.isBlank()) {
            try {
                log.info("[captureToken] Logging in via API: phone={}", phone);
                token = AuthApiClient.login(phone, otp);
                log.info("[captureToken] API login succeeded");
            } catch (Exception e) {
                log.warn("[captureToken] API login failed: {} — trying device fallback", e.getMessage());
            }
        }

        // ── 2 & 3. Device fallback (SharedPrefs / logcat) ─────────────────────
        if (token == null || token.isBlank()) {
            String deviceSerial = "";
            try {
                var info = AppiumDriverManager.getDeviceInfo();
                if (info != null) deviceSerial = info.udid;
            } catch (Exception ignored) {}

            token = TokenExtractor.get(deviceSerial);
        }

        // ── Store or warn ─────────────────────────────────────────────────────
        if (token != null && !token.isBlank()) {
            TestContext.setUserToken(token);
            String preview = token.length() > 20
                    ? token.substring(0, 10) + "…" + token.substring(token.length() - 6)
                    : token;
            System.out.println("  🔑 Token captured: " + preview);
        } else {
            System.out.println("  ⚠️  captureToken: no token found — verifyCLP will run in anonymous mode");
        }
    }
}
