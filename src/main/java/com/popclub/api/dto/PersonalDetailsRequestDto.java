package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalDetailsRequestDto {

    @JsonProperty("name_on_card") private String nameOnCard;
    @JsonProperty("father_name")  private String fatherName;
}
