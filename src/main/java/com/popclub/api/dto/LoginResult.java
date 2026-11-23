package com.popclub.api.dto;

/**
 * Holds both tokens returned by the v3/login VERIFY_OTP response.
 *
 * legacyToken  — old DRF token (data.token / access_token).
 *                Sent as: Authorization: Token <legacyToken>
 *
 * jwtToken     — Hashira JWT (data.jwt_access_token).
 *                Sent as: X-Access-Token: Token <jwtToken>
 */
public class LoginResult {

    public final String legacyToken;
    public final String jwtToken;

    public LoginResult(String legacyToken, String jwtToken) {
        this.legacyToken = legacyToken;
        this.jwtToken    = jwtToken;
    }

    @Override
    public String toString() {
        String legacyPreview = preview(legacyToken);
        String jwtPreview    = preview(jwtToken);
        return "LoginResult{legacy=" + legacyPreview + ", jwt=" + jwtPreview + "}";
    }

    private static String preview(String t) {
        if (t == null || t.isBlank()) return "(none)";
        return t.length() > 12 ? t.substring(0, 8) + "…" + t.substring(t.length() - 4) : t;
    }
}
