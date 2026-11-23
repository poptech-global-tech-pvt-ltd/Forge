package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginRequestDto {

    @JsonProperty("api_type")
    public String apiType = "SEND_OTP";

    @JsonProperty("mobile_number")
    public String mobileNumber;

    public LoginRequestDto(String mobileNumber) { this.mobileNumber = mobileNumber; }
}
