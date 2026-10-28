package com.popclub.cardapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PersonalDetailsRequestDto {

    @JsonProperty("name_on_card")
    private String nameOnCard;

    @JsonProperty("father_name")
    private String fatherName;

    public PersonalDetailsRequestDto(String nameOnCard, String fatherName) {
        this.nameOnCard = nameOnCard;
        this.fatherName = fatherName;
    }

    public String getNameOnCard() { return nameOnCard; }
    public String getFatherName() { return fatherName; }
}
