package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ReserveLimitRequest {
    @JsonProperty("payment_intent_id") private String paymentIntentId;
    @JsonProperty("category")          @Builder.Default private String category = "TUITION";
    @JsonProperty("order_number")      private String orderNumber;

    public static ReserveLimitRequest from(String paymentIntentId, String orderNumber) {
        return ReserveLimitRequest.builder()
                .paymentIntentId(paymentIntentId)
                .orderNumber(orderNumber)
                .build();
    }
}
