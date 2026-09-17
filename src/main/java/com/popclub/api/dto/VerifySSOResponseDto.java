package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VerifySSOResponseDto {

    @JsonProperty("is_success")
    private boolean isSuccess;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private Data data;

    public boolean isSuccess()  { return isSuccess; }
    public String getMessage()  { return message; }
    public Data getData()       { return data; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {

        @JsonProperty("mobile_number")
        private String mobileNumber;

        @JsonProperty("token")
        private String token;

        @JsonProperty("user_id")
        private String userId;

        public String getMobileNumber() { return mobileNumber; }
        public String getToken()        { return token; }
        public String getUserId()       { return userId; }
    }
}
