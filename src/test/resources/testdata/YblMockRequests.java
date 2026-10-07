package com.popclub.testdata;

import com.popclub.cardapi.dto.YblMockRequestDto;
import lombok.Data;

@Data
public class YblMockRequests {
    private YblMockRequestDto consent;
    private YblMockRequestDto ekyc;
    private YblMockRequestDto vkyc;

    public static YblMockRequestDto consentRequest() {
        return TestDataLoader.loadYblMockRequests().getConsent();
    }

    public static YblMockRequestDto ekycRequest() {
        return TestDataLoader.loadYblMockRequests().getEkyc();
    }

    public static YblMockRequestDto vkycRequest() {
        return TestDataLoader.loadYblMockRequests().getVkyc();
    }
}
