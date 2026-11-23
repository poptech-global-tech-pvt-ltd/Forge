package com.popclub.api.util;

public class ApiConstants {

    public static final String APP_TOKEN  = ApiConfig.get("APP_TOKEN",  "");
    public static final String PROD_TOKEN = ApiConfig.get("PROD_TOKEN", "");
    // Optional user JWT — set USER_TOKEN in local.properties (grab from OkHttp logcat X-Access-Token)
    public static final String USER_TOKEN = ApiConfig.get("USER_TOKEN", "");

    // ── API base URLs (use in fetchApi steps as ${APP_API_URL} etc.) ─────────
    // Override in local.properties e.g: APP_API_URL=https://uat.popclub.co.in/api/
    /** debug / release — main app API */
    public static final String APP_API_URL     = ApiConfig.get("APP_API_URL",     "https://app.popclub.co.in/api/");
    /** UAT environment */
    public static final String UAT_API_URL     = ApiConfig.get("UAT_API_URL",     "https://uat.popclub.co.in/api/");
    /** Backend direct */
    public static final String BACKEND_API_URL = ApiConfig.get("BACKEND_API_URL", "https://api.popclub.co.in/");

    // ── Service base URLs ─────────────────────────────────────────────────────
    public static final String APP_BASE_URL          = ApiConfig.get("APP_BASE_URL",          "https://app.popclub.co.in");
    public static final String HASHIRA_BASE_URL       = ApiConfig.get("HASHIRA_BASE_URL",       "https://hashira.popclub.co.in");
    public static final String USER_PROFILE_BASE_URL  = ApiConfig.get("USER_PROFILE_BASE_URL",  "https://userprofile.popclub.co.in");
    public static final String PROD_BASE_URL          = ApiConfig.get("PROD_BASE_URL",          "https://prod.popclub.co.in");
    public static final String PRESENTATION_BASE_URL  = ApiConfig.get("PRESENTATION_BASE_URL",  "https://presentation.popclub.co.in/api/");

    public static final String CARDSTACK_BASE_URL = ApiConfig.get("CARDSTACK_BASE_URL", "https://cardstack.getpopcard.co/api/v1");

    public static final String TESTSIGMA_LOGIN_BASE_URL = ApiConfig.get("TESTSIGMA_LOGIN_BASE_URL", "https://id.testsigma.com");
    public static final String TESTSIGMA_APP_BASE_URL   = ApiConfig.get("TESTSIGMA_APP_BASE_URL",   "https://arcus.testsigma.com");

    public static final String DEVICE_SERIAL = ApiConfig.get("DEVICE_SERIAL", "");

    public static final String PHONE_PREFIX = ApiConfig.get("PHONE_PREFIX", "+91");
    public static final long   PHONE_BASE   = Long.parseLong(ApiConfig.get("PHONE_BASE", "1234560001"));
    public static final long   PHONE_MAX    = Long.parseLong(ApiConfig.get("PHONE_MAX",  "1234569999"));
}
