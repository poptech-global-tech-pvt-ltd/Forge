package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.dto.OtpSendRequestDto;
import com.popclub.cardapi.dto.OtpVerifyRequestDto;
import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.ConfigManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class LoginTest {

    private CardService cardService;
    private String mobileNumber;
    private String otp;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: LoginTest (+ve)");
        cardService = new CardService();
        mobileNumber = ConfigManager.getMobileNumber();
        otp = ConfigManager.getOtp();
    }

    @Test(priority = 1)
    public void sendOtp_success() {

        OtpSendRequestDto request = new OtpSendRequestDto(mobileNumber);
        Response response = cardService.sendOtp(request);

        assertEquals(response.statusCode(), 200);
        assertTrue(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("message"), "success");
        assertEquals(response.jsonPath().getString("data.mobile_number"), mobileNumber);
    }

    @Test(priority = 2)
    public void verifyOtp_success() {

        OtpVerifyRequestDto request = new OtpVerifyRequestDto(mobileNumber, otp);
        Response response = cardService.verifyOtp(request);

        assertEquals(response.statusCode(), 200);
        assertTrue(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("data.mobile_number"), mobileNumber);
        assertNotNull(response.jsonPath().getString("data.token"));
        assertNotNull(response.jsonPath().getString("data.user_id"));

        // Store token — all future API calls will use this automatically
        String token = response.jsonPath().getString("data.token");
        cardService.attachToken(token);
    }
}