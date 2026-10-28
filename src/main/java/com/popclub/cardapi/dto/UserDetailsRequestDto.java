package com.popclub.cardapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserDetailsRequestDto {

    @JsonProperty("pan")
    private String pan;

    @JsonProperty("pin_code")
    private String pinCode;

    @JsonProperty("page_info")
    private String pageInfo;

    public UserDetailsRequestDto(String pan, String pinCode) {
        this.pan = pan;
        this.pinCode = pinCode;
        this.pageInfo = "pan_detail";
    }

    public String getPan() { return pan; }
    public String getPinCode() { return pinCode; }
    public String getPageInfo() { return pageInfo; }
}
