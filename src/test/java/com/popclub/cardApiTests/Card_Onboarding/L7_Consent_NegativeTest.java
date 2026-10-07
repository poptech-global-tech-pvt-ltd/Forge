package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.dto.YblMockRequestDto;
import com.popclub.cardapi.impl.MockCardService;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L7_Consent_NegativeTest {

    private MockCardService mockCardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: L7_Consent_NegativeTest (-ve)");
        mockCardService = new MockCardService();
    }

    @Test(priority = 1)
    public void consent_responseCodeNotZero() {
        Response response = mockCardService.postConsent(YblMockRequestDto.consentRequest(), "response_code_not_0000");

        assertEquals(response.statusCode(), 200);
        assertNotEquals(response.jsonPath().getString("responseCode"), "0000");
    }

    @Test(priority = 2)
    public void consent_journeyStatusNotActive() {
        Response response = mockCardService.postConsent(YblMockRequestDto.consentRequest(), "journey_status_not_active");

        assertEquals(response.statusCode(), 200);
        assertNotEquals(response.jsonPath().getString("journeyStatus"), "Active");
    }

    @Test(priority = 3)
    public void consent_standardJsonError_400() {
        Response response = mockCardService.postConsent(YblMockRequestDto.consentRequest(), "http_non200_standard_json_error");

        assertEquals(response.statusCode(), 400);
    }

    @Test(priority = 4)
    public void consent_serverError_500() {
        Response response = mockCardService.postConsent(YblMockRequestDto.consentRequest(), "http_non200_common_ybl_error_500");

        assertEquals(response.statusCode(), 500);
    }

    @Test(priority = 5)
    public void consent_unprocessableEntity_422() {
        Response response = mockCardService.postConsent(YblMockRequestDto.consentRequest(), "http_non200_common_ybl_error_422");

        assertEquals(response.statusCode(), 422);
    }

    @Test(priority = 6)
    public void consent_tooManyRequests_429() {
        // Mock returns 200 with ResponseCode "429" in body for this scenario
        Response response = mockCardService.postConsent(YblMockRequestDto.consentRequest(), "http_non200_common_ybl_error_429");

        assertEquals(response.statusCode(), 200);
        assertEquals(response.jsonPath().getString("httpCode"), "429");
    }

    @Test(priority = 7)
    public void consent_unauthorized_401() {
        Response response = mockCardService.postConsent(YblMockRequestDto.consentRequest(), "http_non200_common_ybl_error_401");

        assertEquals(response.statusCode(), 401);
    }

    @Test(priority = 8)
    public void consent_methodNotAllowed_405() {
        Response response = mockCardService.postConsent(YblMockRequestDto.consentRequest(), "http_non200_common_ybl_error_405");

        assertEquals(response.statusCode(), 405);
    }

    @Test(priority = 9)
    public void consent_encryptedErrorBody_400() {
        Response response = mockCardService.postConsent(YblMockRequestDto.consentRequest(), "http_non200_encrypted_error_body");

        assertEquals(response.statusCode(), 400);
    }
}
