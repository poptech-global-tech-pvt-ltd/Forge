package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class OrderStatusResponse {

    @JsonProperty("is_success") private boolean success;
    private String message;
    private Data   data;

    @JsonProperty("error_code")    private String errorCode;
    @JsonProperty("error_message") private String errorMessage;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("order_number")    private String orderNumber;
        @JsonProperty("order_type")      private String orderType;
        private String status;
        @JsonProperty("display_status")  private String displayStatus;
        private String message;
        @JsonProperty("payment_status")  private String paymentStatus;
        @JsonProperty("payout_status")   private String payoutStatus;
        @JsonProperty("refund_status")   private String refundStatus;
        private String amount;
        @JsonProperty("created_at")      private String createdAt;
        @JsonProperty("updated_at")      private String updatedAt;
    }
}
