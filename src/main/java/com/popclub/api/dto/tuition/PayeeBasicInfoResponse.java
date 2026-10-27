package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class PayeeBasicInfoResponse {

    @JsonProperty("is_success") private boolean success;
    private String message;
    private Data   data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("flagged_user") private boolean flaggedUser;
        @JsonProperty("vpa_info")     private VpaInfo  vpaInfo;
        @JsonProperty("bank_info")    private BankInfo bankInfo;
        @JsonProperty("payee_info")   private PayeeInfo payeeInfo;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VpaInfo {
        private String  vpa;
        private String  name;
        @JsonProperty("is_valid") private boolean valid;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BankInfo {
        @JsonProperty("is_valid")           private boolean valid;
        @JsonProperty("bank_account_number") private String bankAccountNumber;
        @JsonProperty("bank_ifsc")           private String bankIfsc;
        @JsonProperty("bank_name")           private String bankName;
        @JsonProperty("bank_account_name")   private String bankAccountName;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PayeeInfo {
        @JsonProperty("payout_reference_id") private String payoutReferenceId;
    }
}
