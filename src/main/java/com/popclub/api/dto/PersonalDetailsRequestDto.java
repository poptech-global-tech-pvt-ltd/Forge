package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PersonalDetailsRequestDto {

    // "user_levle" is the actual API field name (upstream typo)
    @JsonProperty("user_levle")
    private String userLevel;

    @JsonProperty("name_on_card")
    private String nameOnCard;

    @JsonProperty("father_name")
    private String fatherName;

    public PersonalDetailsRequestDto(String userLevel, String nameOnCard, String fatherName) {
        this.userLevel = userLevel;
        this.nameOnCard = nameOnCard;
        this.fatherName = fatherName;
    }
}
