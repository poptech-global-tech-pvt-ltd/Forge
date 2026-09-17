package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class GeneratePaymentIntentRequest {
<<<<<<< HEAD
    @JsonProperty("payee_display_id")    private String payeeDisplayId;
    @JsonProperty("total_payable")       private String totalPayable;
    @JsonProperty("gst_convenience_fee") private String gstConvenienceFee;
    @JsonProperty("total_amount")        private String totalAmount;
    @JsonProperty("convenience_fee")     private String convenienceFee;
    @JsonProperty("idempotency_key")     private String idempotencyKey;
=======
    @JsonProperty("payee_display_id")     private String payeeDisplayId;
    @JsonProperty("total_payable")        private String totalPayable;
    @JsonProperty("gst_convenience_fee")  private String gstConvenienceFee;
    @JsonProperty("total_amount")         private String totalAmount;
    @JsonProperty("convenience_fee")      private String convenienceFee;
    @JsonProperty("idempotency_key")      private String idempotencyKey;
>>>>>>> 7679027 (feat(tuition-c2c):)
}
