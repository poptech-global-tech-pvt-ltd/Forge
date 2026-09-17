package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentIntentResponse {
    private String status;
    private Data data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("payment_intent_id") private String paymentIntentId;
        @JsonProperty("razorpay_order_id") private String razorpayOrderId;
        @JsonProperty("razorpay_key_id")   private String razorpayKeyId;
        private long amount;
        private String currency;
    }
}
