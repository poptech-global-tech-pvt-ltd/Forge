package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class VerifyPayeeResponse {

    @JsonProperty("is_success") private boolean success;
    private String message;
    private Data   data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private Payee   payee;
        @JsonProperty("flagged_user")         private boolean flaggedUser;
        @JsonProperty("is_pan_valid")         private boolean panValid;
        @JsonProperty("is_pan_number_match")  private boolean panNumberMatch;
        @JsonProperty("is_name_match")        private boolean nameMatch;
        private String  vpa;
        private Limit   limit;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payee {
        private String name;
        @JsonProperty("payee_id")         private String payeeId;
        @JsonProperty("payee_display_id") private String payeeDisplayId;
        @JsonProperty("masked_phone")     private String maskedPhone;
        @JsonProperty("masked_pan")       private String maskedPan;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Limit {
        private long min;
        private long max;
    }
}
