package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class ReserveLimitResponse {
    private String status;
    private Data data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("reserve_id")        private String reserveId;
        @JsonProperty("payment_intent_id") private String paymentIntentId;
        private long amount;
    }
}
