package com.popclub.api.dto.tuition;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class SendOtpRequest {
    private String code;
}
