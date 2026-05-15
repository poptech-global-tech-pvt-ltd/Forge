package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserDetailsPanRequestDto {

    @JsonProperty("pan")
    private String pan;

    @JsonProperty("pin_code")
    private String pinCode;

    @JsonProperty("page_info")
    private String pageInfo = "pan_detail";

    public UserDetailsPanRequestDto(String pan, String pinCode) {
        this.pan = pan;
        this.pinCode = pinCode;
    }
}
