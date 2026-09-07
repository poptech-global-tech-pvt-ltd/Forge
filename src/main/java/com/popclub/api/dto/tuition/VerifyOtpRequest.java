package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class VerifyOtpRequest {
    @JsonProperty("mobile_number") private String mobileNumber;
    @JsonProperty("country_code")  private String countryCode;
    private String                               otp;
}
