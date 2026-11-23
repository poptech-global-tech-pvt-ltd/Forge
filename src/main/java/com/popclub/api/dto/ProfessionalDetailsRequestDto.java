package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalDetailsRequestDto {

    @JsonProperty("company_name")  private String companyName;
    @JsonProperty("designation")   private String designation;
    @JsonProperty("annual_income") private String annualIncome;
    @JsonProperty("company_type")  private String companyType;
    @JsonProperty("profession")    private String profession;
    @JsonProperty("occupation")    private String occupation;
}
