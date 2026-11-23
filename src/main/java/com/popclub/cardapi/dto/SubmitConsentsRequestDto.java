package com.popclub.cardapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SubmitConsentsRequestDto {

    @JsonProperty("mobile_number")
    private String mobileNumber;

    @JsonProperty("consents")
    private List<ConsentItemDto> consents;

    public SubmitConsentsRequestDto(String mobileNumber, List<ConsentItemDto> consents) {
        this.mobileNumber = mobileNumber;
        this.consents = consents;
    }

    public String getMobileNumber() { return mobileNumber; }
    public List<ConsentItemDto> getConsents() { return consents; }
}
