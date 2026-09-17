package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentSummaryResponse {
    private String status;
    private Data data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private long amount;
        @JsonProperty("platform_fee")  private long platformFee;
        private long gst;
        @JsonProperty("total_amount")  private long totalAmount;
        @JsonProperty("fee_percentage") private double feePercentage;
    }
}
