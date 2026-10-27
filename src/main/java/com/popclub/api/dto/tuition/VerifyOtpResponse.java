package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class VerifyOtpResponse {

    @JsonProperty("is_success") private boolean success;
    private String message;
    private Data   data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String message;
        @JsonProperty("access_token")  private String accessToken;
        @JsonProperty("refresh_token") private String refreshToken;
        @JsonProperty("expire_at")     private String expireAt;
    }
}
