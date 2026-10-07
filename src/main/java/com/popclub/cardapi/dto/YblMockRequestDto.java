package com.popclub.cardapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class YblMockRequestDto {

    public String prn;
    public String jtid;
    public String productCode;
    public String stageName;
    public String OfferStatus;
    public String customerType;
    public String responseCode;
    public String responseMessage;
    public String journeyStatus;
    public String url;
    public String ckycAllowed;

    public YblMockRequestDto() {}

    // Consent request
    public static YblMockRequestDto consentRequest() {
        YblMockRequestDto dto = new YblMockRequestDto();
        dto.prn             = "POPCLUB-1718800000001abc";
        dto.jtid            = "JT-YBL-98765";
        dto.productCode     = "cc";
        dto.stageName       = "consent";
        dto.OfferStatus     = "approved";
        dto.customerType    = "NTB";
        dto.responseCode    = "0000";
        dto.responseMessage = "Success";
        dto.journeyStatus   = "Active";
        return dto;
    }

    // EKYC request
    public static YblMockRequestDto ekycRequest() {
        YblMockRequestDto dto = new YblMockRequestDto();
        dto.prn             = "POPCLUB-1718800000001abc";
        dto.jtid            = "JT-YBL-98765";
        dto.stageName       = "ekyc";
        dto.responseCode    = "0000";
        dto.responseMessage = "Success";
        dto.journeyStatus   = "Active";
        dto.url             = "";
        dto.ckycAllowed     = "Y";
        return dto;
    }

    // VKYC request
    public static YblMockRequestDto vkycRequest() {
        YblMockRequestDto dto = new YblMockRequestDto();
        dto.prn             = "POPCLUB-1718800000001abc";
        dto.jtid            = "JT-YBL-98765";
        dto.stageName       = "vkyc";
        dto.responseCode    = "0000";
        dto.responseMessage = "Success";
        dto.journeyStatus   = "Active";
        dto.url             = "https://yesbank.in/vkyc/session/xyz789";
        return dto;
    }
}
