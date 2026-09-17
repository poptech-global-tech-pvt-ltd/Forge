package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

<<<<<<< HEAD
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
=======
@Data
@Builder
public class CreateTuitionOrderRequest {

    @JsonProperty("order_type")
    private String orderType;

    @JsonProperty("payment_intent_id")
    private String paymentIntentId;

    private Payment payment;

    @JsonProperty("coins_used")
    private int coinsUsed;

    @JsonProperty("amount_breakdown")
    private List<AmountBreakdown> amountBreakdown;

    @Data
    @Builder
    public static class Payment {

        private String method;

        private CardDetails details;
    }

    @Data
    @Builder
    public static class CardDetails {

        @JsonProperty("card_number")
        private String cardNumber;

        @JsonProperty("expiry_month")
        private String expiryMonth;

        @JsonProperty("expiry_year")
        private String expiryYear;

        private String cvv;

        @JsonProperty("card_holder_name")
        private String cardHolderName;

        @JsonProperty("card_type")
        private String cardType;

        private String flow;

        @JsonProperty("save_card")
        private boolean saveCard;
    }

    @Data
    @Builder
    public static class AmountBreakdown {

        private String key;

        private String label;

        private String value;

        @JsonProperty("display_value")
        private String displayValue;
    }

   // @Data
   // @Builder
   // public static class AmountBreakdown {

     //   @JsonProperty("total_amount")
      //  private String totalAmount;

        //@JsonProperty("convenience_fee")
        //private String convenienceFee;

        //@JsonProperty("gst_convenience_fee")
        //private String gstConvenienceFee;

        //@JsonProperty("total_payable")
        //private String totalPayable;
    //}
>>>>>>> 7679027 (feat(tuition-c2c):)
}
