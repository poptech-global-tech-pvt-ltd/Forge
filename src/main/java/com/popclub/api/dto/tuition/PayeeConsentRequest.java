package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class PayeeConsentRequest {
    private String             category;
    @JsonProperty("payee_action") private String payeeAction;
    private String             code;
    private List<ConsentItem>  consent;

    @Data @Builder
    public static class ConsentItem {
        @JsonProperty("consent_type_id")    private String consentTypeId;
        @JsonProperty("consent_version_id") private String consentVersionId;
        private String                                     action;
    }
}
