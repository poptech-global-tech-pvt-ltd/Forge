package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentIntentVerifyResponse {

    @JsonProperty("is_success") private boolean success;
    private Data data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("payment_allowed") private boolean paymentAllowed;
        private String reason;
        private Payee payee;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payee {
        @JsonProperty("payee_onboarding_status") private String payeeOnboardingStatus;
        @JsonProperty("payee_display_id")         private String payeeDisplayId;
        @JsonProperty("pay_msg_link")             private String payMsgLink;
        @JsonProperty("pay_invite_msg")           private String payInviteMsg;
    }
}
