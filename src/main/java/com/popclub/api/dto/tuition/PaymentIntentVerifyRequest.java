package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class PaymentIntentVerifyRequest {
    private String category;
    @JsonProperty("order_number") private String orderNumber;
    @JsonProperty("payer_id")     private String payerId;
    private Fee                                  fee;

    @Data @Builder
    public static class Fee {
        @JsonProperty("total_payable")       private String totalPayable;
        @JsonProperty("convenience_fee")     private String convenienceFee;
        @JsonProperty("gst_convenience_fee") private String gstConvenienceFee;
        @JsonProperty("total_amount")        private String totalAmount;
    }
}
