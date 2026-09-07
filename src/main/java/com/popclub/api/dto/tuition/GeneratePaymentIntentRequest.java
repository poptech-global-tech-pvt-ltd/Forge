package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class GeneratePaymentIntentRequest {
    @JsonProperty("payee_id")   private String payeeId;
    private long                               amount;
    private String                             remarks;
    @JsonProperty("device_id")  private String deviceId;
}
