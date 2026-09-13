package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class OrderStatusResponse {
    private String status;
    private Data data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("order_number") private String orderNumber;
        private String status;
        @JsonProperty("updated_at")   private String updatedAt;
    }
}
