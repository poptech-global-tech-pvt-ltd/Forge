package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class PayeeBasicInfoResponse {
    private String status;
    private Data data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("payee_id")      private String payeeId;
        private String name;
        @JsonProperty("mobile_number") private String mobileNumber;
        @JsonProperty("is_verified")   private boolean isVerified;
    }
}
