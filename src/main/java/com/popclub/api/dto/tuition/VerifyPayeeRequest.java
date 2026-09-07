package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class VerifyPayeeRequest {
    @JsonProperty("payee_id") private String payeeId;
}
