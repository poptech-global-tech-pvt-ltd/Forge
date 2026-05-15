package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PopConsentRequestDto {

    @JsonProperty("mobile_number")
    private String mobileNumber;

    @JsonProperty("consents")
    private List<Consent> consents;

    public PopConsentRequestDto(String mobileNumber, List<Consent> consents) {
        this.mobileNumber = mobileNumber;
        this.consents = consents;
    }

    public static class Consent {

        @JsonProperty("name")
        private String name;

        @JsonProperty("title")
        private String title;

        @JsonProperty("is_parent")
        private boolean isParent;

        @JsonProperty("is_mandatory")
        private boolean isMandatory;

        @JsonProperty("value")
        private boolean value;

        public Consent(String name, String title, boolean isParent, boolean isMandatory, boolean value) {
            this.name = name;
            this.title = title;
            this.isParent = isParent;
            this.isMandatory = isMandatory;
            this.value = value;
        }
    }
}
