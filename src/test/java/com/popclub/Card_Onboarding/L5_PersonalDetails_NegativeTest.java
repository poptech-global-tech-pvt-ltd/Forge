package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L5_PersonalDetails_NegativeTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level5_NegativeTest (-ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
    }

    @Test(priority = 1)
    public void postPersonalDetails_missingNameOnCard() {
        Response response = cardService.postPersonalDetailsRaw("{\"father_name\":\"Pop Singh\"}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
        assertEquals(response.jsonPath().getString("error.message"), "Required field name_on_card missing");
    }

    @Test(priority = 2)
    public void postPersonalDetails_missingFatherName() {
        Response response = cardService.postPersonalDetailsRaw("{\"name_on_card\":\"Harshala Sonavane\"}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
        assertEquals(response.jsonPath().getString("error.message"), "Required field father_name missing");
    }

    @Test(priority = 3)
    public void postPersonalDetails_emptyNameOnCard() {
        Response response = cardService.postPersonalDetailsRaw("{\"name_on_card\":\"\",\"father_name\":\"Pop Singh\"}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
    }

    @Test(priority = 4)
    public void postPersonalDetails_emptyFatherName() {
        Response response = cardService.postPersonalDetailsRaw("{\"name_on_card\":\"Harshala Sonavane\",\"father_name\":\"\"}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
    }

    @Test(priority = 5)
    public void postPersonalDetails_emptyBody() {
        Response response = cardService.postPersonalDetailsRaw("{}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
    }
}
