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
}
