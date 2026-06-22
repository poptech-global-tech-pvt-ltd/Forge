package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SignupResponseDto {

    // Top-level user_id / id (some endpoints return it here)
    @JsonProperty("user_id")
    public Object userId;

    @JsonProperty("id")
    public Object id;

    // Nested data object (hashira signup returns { "data": { "user_id": ..., "global_id": ... } })
    @JsonProperty("data")
    public SignupDataDto data;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SignupDataDto {
        @JsonProperty("user_id")
        public Object userId;

        @JsonProperty("global_id")
        public String globalId;
    }

    /** Returns userId as string regardless of whether the API returned a number or string. */
    public String resolvedUserId() {
        // prefer nested data.user_id, fallback to root-level fields
        if (data != null && data.userId != null) return String.valueOf(data.userId);
        Object val = userId != null ? userId : id;
        return val != null ? String.valueOf(val) : null;
    }
}
