package com.popclub.api.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popclub.api.dto.*;
import com.popclub.api.enums.Authorization;
import com.popclub.api.enums.HttpMethod;
import com.popclub.api.enums.Routes;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class WebSetupApiImpl extends PopApiClient {

    private static final Logger log    = LoggerFactory.getLogger(WebSetupApiImpl.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // ── API 1: check first_time_user ─────────────────────────────────────────

    public LoginResponseDto login(String phone) {
        log.info("[WebSetupImpl] login for {}", phone);
        try {
            PopRequestSpecification spec = PopRequestSpecification.builder()
                    .baseUrl(Routes.LOGIN.appUrl())
                    .authorization(Authorization.APP_API)
                    .headers(Map.of(
                            "Accept",      "*/*",
                            "device_id",   "b1bee75238a76999",
                            "platform",    "android",
                            "app_version", "89"))
                    .body(mapper.writeValueAsString(new LoginRequestDto(phone)))
                    .build();

            Response response = executeRequest(HttpMethod.POST, spec);
            int status = response.statusCode();
            log.debug("[WebSetupImpl] login [{}]: {}", status, response.body().asString());
            if (status == 401 || status == 403) {
                throw new AuthException("login returned HTTP " + status + " — APP_TOKEN may be expired");
            }
            return mapper.readValue(response.body().asString(), LoginResponseDto.class);
        } catch (Exception e) {
            throw new RuntimeException("login failed for " + phone, e);
        }
    }

    // ── API 2: signup — get user ID ──────────────────────────────────────────

    public SignupResponseDto signup(String phone) {
        log.info("[WebSetupImpl] signup for {}", phone);
        try {
            PopRequestSpecification spec = PopRequestSpecification.builder()
                    .baseUrl(Routes.SIGNUP.hashiraUrl())
                    .authorization(Authorization.NONE)
                    .body(mapper.writeValueAsString(new SignupRequestDto(phone)))
                    .build();

            Response response = executeRequest(HttpMethod.POST, spec);
            log.info("[WebSetupImpl] signup [{}]: {}", response.statusCode(), response.body().asString());
            return mapper.readValue(response.body().asString(), SignupResponseDto.class);
        } catch (Exception e) {
            throw new RuntimeException("signup failed for " + phone, e);
        }
    }

    // ── API 3: mark hybrid journey ───────────────────────────────────────────

    public void markHybridJourney(String userId) {
        log.info("[WebSetupImpl] markHybridJourney userId={}", userId);
        try {
            PopRequestSpecification spec = PopRequestSpecification.builder()
                    .baseUrl(Routes.USER_PROFILE.userProfileUrl(userId))
                    .authorization(Authorization.NONE)
                    .body(mapper.writeValueAsString(new HybridJourneyRequestDto()))
                    .build();

            Response response = executeRequest(HttpMethod.PUT, spec);
            log.info("[WebSetupImpl] markHybridJourney [{}] userId={}", response.statusCode(), userId);
        } catch (Exception e) {
            throw new RuntimeException("markHybridJourney failed for userId=" + userId, e);
        }
    }

    // ── API 4: set card attributes ───────────────────────────────────────────

    public void setCardAttributes(String phone) {
        log.info("[WebSetupImpl] setCardAttributes for {}", phone);
        try {
            PopRequestSpecification spec = PopRequestSpecification.builder()
                    .baseUrl(Routes.CUSTOM_ATTRIBUTES.prodUrl())
                    .authorization(Authorization.PROD_API)
                    .headers(Map.of("Accept-Encoding", "gzip,deflate,sdch"))
                    .body(mapper.writeValueAsString(new CardAttributesRequestDto(phone)))
                    .build();

            Response response = executeRequest(HttpMethod.POST, spec);
            log.info("[WebSetupImpl] setCardAttributes [{}] for {}", response.statusCode(), phone);
        } catch (Exception e) {
            throw new RuntimeException("setCardAttributes failed for " + phone, e);
        }
    }
}
