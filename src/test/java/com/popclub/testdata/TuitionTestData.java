package com.popclub.testdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.InputStream;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class TuitionTestData {

    private Tutor   tutor;
    private Payment payment;
    private Consent consent;

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
        @JsonProperty("total_payable")       private String totalPayable;
        @JsonProperty("convenience_fee")     private String convenienceFee;
        @JsonProperty("gst_convenience_fee") private String gstConvenienceFee;
        @JsonProperty("card_number")         private String cardNumber;
        @JsonProperty("expiry_month")        private String expiryMonth;
        @JsonProperty("expiry_year")         private String expiryYear;
        private String cvv;
        @JsonProperty("card_holder_name")    private String cardHolderName;
        @JsonProperty("card_type")           private String cardType;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Consent {
        private String category;
        @JsonProperty("payee_action")        private String payeeAction;
        @JsonProperty("consent_type_id")     private String consentTypeId;
        @JsonProperty("consent_version_id")  private String consentVersionId;
        private String action;
    }
}
