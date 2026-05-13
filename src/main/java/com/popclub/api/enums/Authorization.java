package com.popclub.api.enums;

import com.popclub.api.util.ApiConstants;

public enum Authorization {

    APP_API (ApiConstants.APP_TOKEN),
    PROD_API(ApiConstants.PROD_TOKEN),
    NONE    (null);

    private final String token;

    Authorization(String token) { this.token = token; }

    public String token()  { return token; }
    public String header() { return token != null ? "Token " + token : null; }
}
