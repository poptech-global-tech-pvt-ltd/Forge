package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsPanRequestDto {

    @JsonProperty("pan")      private String pan;
    @JsonProperty("pin_code") private String pinCode;

    @Builder.Default
    @JsonProperty("page_info")
    private String pageInfo = "pan_detail";
}
