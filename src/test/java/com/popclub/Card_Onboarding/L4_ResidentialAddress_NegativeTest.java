package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L4_ResidentialAddress_NegativeTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level4_NegativeTest (-ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
    }

    private String msg(String testName, Response response) {
        return "[" + testName + "] Expected is_success=false, got status=" + response.statusCode()
                + " | API message: " + response.jsonPath().getString("message");
    }

    // ===================== POST YBL Address — Negative =====================

    @Test
    public void postYblAddress_emptyBody() {
        Response response = cardService.postYblAddressRaw("{}");
        assertFalse(response.jsonPath().getBoolean("is_success"),
                msg("postYblAddress_emptyBody", response));
    }

    @Test
    public void postYblAddress_missingAddressLine1() {
        String body = "{\"address_type\": \"residential\", \"address_line_2\": \"Indiranagar\", " +
                "\"address_line_3\": \"\", \"landmark\": \"\", \"city\": \"Bangalore\", " +
                "\"state\": \"Karnataka\", \"country\": \"India\", \"pin_code\": \"400028\"}";
        Response response = cardService.postYblAddressRaw(body);
        assertFalse(response.jsonPath().getBoolean("is_success"),
                msg("postYblAddress_missingAddressLine1", response));
    }

    @Test
    public void postYblAddress_missingAddressType() {
        String body = "{\"address_line_1\": \"12 MG Road\", \"address_line_2\": \"Indiranagar\", " +
                "\"address_line_3\": \"\", \"landmark\": \"\", \"city\": \"Bangalore\", " +
                "\"state\": \"Karnataka\", \"country\": \"India\", \"pin_code\": \"400028\"}";
        Response response = cardService.postYblAddressRaw(body);
        assertFalse(response.jsonPath().getBoolean("is_success"),
                msg("postYblAddress_missingAddressType", response));
    }

    @Test
    public void postYblAddress_invalidPincode() {
        String body = "{\"address_type\": \"residential\", \"address_line_1\": \"12 MG Road\", " +
                "\"address_line_2\": \"Indiranagar\", \"address_line_3\": \"\", \"landmark\": \"\", " +
                "\"city\": \"Bangalore\", \"state\": \"Karnataka\", \"country\": \"India\", " +
                "\"pin_code\": \"ABCDEF\"}";
        Response response = cardService.postYblAddressRaw(body);
        assertFalse(response.jsonPath().getBoolean("is_success"),
                msg("postYblAddress_invalidPincode", response));
    }
}
