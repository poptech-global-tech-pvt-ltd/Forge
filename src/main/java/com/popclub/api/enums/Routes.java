package com.popclub.api.enums;

import com.popclub.api.util.ApiConstants;

public enum Routes {

    // ── app.popclub.co.in — auth ──────────────────────────────────────────────
    LOGIN             ("/api/v2/login/"),

    // ── app.popclub.co.in — search ───────────────────────────────────────────
    SEARCH_PLP_V2     ("search/v2/plp/"),

    // ── app.popclub.co.in — PDP ───────────────────────────────────────────────
    PRODUCT_DETAIL_V3 ("v3/products/%s/"),

    // ── sherlock.popclub.co.in — suggestion & filters ────────────────────────
    SUGGESTION        ("/api/v4/suggestion"),
    SUGGESTION_PLP    ("/api/v4/suggestion/plp"),
    FILTERS_LIST      ("/api/v4/filters-list"),
    FILTERS_MENU_LIST ("/api/v4/filters-menu-list"),

    // ── api.popclub.co.in — fulfillment (cart) ──────────────────────────────
    ADD_TO_CART       ("/fulfillment/api/v3/add-to-cart/"),
    BASKET_COUNT      ("/fulfillment/api/v3/basket/count/"),
    GET_CART          ("/fulfillment/api/v3/get-cart/serviceability/"),

    // ── api.popclub.co.in — order management ────────────────────────────────
    CREATE_ORDER      ("/order-management/api/v1/orders"),

    // hashira.popclub.co.in
    TOKEN_REFRESH     ("/token/refresh"),
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
    TESTSIGMA_TOKEN_EXCHANGE("/identity/authorize_callback");

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
}
