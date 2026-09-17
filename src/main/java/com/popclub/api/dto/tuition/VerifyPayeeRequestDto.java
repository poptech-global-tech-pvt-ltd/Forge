package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class VerifyPayeeRequestDto {
    @JsonProperty("pan")
    private String pan;

    @JsonProperty("mobile")
    private String mobile;

    @JsonProperty("payee_type")
    private final String payeeType = "TUTOR";

    @JsonProperty("payout_reference_id")
    private String payoutReferenceId;

}
