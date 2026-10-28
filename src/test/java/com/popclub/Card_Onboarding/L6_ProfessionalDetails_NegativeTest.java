package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L6_ProfessionalDetails_NegativeTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level6_NegativeTest (-ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
    }

    @Test(priority = 1)
    public void postProfessionalDetails_missingCompanyName() {
        Response response = cardService.postProfessionalDetailsRaw(
                "{\"designation\":\"Agriculturist\",\"annual_income\":\"9887856\",\"company_type\":\"Public Ltd Company\",\"profession\":\"DOCTOR\",\"occupation\":\"Salaried\"}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
    }

    @Test(priority = 2)
    public void postProfessionalDetails_missingDesignation() {
        Response response = cardService.postProfessionalDetailsRaw(
                "{\"company_name\":\"123STORES E COMMERCE PVT LTD\",\"annual_income\":\"9887856\",\"company_type\":\"Public Ltd Company\",\"profession\":\"DOCTOR\",\"occupation\":\"Salaried\"}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
    }

    @Test(priority = 3)
    public void postProfessionalDetails_missingAnnualIncome() {
        Response response = cardService.postProfessionalDetailsRaw(
                "{\"company_name\":\"123STORES E COMMERCE PVT LTD\",\"designation\":\"Agriculturist\",\"company_type\":\"Public Ltd Company\",\"profession\":\"DOCTOR\",\"occupation\":\"Salaried\"}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
    }

    @Test(priority = 4)
    public void postProfessionalDetails_missingCompanyType() {
        Response response = cardService.postProfessionalDetailsRaw(
                "{\"company_name\":\"123STORES E COMMERCE PVT LTD\",\"designation\":\"Agriculturist\",\"annual_income\":\"9887856\",\"profession\":\"DOCTOR\",\"occupation\":\"Salaried\"}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
    }

    @Test(priority = 5)
    public void postProfessionalDetails_missingProfession() {
        Response response = cardService.postProfessionalDetailsRaw(
                "{\"company_name\":\"123STORES E COMMERCE PVT LTD\",\"designation\":\"Agriculturist\",\"annual_income\":\"9887856\",\"company_type\":\"Public Ltd Company\",\"occupation\":\"Salaried\"}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
    }

    @Test(priority = 6)
    public void postProfessionalDetails_missingOccupation() {
        Response response = cardService.postProfessionalDetailsRaw(
                "{\"company_name\":\"123STORES E COMMERCE PVT LTD\",\"designation\":\"Agriculturist\",\"annual_income\":\"9887856\",\"company_type\":\"Public Ltd Company\",\"profession\":\"DOCTOR\"}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
        assertEquals(response.jsonPath().getString("error.message"), "Required field occupation missing");
    }

    @Test(priority = 7)
    public void postProfessionalDetails_emptyBody() {
        Response response = cardService.postProfessionalDetailsRaw("{}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("error.code"), "olppc10002");
    }

    @Test(priority = 8)
    public void postProfessionalDetails_invalidAnnualIncome() {
        Response response = cardService.postProfessionalDetailsRaw(
                "{\"company_name\":\"123STORES E COMMERCE PVT LTD\",\"designation\":\"Agriculturist\",\"annual_income\":\"-50000\",\"company_type\":\"Public Ltd Company\",\"profession\":\"DOCTOR\",\"occupation\":\"Salaried\"}");
        assertEquals(response.getStatusCode(), 400);
        assertFalse(response.jsonPath().getBoolean("is_success"));
    }
}
