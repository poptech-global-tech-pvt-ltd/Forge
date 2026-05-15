package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VerifySSORequestDto {

    @JsonProperty("mobile_number")
    private String mobileNumber;

    public VerifySSORequestDto(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }
}
