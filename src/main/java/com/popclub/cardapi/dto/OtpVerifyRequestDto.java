package com.popclub.cardapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OtpVerifyRequestDto {

    @JsonProperty("mobile_number")
    private String mobileNumber;

    @JsonProperty("otp")
    private String otp;

    public OtpVerifyRequestDto(String mobileNumber, String otp) {
        this.mobileNumber = mobileNumber;
        this.otp = otp;
    }

    public String getMobileNumber() { return mobileNumber; }
    public String getOtp() { return otp; }
}
