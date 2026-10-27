package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YblConsentRequestDto {

    @JsonProperty("is_politically_exposed")      @Builder.Default private boolean isPoliticallyExposed    = false;
    @JsonProperty("officer_relation_consent")    @Builder.Default private boolean officerRelationConsent  = false;
    @JsonProperty("bank_officer_name")           @Builder.Default private String  bankOfficerName          = "";
    @JsonProperty("relationships_with_officer")  @Builder.Default private String  relationshipsWithOfficer = "";
    @JsonProperty("term_condition_consent")      @Builder.Default private boolean termConditionConsent    = true;
    @JsonProperty("yesbank_authorize_consent")   @Builder.Default private boolean yesbankAuthorizeConsent = true;
    @JsonProperty("promo_consent")               @Builder.Default private boolean promoConsent            = true;
    @JsonProperty("cibil_consent")               @Builder.Default private boolean cibilConsent            = true;
    @JsonProperty("user_comm_consent")           @Builder.Default private boolean userCommConsent         = true;
    @JsonProperty("kfs_consent")                 @Builder.Default private boolean kfsConsent              = true;
    @JsonProperty("yesbank_gogreen_consent")     @Builder.Default private boolean yesbankGogreenConsent   = true;
    @JsonProperty("yesbank_cross_selling")       @Builder.Default private boolean yesbankCrossSelling     = true;
    @JsonProperty("digit_app_consent")           @Builder.Default private boolean digitAppConsent         = true;
}
