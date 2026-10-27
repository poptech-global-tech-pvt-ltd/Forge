package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class PayeeDetailsResponse {

    @JsonProperty("is_success") private boolean success;
    private Data data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String amount;
        private String code;
        @JsonProperty("expire_at") private String expireAt;
        private Payer  payer;
        private Payee  payee;
        private List<Consent> consent;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payer {
        private String name;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payee {
        private String vpa;
        private String name;
        @JsonProperty("payee_id")      private String payeeId;
        @JsonProperty("masked_phone")  private String maskedPhone;
        @JsonProperty("masked_pan")    private String maskedPan;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Consent {
        private String  id;
        @JsonProperty("tnc_text")     private String  tncText;
        private String  link;
        @JsonProperty("is_given")     private boolean given;
        @JsonProperty("is_mandatory") private boolean mandatory;
    }
}
