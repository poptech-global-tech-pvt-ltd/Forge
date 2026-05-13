package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class CardAttributesRequestDto {

    @JsonProperty("phone_number")
    public String phoneNumber;

    @JsonProperty("attributes")
    public Map<String, String> attributes;

    public CardAttributesRequestDto(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.attributes  = Map.of("card_status", "STARTED", "card_program", "CARD_OLPP_YBL");
    }
}
