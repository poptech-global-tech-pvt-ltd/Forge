package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.dto.YblConsentsRequestDto;
import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L3_YblConsents_PositiveTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level3_PositiveTest (+ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
    }

    private YblConsentsRequestDto defaultRequest() {
        return new YblConsentsRequestDto(
                false, false, "", "",
                true, true, true, true, true, true, true, true, true
        );
    }

    // ===================== POST YBL Consents =====================

    @Test(priority = 1)
    public void postYblConsents_success() {
        Response response = cardService.postYblConsents(defaultRequest());

        assertEquals(response.statusCode(), 200);
        assertTrue(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("message"), "success");
    }

    @Test(priority = 2)
    public void postYblConsents_dataNullOnSuccess() {
        Response response = cardService.postYblConsents(defaultRequest());

        assertEquals(response.statusCode(), 200);
        assertNull(response.jsonPath().get("data"));
    }

}
