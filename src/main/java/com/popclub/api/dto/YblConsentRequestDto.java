package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class YblConsentRequestDto {

    @JsonProperty("is_politically_exposed")    private boolean isPoliticallyExposed = false;
    @JsonProperty("officer_relation_consent")  private boolean officerRelationConsent = false;
    @JsonProperty("bank_officer_name")         private String bankOfficerName = "";
    @JsonProperty("relationships_with_officer") private String relationshipsWithOfficer = "";
    @JsonProperty("term_condition_consent")    private boolean termConditionConsent = true;
    @JsonProperty("yesbank_authorize_consent") private boolean yesbankAuthorizeConsent = true;
    @JsonProperty("promo_consent")             private boolean promoConsent = true;
    @JsonProperty("cibil_consent")             private boolean cibilConsent = true;
    @JsonProperty("user_comm_consent")         private boolean userCommConsent = true;
    @JsonProperty("kfs_consent")               private boolean kfsConsent = true;
    @JsonProperty("yesbank_gogreen_consent")   private boolean yesbankGogreenConsent = true;
    @JsonProperty("yesbank_cross_selling")     private boolean yesbankCrossSelling = true;
    @JsonProperty("digit_app_consent")         private boolean digitAppConsent = true;
}
