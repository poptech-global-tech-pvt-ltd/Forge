package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.dto.PersonalDetailsRequestDto;
import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import com.popclub.cardapi.util.ConfigManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L5_PersonalDetails_PositiveTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level5_PositiveTest (+ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
    }

    @Test(priority = 1)
    public void postPersonalDetails_success() {
        PersonalDetailsRequestDto request = new PersonalDetailsRequestDto(
                ConfigManager.getNameOnCard(),
                ConfigManager.getFatherName()
        );
        Response response = cardService.postPersonalDetails(request);
        assertTrue(response.jsonPath().getBoolean("is_success"), "Expected is_success=true");
    }

    @Test(priority = 2)
    public void postPersonalDetails_dataIsNull() {
        PersonalDetailsRequestDto request = new PersonalDetailsRequestDto(
                ConfigManager.getNameOnCard(),
                ConfigManager.getFatherName()
        );
        Response response = cardService.postPersonalDetails(request);
        assertNull(response.jsonPath().get("data"), "Expected data=null");
    }

    @Test(priority = 3)
    public void getPersonalDetails_success() {
        Response response = cardService.getPersonalDetails();
        assertTrue(response.jsonPath().getBoolean("is_success"), "Expected is_success=true");
    }

    @Test(priority = 4)
    public void getPersonalDetails_storedFieldsMatch() {
        Response response = cardService.getPersonalDetails();
        assertEquals(response.jsonPath().getString("data.card.name"),        ConfigManager.getNameOnCard());
        assertEquals(response.jsonPath().getString("data.card.father_name"), ConfigManager.getFatherName());
        assertNotNull(response.jsonPath().get("data.is_editable"),           "Expected is_editable to be present");
    }
}
