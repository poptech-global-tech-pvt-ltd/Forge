package com.popclub.api.dto.tuition;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class VerifyOtpRequest {
    private String code;
    private String otp;
}
