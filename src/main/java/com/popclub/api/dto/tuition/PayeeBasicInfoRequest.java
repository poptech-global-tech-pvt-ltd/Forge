package com.popclub.api.dto.tuition;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder; import lombok.Data;

@Data @Builder
public class PayeeBasicInfoRequest {
    @JsonProperty("payment_option")      private String paymentOption;
    private String                                      vpa;
    @JsonProperty("bank_account_number") private String bankAccountNumber;
    @JsonProperty("bank_ifsc")           private String bankIfsc;
    @JsonProperty("bank_account_name")   private String bankAccountName;
    @JsonProperty("bank_name")           private String bankName;

    public static PayeeBasicInfoRequest forVpa(String paymentOption, String vpa) {
        return PayeeBasicInfoRequest.builder()
                .paymentOption(paymentOption)
                .vpa(vpa)
                .build();
    }

    // bank_* fields are required-but-empty for payment_option=VPA
    public static PayeeBasicInfoRequest forVpaSubmission(String vpa) {
        return PayeeBasicInfoRequest.builder()
                .paymentOption("VPA")
                .vpa(vpa)
                .bankAccountNumber("")
                .bankIfsc("")
                .bankAccountName("")
                .bankName("")
                .build();
    }
}
