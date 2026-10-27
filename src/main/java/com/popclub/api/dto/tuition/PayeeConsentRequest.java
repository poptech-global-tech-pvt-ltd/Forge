package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class PayeeConsentRequest {
    @JsonProperty("category")     @Builder.Default private String category = "tuition";
    @JsonProperty("payee_action") private String payeeAction; // "confirm" | "reject"
    @JsonProperty("code")         private String code;
    private List<ConsentItem>                     consent;

    public static PayeeConsentRequest from(String payeeAction, String code, List<ConsentItem> consent) {
        return PayeeConsentRequest.builder()
                .payeeAction(payeeAction)
                .code(code)
                .consent(consent)
                .build();
    }

    @Data @Builder
    public static class ConsentItem {
        @JsonProperty("consent_type_id")    private String consentTypeId;
        @JsonProperty("consent_version_id") private String consentVersionId;
        private String                                      action; // "granted" | "rejected"

        public static ConsentItem of(String consentTypeId, String consentVersionId, String action) {
            return ConsentItem.builder()
                    .consentTypeId(consentTypeId)
                    .consentVersionId(consentVersionId)
                    .action(action)
                    .build();
        }
    }
}
