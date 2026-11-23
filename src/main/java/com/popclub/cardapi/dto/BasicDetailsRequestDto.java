package com.popclub.cardapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BasicDetailsRequestDto {

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("middle_name")
    private String middleName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("email")
    private String email;

    @JsonProperty("dob")
    private String dob;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("occupation")
    private String occupation;

    @JsonProperty("marital_status")
    private String maritalStatus;

    @JsonProperty("page_info")
    private String pageInfo;

    public BasicDetailsRequestDto(String firstName, String lastName, String email,
                                   String dob, String gender, String occupation, String maritalStatus) {
        this.firstName = firstName;
        this.middleName = "";
        this.lastName = lastName;
        this.email = email;
        this.dob = dob;
        this.gender = gender;
        this.occupation = occupation;
        this.maritalStatus = maritalStatus;
        this.pageInfo = "basic_detail";
    }

    public String getFirstName()     { return firstName; }
    public String getMiddleName()    { return middleName; }
    public String getLastName()      { return lastName; }
    public String getEmail()         { return email; }
    public String getDob()           { return dob; }
    public String getGender()        { return gender; }
    public String getOccupation()    { return occupation; }
    public String getMaritalStatus() { return maritalStatus; }
    public String getPageInfo()      { return pageInfo; }
}
