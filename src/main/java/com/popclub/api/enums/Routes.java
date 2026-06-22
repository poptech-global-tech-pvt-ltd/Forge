package com.popclub.api.enums;

import com.popclub.api.util.ApiConstants;

public enum Routes {

    // ── app.popclub.co.in — auth ──────────────────────────────────────────────
    LOGIN             ("/api/v2/login/"),

    // ── app.popclub.co.in — search ───────────────────────────────────────────
    SEARCH_PLP_V2     ("search/v2/plp/"),

    // hashira.popclub.co.in
    SIGNUP            ("/api/v1/signup"),

    // userprofile.popclub.co.in
    USER_PROFILE      ("/api/v1/users/%s/"),

    // prod.popclub.co.in
    CUSTOM_ATTRIBUTES ("/api/v1/user/custom-attributes/"),

    // cardstack — POP endpoints
    SSO_VERIFY              ("/sso/verify"),
    POP_CONSENTS            ("/pop/consents"),
    POP_USER_DETAILS        ("/pop/user-details"),
    POP_VERIFY_PINCODE      ("/pop/verify_pincode"),
    POP_USER_JOURNEY        ("/pop/user-journey-detail"),

    // cardstack — YBL endpoints
    YBL_CONSENTS            ("/ybl/consents"),
    YBL_ADDRESS             ("/ybl/address"),
    YBL_ADDRESSES           ("/ybl/addresses"),
    YBL_PERSONAL_DETAILS    ("/ybl/personal-details"),
    YBL_MASTER_LISTS        ("/ybl/master-lists?filter=company&filter=industry&filter=profession&filter=business&filter=designation&filter=companytype"),
    YBL_PROFESSIONAL_DETAILS("/ybl/professional-details");

    private final String path;

    Routes(String path) { this.path = path; }

    public String getPath() { return path; }

    public String appUrl(String... args) {
        return ApiConstants.APP_BASE_URL + String.format(path, (Object[]) args);
    }

    public String hashiraUrl(String... args) {
        return ApiConstants.HASHIRA_BASE_URL + String.format(path, (Object[]) args);
    }

    public String userProfileUrl(String... args) {
        return ApiConstants.USER_PROFILE_BASE_URL + String.format(path, (Object[]) args);
    }

    public String prodUrl(String... args) {
        return ApiConstants.PROD_BASE_URL + String.format(path, (Object[]) args);
    }

    public String cardstackUrl(String... args) {
        return ApiConstants.CARDSTACK_BASE_URL + String.format(path, (Object[]) args);
    }
}
