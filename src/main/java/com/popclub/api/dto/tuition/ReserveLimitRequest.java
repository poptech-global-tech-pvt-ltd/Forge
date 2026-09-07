package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ReserveLimitRequest {
    @JsonProperty("payment_intent_id") private String paymentIntentId;
    private long                                      amount;
}
