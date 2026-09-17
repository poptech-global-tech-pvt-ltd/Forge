package com.popclub.api.dto.tuition;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class SendOtpRequest {
<<<<<<< HEAD
    private String code;
=======
    @JsonProperty("mobile_number")  private String mobileNumber;
    @JsonProperty("country_code")   private String countryCode;

>>>>>>> 7679027 (feat(tuition-c2c):)
}
