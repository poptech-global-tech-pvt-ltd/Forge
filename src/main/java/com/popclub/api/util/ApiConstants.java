package com.popclub.api.util;

public class ApiConstants {

    public static final String APP_TOKEN  = ApiConfig.get("APP_TOKEN",  "");
    public static final String PROD_TOKEN = ApiConfig.get("PROD_TOKEN", "");
    // Optional user JWT — set USER_TOKEN in local.properties (grab from OkHttp logcat X-Access-Token)
    public static final String USER_TOKEN = ApiConfig.get("USER_TOKEN", "");

    public static final String APP_BASE_URL          = ApiConfig.get("APP_BASE_URL",          "https://app.popclub.co.in");
    public static final String HASHIRA_BASE_URL       = ApiConfig.get("HASHIRA_BASE_URL",       "https://hashira.popclub.co.in");
    public static final String USER_PROFILE_BASE_URL  = ApiConfig.get("USER_PROFILE_BASE_URL",  "https://userprofile.popclub.co.in");
    public static final String PROD_BASE_URL          = ApiConfig.get("PROD_BASE_URL",          "https://prod.popclub.co.in");
    public static final String PRESENTATION_BASE_URL  = ApiConfig.get("PRESENTATION_BASE_URL",  "https://presentation.popclub.co.in/api/");

    public static final String DEVICE_SERIAL = ApiConfig.get("DEVICE_SERIAL", "");

    public static final String PHONE_PREFIX = ApiConfig.get("PHONE_PREFIX", "+91");
    public static final long   PHONE_BASE   = Long.parseLong(ApiConfig.get("PHONE_BASE", "1234560001"));
    public static final long   PHONE_MAX    = Long.parseLong(ApiConfig.get("PHONE_MAX",  "1234569999"));
}
