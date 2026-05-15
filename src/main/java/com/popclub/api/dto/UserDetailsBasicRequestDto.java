package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserDetailsBasicRequestDto {

    @JsonProperty("first_name")    private String firstName;
    @JsonProperty("middle_name")   private String middleName;
    @JsonProperty("last_name")     private String lastName;
    @JsonProperty("email")         private String email;
    @JsonProperty("dob")           private String dob;
    @JsonProperty("gender")        private String gender;
    @JsonProperty("occupation")    private String occupation;
    @JsonProperty("marital_status") private String maritalStatus;
    @JsonProperty("page_info")     private String pageInfo = "basic_detail";

    public UserDetailsBasicRequestDto(String firstName, String middleName, String lastName,
                                   String email, String dob, String gender,
                                   String occupation, String maritalStatus) {
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.email = email;
        this.dob = dob;
        this.gender = gender;
        this.occupation = occupation;
        this.maritalStatus = maritalStatus;
    }
}
