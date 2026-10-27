package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsBasicRequestDto {

    @JsonProperty("first_name")     private String firstName;
    @JsonProperty("middle_name")    private String middleName;
    @JsonProperty("last_name")      private String lastName;
    @JsonProperty("email")          private String email;
    @JsonProperty("dob")            private String dob;
    @JsonProperty("gender")         private String gender;
    @JsonProperty("occupation")     private String occupation;
    @JsonProperty("marital_status") private String maritalStatus;

    @Builder.Default
    @JsonProperty("page_info")
    private String pageInfo = "basic_detail";
}
