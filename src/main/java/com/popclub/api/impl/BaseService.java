package com.popclub.api.impl;

import com.popclub.api.util.ConfigManager;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseService {

    private static final Logger log = LoggerFactory.getLogger(BaseService.class);

    private String token;

    protected RequestSpecification buildPublicSpec() {
        return RestAssured.given()
                .baseUri(ConfigManager.getBaseUrl())
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter());
    }

    protected RequestSpecification buildSpec() {
        RequestSpecification spec = RestAssured.given()
                .baseUri(ConfigManager.getBaseUrl())
                .header("X-Source-Api-Key", ConfigManager.getXSourceApiKey())
                .contentType(ContentType.JSON)
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter());
        if (token != null) {
            spec.header("X-Auth-Token", token);
        }
        return spec;
    }

    public void attachToken(String token) {
        this.token = token;
    }

    protected Response get(String path) {
        try {
            Response response = buildSpec().get(path);
            logIfError("GET", path, response);
            return response;
        } catch (Exception e) {
            log.error("[BaseService] GET {} failed: {}", path, e.getMessage());
            throw new ApiException("GET " + path + " failed", e);
        }
    }

    protected Response post(String path, Object body) {
        try {
            Response response = buildSpec().body(body).post(path);
            logIfError("POST", path, response);
            return response;
        } catch (Exception e) {
            log.error("[BaseService] POST {} failed: {}", path, e.getMessage());
            throw new ApiException("POST " + path + " failed", e);
        }
    }

    protected Response put(String path, Object body) {
        try {
            Response response = buildSpec().body(body).put(path);
            logIfError("PUT", path, response);
            return response;
        } catch (Exception e) {
            log.error("[BaseService] PUT {} failed: {}", path, e.getMessage());
            throw new ApiException("PUT " + path + " failed", e);
        }
    }

    protected Response patch(String path, Object body) {
        try {
            Response response = buildSpec().body(body).patch(path);
            logIfError("PATCH", path, response);
            return response;
        } catch (Exception e) {
            log.error("[BaseService] PATCH {} failed: {}", path, e.getMessage());
            throw new ApiException("PATCH " + path + " failed", e);
        }
    }

    private void logIfError(String method, String path, Response response) {
        int status = response.statusCode();
        if (status >= 400) {
            String body = response.body().asString();
            log.error("[BaseService] {} {} returned {} | body: {}", method, path, status, body);
        }
    }

    public void reset() {
        this.token = null;
    }
}
