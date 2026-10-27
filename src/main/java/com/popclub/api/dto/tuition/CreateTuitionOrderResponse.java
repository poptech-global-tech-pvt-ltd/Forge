package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class CreateTuitionOrderResponse {

    @JsonProperty("is_success") private boolean success;
    private String message;
    private Data   data;

    // Error envelope ({"is_success":false,"error_code":...,"error_message":...}) shares this shape.
    @JsonProperty("error_code")    private String errorCode;
    @JsonProperty("error_message") private String errorMessage;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("order_number") private String orderNumber;
        @JsonProperty("order_type")   private String orderType;
        private String status;
        @JsonProperty("workflow_id")  private String workflowId;
        @JsonProperty("payee_id")     private String payeeId;
        private String amount;
        @JsonProperty("created_at")   private String createdAt;
    }
}
