package com.popclub.cardapi.impl;

import com.popclub.cardapi.enums.Routes;
import io.restassured.response.Response;

public class MockCardService extends MockBaseService {

    public Response postConsent(Object body, String scenario) {
        return post(Routes.MOCK_CONSENT, body, scenario);
    }

    public Response postEkyc(Object body, String scenario) {
        return post(Routes.MOCK_EKYC, body, scenario);
    }

    public Response postVkyc(Object body, String scenario) {
        return post(Routes.MOCK_VKYC, body, scenario);
    }
}
