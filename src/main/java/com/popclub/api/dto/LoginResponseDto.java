package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginResponseDto {

    @JsonProperty("is_success")
    public boolean isSuccess;

    @JsonProperty("message")
    public String message;

    @JsonProperty("data")
    public Data data;

    public boolean isFirstTimeUser() {
        return data != null && data.isFirstTimeUser;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("is_first_time_user")
        public boolean isFirstTimeUser;

        @JsonProperty("account_status")
        public String accountStatus;
    }
}
