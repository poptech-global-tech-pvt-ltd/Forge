package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L1_PopConsents_NegativeTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level1_NegativeTest (-ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
    }

    private String msg(String testName, Response response) {
        return "[" + testName + "] Expected 4xx, got " + response.statusCode()
                + " | API message: " + response.jsonPath().getString("message");
    }

    // ===================== Step 1.1: GET POP Consents — Negative =====================

    @Test
    public void getPopConsents_emptyMobile() {
        Response response = cardService.getPopConsents("");
        assertTrue(response.statusCode() >= 400, msg("getPopConsents_emptyMobile", response));
    }

    @Test
    public void getPopConsents_invalidMobile() {
        Response response = cardService.getPopConsents("abcdef");
        assertTrue(response.statusCode() >= 400, msg("getPopConsents_invalidMobile", response));
    }

    @Test
    public void getPopConsents_specialChars() {
        Response response = cardService.getPopConsents("!@#$%^");
        assertTrue(response.statusCode() >= 400, msg("getPopConsents_specialChars", response));
    }

    // ===================== Step 1.2: POST Consents — Negative =====================

    @Test
    public void submitConsents_emptyBody() {
        Response response = cardService.submitConsentsRaw("{}");
        assertTrue(response.statusCode() >= 400, msg("submitConsents_emptyBody", response));
    }

    @Test
    public void submitConsents_noConsentsArray() {
        String body = "{\"mobile_number\": \"1234567271\"}";
        Response response = cardService.submitConsentsRaw(body);
        assertTrue(response.statusCode() >= 400, msg("submitConsents_noConsentsArray", response));
    }

    // ===================== Step 2.0: POST User Details — Negative =====================

    @Test
    public void postUserDetails_emptyPan() {
        Response response = cardService.postUserDetailsRaw("{\"pan\": \"\", \"pin_code\": \"560101\", \"page_info\": \"pan_detail\"}");
        assertTrue(response.statusCode() >= 400, msg("postUserDetails_emptyPan", response));
    }

    @Test
    public void postUserDetails_invalidPan() {
        Response response = cardService.postUserDetailsRaw("{\"pan\": \"AAAAA\", \"pin_code\": \"560101\", \"page_info\": \"pan_detail\"}");
        assertTrue(response.statusCode() >= 400, msg("postUserDetails_invalidPan", response));
    }

    @Test
    public void postUserDetails_emptyPincode() {
        Response response = cardService.postUserDetailsRaw("{\"pan\": \"IKUPS9509G\", \"pin_code\": \"\", \"page_info\": \"pan_detail\"}");
        assertTrue(response.statusCode() >= 400, msg("postUserDetails_emptyPincode", response));
    }

    // ===================== Step 2.1: GET Verify Pincode — Negative =====================

    @Test
    public void verifyPincode_emptyPincode() {
        Response response = cardService.verifyPincode("");
        assertTrue(response.statusCode() >= 400, msg("verifyPincode_emptyPincode", response));
    }

    @Test
    public void verifyPincode_invalidPincode() {
        Response response = cardService.verifyPincode("000000");
        assertTrue(response.statusCode() >= 400, msg("verifyPincode_invalidPincode", response));
    }

    @Test
    public void verifyPincode_alphabeticPincode() {
        Response response = cardService.verifyPincode("abcdef");
        assertTrue(response.statusCode() >= 400, msg("verifyPincode_alphabeticPincode", response));
    }
}
