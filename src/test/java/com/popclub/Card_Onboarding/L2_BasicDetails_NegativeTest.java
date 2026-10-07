package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L2_BasicDetails_NegativeTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level2_NegativeTest (-ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
    }

    private String msg(String testName, Response response) {
        return "[" + testName + "] Expected 4xx, got " + response.statusCode()
                + " | API message: " + response.jsonPath().getString("message");
    }

    // ===================== POST Basic Details — Negative =====================

    @Test
    public void postBasicDetails_emptyBody() {
        Response response = cardService.postBasicDetailsRaw("{}");
        assertTrue(response.statusCode() >= 400, msg("postBasicDetails_emptyBody", response));
    }

    @Test
    public void postBasicDetails_missingFirstName() {
        String body = "{\"last_name\": \"Sonavane\", \"email\": \"singh.gaurav@popclub.co\", " +
                "\"dob\": \"1998-09-15\", \"gender\": \"MALE\", \"occupation\": \"Salaried\", " +
                "\"marital_status\": \"SINGLE\", \"page_info\": \"basic_detail\"}";
        Response response = cardService.postBasicDetailsRaw(body);
        assertTrue(response.statusCode() >= 400, msg("postBasicDetails_missingFirstName", response));
    }

    @Test
    public void postBasicDetails_invalidEmail() {
        String body = "{\"first_name\": \"Harshala\", \"last_name\": \"Sonavane\", " +
                "\"email\": \"notanemail\", \"dob\": \"1998-09-15\", \"gender\": \"MALE\", " +
                "\"occupation\": \"Salaried\", \"marital_status\": \"SINGLE\", \"page_info\": \"basic_detail\"}";
        Response response = cardService.postBasicDetailsRaw(body);
        assertTrue(response.statusCode() >= 400, msg("postBasicDetails_invalidEmail", response));
    }

    @Test
    public void postBasicDetails_invalidDobFormat() {
        String body = "{\"first_name\": \"Harshala\", \"last_name\": \"Sonavane\", " +
                "\"email\": \"singh.gaurav@popclub.co\", \"dob\": \"15-09-1998\", \"gender\": \"MALE\", " +
                "\"occupation\": \"Salaried\", \"marital_status\": \"SINGLE\", \"page_info\": \"basic_detail\"}";
        Response response = cardService.postBasicDetailsRaw(body);
        assertTrue(response.statusCode() >= 400, msg("postBasicDetails_invalidDobFormat", response));
    }

    @Test
    public void postBasicDetails_missingPageInfo() {
        String body = "{\"first_name\": \"Harshala\", \"last_name\": \"Sonavane\", " +
                "\"email\": \"singh.gaurav@popclub.co\", \"dob\": \"1998-09-15\", \"gender\": \"MALE\", " +
                "\"occupation\": \"Salaried\", \"marital_status\": \"SINGLE\"}";
        Response response = cardService.postBasicDetailsRaw(body);
        assertTrue(response.statusCode() >= 400, msg("postBasicDetails_missingPageInfo", response));
    }
}
