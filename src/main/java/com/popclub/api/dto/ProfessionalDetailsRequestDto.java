package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProfessionalDetailsRequestDto {

    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("designation")
    private String designation;

    @JsonProperty("annual_income")
    private String annualIncome;

    @JsonProperty("company_type")
    private String companyType;

    @JsonProperty("profession")
    private String profession;

    @JsonProperty("occupation")
    private String occupation;

    public ProfessionalDetailsRequestDto(String companyName, String designation,
                                      String annualIncome, String companyType,
                                      String profession, String occupation) {
        this.companyName = companyName;
        this.designation = designation;
        this.annualIncome = annualIncome;
        this.companyType = companyType;
        this.profession = profession;
        this.occupation = occupation;
    }
}
