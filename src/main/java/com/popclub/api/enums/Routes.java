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
    YBL_PROFESSIONAL_DETAILS("/ybl/professional-details"),

    TESTSIGMA_LOGIN         ("/login"),
    TESTSIGMA_AUTHORIZE     ("/callbacks/authorize/72987"),
    TESTSIGMA_TOKEN_EXCHANGE("/identity/authorize_callback"),

    // ── C2C-Svc routes (via Kong) ─────────────────────────────────────────────
    CSVC_PAYEE_LIST           ("/c2c/api/v1/payee-list"),
    CSVC_PAYEE_BASIC_INFO     ("/c2c/api/v1/payee/basic-info"),
    CSVC_PAYEE_VERIFICATION   ("/c2c/api/v1/payee/verification"),
    CSVC_PAYMENT_SUMMARY      ("/c2c/api/v1/payment/summary"),
    CSVC_PAYMENT_INTENT       ("/c2c/api/v1/payee/payment-intent"),
    CSVC_PAYMENT_INTENT_VERIFY("/c2c/api/v1/payment-intent/%s/verify"),
    CSVC_PAYMENT_INTENT_STATUS("/c2c/api/v1/payment-intent/%s/status"),
    CSVC_LIMIT_RESERVE        ("/c2c/api/v1/limit/reserve"),
    CSVC_LIMITS_RELEASE       ("/c2c/api/v1/limits/release"),
    CSVC_LIMITS_CONSUME       ("/c2c/api/v1/limits/consume"),

    // ── OMS routes (via Kong) ─────────────────────────────────────────────────
    OMS_ORDERS                ("/order-management/api/v1/marketplace/orders"),
    OMS_GET_ORDER             ("/order-management/api/v1/marketplace/order/%s"),
    OMS_ORDER_STATUS          ("/order-management/api/v1/marketplace/order/%s/status"),
    OMS_ACCEPTANCE_RESPONSE   ("/order-management/api/v1/internal/marketplace/orders/%s/acceptance-response"),
    OMS_STREAM                ("/order-management/api/v1/marketplace/orders/%s/stream"),

    // ── Onboarding routes (via Kong) ─────────────────────────────────────────
    ONBOARDING_SEND_OTP       ("/c2c-onboarding/auth/api/v1/send-otp"),
    ONBOARDING_VERIFY_OTP     ("/c2c-onboarding/auth/api/v1/verify-otp"),
    ONBOARDING_PAYEE_DETAILS  ("/c2c-onboarding/api/v1/payee"),
    ONBOARDING_PAYEE_CONSENT  ("/c2c-onboarding/api/v1/payee/consent");

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

    public String testSigmaLoginUrl() {
        return ApiConstants.TESTSIGMA_LOGIN_BASE_URL + path;
    }

    public String testSigmaAppUrl() {
        return ApiConstants.TESTSIGMA_APP_BASE_URL + path;
    }

    public String kongUrl(String... args) {
        return ApiConstants.KONG_BASE_URL + String.format(path, (Object[]) args);
    }
}
