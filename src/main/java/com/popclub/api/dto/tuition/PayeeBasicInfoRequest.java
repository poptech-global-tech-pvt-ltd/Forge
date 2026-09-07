package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class PayeeBasicInfoRequest {
    @JsonProperty("mobile_number")  private String mobileNumber;
    @JsonProperty("mobile_country_code") private String mobileCountryCode;
}
