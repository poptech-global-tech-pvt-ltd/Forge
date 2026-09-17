package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LimitActionRequest {
    @JsonProperty("payment_intent_id")    private String paymentIntentId;
    private String                                       category;
    @JsonProperty("limit_reservation_id") private String limitReservationId;
    @JsonProperty("order_number")         private String orderNumber;
}
