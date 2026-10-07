package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.dto.YblMockRequestDto;
import com.popclub.cardapi.impl.MockCardService;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L7_Consent_PositiveTest {

    private MockCardService mockCardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: L7_Consent_PositiveTest (+ve)");
        mockCardService = new MockCardService();
    }

    @Test(priority = 1)
    public void consent_defaultSuccess() {
        Response response = mockCardService.postConsent(YblMockRequestDto.consentRequest(), "default");

        assertEquals(response.statusCode(), 200);
    }

    @Test(priority = 2)
    public void consent_fullSuccess() {
        Response response = mockCardService.postConsent(YblMockRequestDto.consentRequest(), "full_success");

        assertEquals(response.statusCode(), 200);
        assertEquals(response.jsonPath().getString("responseCode"), "0000");
        assertEquals(response.jsonPath().getString("responseMessage"), "Success");
        assertEquals(response.jsonPath().getString("journeyStatus"), "Active");
        assertEquals(response.jsonPath().getString("stageName"), "consent");
    }
}
