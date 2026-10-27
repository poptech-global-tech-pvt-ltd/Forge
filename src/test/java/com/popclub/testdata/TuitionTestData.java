package com.popclub.testdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.InputStream;

/**
 * Tuition / Education-Fee test fixtures — no hardcoded tutor/payment/invalid-case
 * strings in the test classes, same convention as {@link TestUser}/{@link InvalidCases}.
 */
@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class TuitionTestData {

    private Tutor        tutor;
    private Payment      payment;
    private Consent      consent;
    @JsonProperty("invalid_cases") private InvalidCases invalidCases;

    private static final TuitionTestData INSTANCE;

    static {
        try (InputStream in = TuitionTestData.class.getClassLoader()
                .getResourceAsStream("testdata/tuition/tuition_test_data.json")) {
            INSTANCE = new ObjectMapper().readValue(in, TuitionTestData.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load tuition_test_data.json", e);
        }
    }

    public static TuitionTestData get() { return INSTANCE; }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tutor {
        private String mobile;
        private String pan;
        @JsonProperty("payee_type") private String payeeType;
        private String vpa;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payment {
        private String amount;
        @JsonProperty("card_number")         private String cardNumber;
        @JsonProperty("expiry_month")        private String expiryMonth;
        @JsonProperty("expiry_year")         private String expiryYear;
        private String cvv;
        @JsonProperty("card_holder_name")    private String cardHolderName;
        @JsonProperty("card_type")           private String cardType;
        private String otp;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Consent {
        private String category;
        @JsonProperty("payee_action")        private String payeeAction;
        @JsonProperty("consent_type_id")     private String consentTypeId;
        @JsonProperty("consent_version_id")  private String consentVersionId;
        private String action;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InvalidCases {
        @JsonProperty("invalid_payment_option")      private String invalidPaymentOption;
        @JsonProperty("well_formed_vpa")              private String wellFormedVpa;
        @JsonProperty("invalid_vpa")                  private String invalidVpa;
        @JsonProperty("malformed_pan")                private String malformedPan;
        @JsonProperty("unresolvable_pan")             private String unresolvablePan;
        @JsonProperty("rate_limit_sample_pan")        private String rateLimitSamplePan;
        @JsonProperty("rate_limit_sample_mobile")     private String rateLimitSampleMobile;
        @JsonProperty("generic_mobile")               private String genericMobile;
        @JsonProperty("unknown_payment_intent_id")    private String unknownPaymentIntentId;
        @JsonProperty("dummy_card")                   private DummyCard dummyCard;
        @JsonProperty("dummy_amount_breakdown")       private DummyAmountBreakdown dummyAmountBreakdown;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DummyCard {
        @JsonProperty("card_number")      private String cardNumber;
        @JsonProperty("expiry_month")     private String expiryMonth;
        @JsonProperty("expiry_year")      private String expiryYear;
        private String cvv;
        @JsonProperty("card_holder_name") private String cardHolderName;
        @JsonProperty("card_type")        private String cardType;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DummyAmountBreakdown {
        @JsonProperty("total_amount")        private String totalAmount;
        @JsonProperty("convenience_fee")     private String convenienceFee;
        @JsonProperty("gst_convenience_fee") private String gstConvenienceFee;
        @JsonProperty("total_payable")       private String totalPayable;
    }
}
