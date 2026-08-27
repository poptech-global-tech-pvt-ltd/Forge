package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopConsentRequestDto {

    @JsonProperty("mobile_number") private String       mobileNumber;
    @JsonProperty("consents")      private List<Consent> consents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Consent {

        @JsonProperty("name")         private String  name;
        @JsonProperty("title")        private String  title;
        @JsonProperty("is_parent")    private boolean isParent;
        @JsonProperty("is_mandatory") private boolean isMandatory;
        @JsonProperty("value")        private boolean value;
    }
}
