package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class CreateTuitionOrderResponse {
    private String status;
    private Data data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("order_number")  private String orderNumber;
        @JsonProperty("invite_code")   private String inviteCode;
        private String status;
        @JsonProperty("payment_intent_id") private String paymentIntentId;
    }
}
