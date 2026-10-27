package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class SendOtpRequest {
    @JsonProperty("mobile_number")  private String mobileNumber;
    @JsonProperty("country_code")   private String countryCode;

    public static SendOtpRequest forMobile(String mobileNumber) {
        return SendOtpRequest.builder()
                .mobileNumber(mobileNumber)
                .countryCode("+91")
                .build();
    }
}
