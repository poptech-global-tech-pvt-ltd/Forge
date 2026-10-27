package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class GeneratePaymentIntentRequest {
    @JsonProperty("payee_display_id")    private String payeeDisplayId;
    @JsonProperty("total_payable")       private String totalPayable;
    @JsonProperty("gst_convenience_fee") private String gstConvenienceFee;
    @JsonProperty("total_amount")        private String totalAmount;
    @JsonProperty("convenience_fee")     private String convenienceFee;
    @JsonProperty("idempotency_key")     private String idempotencyKey;

    public static GeneratePaymentIntentRequest from(
            String payeeDisplayId, PaymentSummaryResponse.Fee fee, String idempotencyKey) {
        return GeneratePaymentIntentRequest.builder()
                .payeeDisplayId(payeeDisplayId)
                .totalPayable(String.valueOf(fee.getTotalPayable()))
                .gstConvenienceFee(String.valueOf(fee.getGstConvenienceFee()))
                .totalAmount(String.valueOf(fee.getTotalAmount()))
                .convenienceFee(String.valueOf(fee.getConvenienceFee()))
                .idempotencyKey(idempotencyKey)
                .build();
    }

    public static GeneratePaymentIntentRequest withIdempotencyKeyOnly(String idempotencyKey) {
        return GeneratePaymentIntentRequest.builder()
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
