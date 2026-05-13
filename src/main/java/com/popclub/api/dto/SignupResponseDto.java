package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SignupResponseDto {

    @JsonProperty("user_id")
    public Object userId;

    @JsonProperty("id")
    public Object id;

    /** Returns userId as string regardless of whether the API returned a number or string. */
    public String resolvedUserId() {
        Object val = userId != null ? userId : id;
        return val != null ? String.valueOf(val) : null;
    }
}
