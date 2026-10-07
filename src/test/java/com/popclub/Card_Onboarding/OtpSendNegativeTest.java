package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.dto.OtpSendRequestDto;
import com.popclub.cardapi.impl.CardService;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class OtpSendNegativeTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: OtpSendNegativeTest (-ve)");
        cardService = new CardService();
    }

    private String msg(String testName, Response response) {
        return "[" + testName + "] Expected 4xx, got " + response.statusCode()
                + " | API message: " + response.jsonPath().getString("message");
    }

    @Test
    public void otpSend_emptyMobile() {
        Response response = cardService.sendOtp(new OtpSendRequestDto(""));
        assertTrue(response.statusCode() >= 400, msg("otpSend_emptyMobile", response));
    }

    @Test
    public void otpSend_shortMobile() {
        Response response = cardService.sendOtp(new OtpSendRequestDto("12345"));
        assertTrue(response.statusCode() >= 400, msg("otpSend_shortMobile", response));
    }

    @Test
    public void otpSend_longMobile() {
        Response response = cardService.sendOtp(new OtpSendRequestDto("123456789012345"));
        assertTrue(response.statusCode() >= 400, msg("otpSend_longMobile", response));
    }

    @Test
    public void otpSend_specialCharsMobile() {
        Response response = cardService.sendOtp(new OtpSendRequestDto("!@#$%^&*()"));
        assertTrue(response.statusCode() >= 400, msg("otpSend_specialCharsMobile", response));
    }

    @Test
    public void otpSend_emptyBody() {
        Response response = cardService.sendOtpRaw("{}");
        assertTrue(response.statusCode() >= 400, msg("otpSend_emptyBody", response));
    }

    @Test
    public void otpSend_malformedJson() {
        Response response = cardService.sendOtpRaw("{invalid json");
        assertTrue(response.statusCode() >= 400, msg("otpSend_malformedJson", response));
    }
}
