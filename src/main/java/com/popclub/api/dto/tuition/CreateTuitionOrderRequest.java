package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class CreateTuitionOrderRequest {
    @JsonProperty("order_type")        private String            orderType;
    @JsonProperty("payment_intent_id") private String            paymentIntentId;
    private Payment                                               payment;
    @JsonProperty("coins_used")        private int               coinsUsed;
    @JsonProperty("amount_breakdown")  private List<AmountBreakdown> amountBreakdown;

    /** Empty body — for INVALID_PARAMETERS validation tests. */
    public static CreateTuitionOrderRequest empty() {
        return CreateTuitionOrderRequest.builder().build();
    }

    /** Full CARD-payment order request. */
    public static CreateTuitionOrderRequest cardOrder(
            String orderType, String paymentIntentId, Payment payment,
            int coinsUsed, List<AmountBreakdown> amountBreakdown) {
        return CreateTuitionOrderRequest.builder()
                .orderType(orderType)
                .paymentIntentId(paymentIntentId)
                .payment(payment)
                .coinsUsed(coinsUsed)
                .amountBreakdown(amountBreakdown)
                .build();
    }

    @Data @Builder
    public static class Payment {
        private String      method;
        private CardDetails details;

        public static Payment cardPayment(CardDetails details) {
            return Payment.builder()
                    .method("CARD")
                    .details(details)
                    .build();
        }
    }

    @Data @Builder
    public static class CardDetails {
        @JsonProperty("card_number")      private String  cardNumber;
        @JsonProperty("expiry_month")     private String  expiryMonth;
        @JsonProperty("expiry_year")      private String  expiryYear;
        private String                                    cvv;
        @JsonProperty("card_holder_name") private String  cardHolderName;
        @JsonProperty("card_type")        private String  cardType;
        private String                                    flow;
        @JsonProperty("save_card")        private boolean saveCard;

        public static CardDetails of(String cardNumber, String expiryMonth, String expiryYear,
                                      String cvv, String cardHolderName, String cardType) {
            return CardDetails.builder()
                    .cardNumber(cardNumber)
                    .expiryMonth(expiryMonth)
                    .expiryYear(expiryYear)
                    .cvv(cvv)
                    .cardHolderName(cardHolderName)
                    .cardType(cardType)
                    .flow("non-save")
                    .saveCard(false)
                    .build();
        }
    }

    @Data @Builder
    public static class AmountBreakdown {
        private String key;
        private String label;
        private String value;
        @JsonProperty("display_value")
        private String displayValue;

        /** Builds the standard 4-line amount_breakdown from a payment-summary fee quote. */
        public static List<AmountBreakdown> listFrom(PaymentSummaryResponse.Fee fee) {
            return listFrom(
                    String.valueOf(fee.getTotalAmount()),
                    String.valueOf(fee.getConvenienceFee()),
                    String.valueOf(fee.getGstConvenienceFee()),
                    String.valueOf(fee.getTotalPayable())
            );
        }

        /** Builds the standard 4-line amount_breakdown from raw string values. */
        public static List<AmountBreakdown> listFrom(
                String totalAmount, String convenienceFee, String gstConvenienceFee, String totalPayable) {
            return List.of(
                    AmountBreakdown.builder()
                            .key("total_amount").label("Tuition Fee")
                            .value(totalAmount).displayValue("₹" + totalAmount).build(),
                    AmountBreakdown.builder()
                            .key("convenience_fee").label("Convenience Fee")
                            .value(convenienceFee).displayValue("₹" + convenienceFee).build(),
                    AmountBreakdown.builder()
                            .key("gst_convenience_fee").label("GST")
                            .value(gstConvenienceFee).displayValue("₹" + gstConvenienceFee).build(),
                    AmountBreakdown.builder()
                            .key("total_payable").label("Total Payable")
                            .value(totalPayable).displayValue("₹" + totalPayable).build()
            );
        }
    }
}
