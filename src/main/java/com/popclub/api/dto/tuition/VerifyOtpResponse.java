package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class VerifyOtpResponse {
    private String status;
    private Data data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String token;
        @JsonProperty("access_token") private String accessToken;
        @JsonProperty("mobile_number") private String mobileNumber;
    }
}
