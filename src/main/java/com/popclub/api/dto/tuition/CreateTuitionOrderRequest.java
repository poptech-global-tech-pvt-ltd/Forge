package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class CreateTuitionOrderRequest {
    @JsonProperty("payment_intent_id") private String paymentIntentId;
    @JsonProperty("payee_id")          private String payeeId;
    private long                                      amount;
    private String                                    remarks;
}
