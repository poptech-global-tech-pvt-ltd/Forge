package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentSummaryResponse {

    @JsonProperty("is_success") private boolean success;
    private String message;
    private Data   data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private Fee   fee;
        private Payee payee;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fee {
        @JsonProperty("total_cta_amount")     private String totalCtaAmount;
        @JsonProperty("total_amount")         private double totalAmount;
        @JsonProperty("convenience_fee")      private double convenienceFee;
        @JsonProperty("gst_convenience_fee")  private double gstConvenienceFee;
        @JsonProperty("total_payable")        private double totalPayable;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payee {
        private String vpa;
        private String name;
        @JsonProperty("payee_id")         private String payeeId;
        @JsonProperty("payee_display_id") private String payeeDisplayId;
        @JsonProperty("masked_phone")     private String maskedPhone;
        @JsonProperty("masked_pan")       private String maskedPan;
    }
}
