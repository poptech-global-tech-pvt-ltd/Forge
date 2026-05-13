package com.popclub.api.enums;

import com.popclub.api.util.ApiConstants;

public enum Routes {

    // app.popclub.co.in
    LOGIN             ("/api/v2/login/"),

    // hashira.popclub.co.in
    SIGNUP            ("/api/v1/signup"),

    // userprofile.popclub.co.in
    USER_PROFILE      ("/api/v1/users/%s/"),

    // prod.popclub.co.in
    CUSTOM_ATTRIBUTES ("/api/v1/user/custom-attributes/");

    private final String path;

    Routes(String path) { this.path = path; }

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
}
