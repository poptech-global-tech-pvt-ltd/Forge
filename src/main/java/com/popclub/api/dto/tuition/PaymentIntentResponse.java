package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentIntentResponse {

    @JsonProperty("is_success") private boolean success;
    private String message;
    private Data   data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("is_active")         private boolean active;
        @JsonProperty("idempotency_key")   private String  idempotencyKey;
        @JsonProperty("payment_intent_id") private String  paymentIntentId;
        @JsonProperty("payee_id")          private String  payeeId;
        @JsonProperty("payee_display_id")  private String  payeeDisplayId;
        @JsonProperty("payer_id")          private String  payerId;
        @JsonProperty("payout_type")       private String  payoutType;
        private String vpa;
        private String status;
        @JsonProperty("total_payable")     private String  totalPayable;
        @JsonProperty("created_at")        private String  createdAt;
    }
}
