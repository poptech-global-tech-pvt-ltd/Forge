package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class VerifyPayeeRequest {
    private String pan;
    private String mobile;
    @JsonProperty("payee_type")          private String payeeType;
    @JsonProperty("payout_reference_id") private String payoutReferenceId;
}
