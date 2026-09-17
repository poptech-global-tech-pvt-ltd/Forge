package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class HybridJourneyRequestDto {

    @JsonProperty("is_hybrid_journey")
    public boolean isHybridJourney = true;
}
