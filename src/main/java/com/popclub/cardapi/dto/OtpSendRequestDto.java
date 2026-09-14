package com.popclub.cardapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OtpSendRequestDto {

    @JsonProperty("mobile_number")
    private String mobileNumber;

    public OtpSendRequestDto(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getMobileNumber() { return mobileNumber; }
}