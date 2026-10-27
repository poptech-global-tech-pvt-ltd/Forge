package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SignupRequestDto {

    @JsonProperty("phone")
    public String phone;

    public SignupRequestDto(String phone) { this.phone = phone; }
}
