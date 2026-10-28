package com.popclub.cardapi.util;

import com.popclub.cardapi.dto.OtpSendRequestDto;
import com.popclub.cardapi.dto.OtpVerifyRequestDto;
import com.popclub.cardapi.impl.CardService;
import io.restassured.response.Response;

public class AuthManager {

    private static String token;

    public static String getToken() {
        if (token == null) {
            login();
        }
        return token;
    }

    private static void login() {
        CardService service = new CardService();
        String mobile = ConfigManager.getMobileNumber();
        String otp = ConfigManager.getOtp();

        service.sendOtp(new OtpSendRequestDto(mobile));

        Response response = service.verifyOtp(new OtpVerifyRequestDto(mobile, otp));
        token = response.jsonPath().getString("data.token");
    }
}
