package com.popclub.api.actions;

import com.popclub.android.actions.Action;
import com.popclub.api.auth.TokenExtractor;
import com.popclub.core.TestContext;
import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.model.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CaptureTokenAction — reads the JWT the app already holds after login
 * and stores it in TestContext for the rest of the run.
 *
 * Priority:
 *   1. SharedPreferences on device — the token written by the app after login
 *   2. USER_TOKEN from local.properties (CI fallback)
 *
 * Always reads what the app stored — never triggers a fresh login.
 * Place this step after loginIfNeeded so the token is already present.
 *
 * YAML usage:
 *
 *   - action: loginIfNeeded
 *     value: "1234561122"
 *     text:  "560102"
 *
 *   - action: captureToken
 */
public class CaptureTokenAction implements Action {

    private static final Logger log = LoggerFactory.getLogger(CaptureTokenAction.class);

    @Override
    public void perform(Step step) {

        // Read token from device SharedPreferences (written by the app after login)
        // Always overwrites — if login happened again the new token replaces the old one
        String deviceSerial = "";
        try {
            var info = AppiumDriverManager.getDeviceInfo();
            if (info != null) deviceSerial = info.udid;
        } catch (Exception ignored) {}

        String token = TokenExtractor.get(deviceSerial);

        // ── Store or warn ─────────────────────────────────────────────────────
        if (token != null && !token.isBlank()) {
            TestContext.setUserToken(token);
            // Also expose as ${auth_token} for ${} interpolation in fetchApi steps
            TestContext.setScalarData("auth_token", token);
            String preview = token.length() > 20
                    ? token.substring(0, 10) + "…" + token.substring(token.length() - 6)
                    : token;
            System.out.println("  🔑 Token captured: " + preview);
        } else {
            System.out.println("  ⚠️  captureToken: no token found on device — run loginIfNeeded first");
        }
    }
}
