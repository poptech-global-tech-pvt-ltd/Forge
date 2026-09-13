package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class CreateTuitionOrderRequest {
    @JsonProperty("order_type")        private String       orderType;
    @JsonProperty("payment_intent_id") private String       paymentIntentId;
    private Payment                                         payment;
    @JsonProperty("coins_used")        private int          coinsUsed;
    @JsonProperty("amount_breakdown")  private List<Object> amountBreakdown;

    @Data @Builder
    public static class Payment {
        private String  method;
        private Details details;
    }

    @Data @Builder
    public static class Details {
        @JsonProperty("card_number")      private String  cardNumber;
        @JsonProperty("expiry_month")     private String  expiryMonth;
        @JsonProperty("expiry_year")      private String  expiryYear;
        private String                                    cvv;
        @JsonProperty("card_holder_name") private String  cardHolderName;
        @JsonProperty("card_type")        private String  cardType;
        private String                                    flow;
        @JsonProperty("save_card")        private boolean saveCard;
    }
}
