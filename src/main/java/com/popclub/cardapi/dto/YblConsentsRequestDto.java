package com.popclub.cardapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class YblConsentsRequestDto {

    @JsonProperty("is_politically_exposed")
    private boolean isPoliticallyExposed;

    @JsonProperty("officer_relation_consent")
    private boolean officerRelationConsent;

    @JsonProperty("bank_officer_name")
    private String bankOfficerName;

    @JsonProperty("relationships_with_officer")
    private String relationshipsWithOfficer;

    @JsonProperty("term_condition_consent")
    private boolean termConditionConsent;

    @JsonProperty("yesbank_authorize_consent")
    private boolean yesbankAuthorizeConsent;

    @JsonProperty("promo_consent")
    private boolean promoConsent;

    @JsonProperty("cibil_consent")
    private boolean cibilConsent;

    @JsonProperty("user_comm_consent")
    private boolean userCommConsent;

    @JsonProperty("kfs_consent")
    private boolean kfsConsent;

    @JsonProperty("yesbank_gogreen_consent")
    private boolean yesbankGogreenConsent;

    @JsonProperty("yesbank_cross_selling")
    private boolean yesbankCrossSelling;

    @JsonProperty("digit_app_consent")
    private boolean digitAppConsent;

    public YblConsentsRequestDto(boolean isPoliticallyExposed, boolean officerRelationConsent,
                                  String bankOfficerName, String relationshipsWithOfficer,
                                  boolean termConditionConsent, boolean yesbankAuthorizeConsent,
                                  boolean promoConsent, boolean cibilConsent, boolean userCommConsent,
                                  boolean kfsConsent, boolean yesbankGogreenConsent,
                                  boolean yesbankCrossSelling, boolean digitAppConsent) {
        this.isPoliticallyExposed = isPoliticallyExposed;
        this.officerRelationConsent = officerRelationConsent;
        this.bankOfficerName = bankOfficerName;
        this.relationshipsWithOfficer = relationshipsWithOfficer;
        this.termConditionConsent = termConditionConsent;
        this.yesbankAuthorizeConsent = yesbankAuthorizeConsent;
        this.promoConsent = promoConsent;
        this.cibilConsent = cibilConsent;
        this.userCommConsent = userCommConsent;
        this.kfsConsent = kfsConsent;
        this.yesbankGogreenConsent = yesbankGogreenConsent;
        this.yesbankCrossSelling = yesbankCrossSelling;
        this.digitAppConsent = digitAppConsent;
    }
}
