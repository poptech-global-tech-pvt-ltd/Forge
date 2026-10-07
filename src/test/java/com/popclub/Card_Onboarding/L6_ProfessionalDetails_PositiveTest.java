package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.dto.ProfessionalDetailsRequestDto;
import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import com.popclub.cardapi.util.ConfigManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L6_ProfessionalDetails_PositiveTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level6_PositiveTest (+ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
    }

    @Test(priority = 1)
    public void getMasterLists_success() {
        Response response = cardService.getMasterLists();
        assertTrue(response.jsonPath().getBoolean("is_success"), "Expected is_success=true");
    }

    @Test(priority = 2)
    public void getMasterLists_hasRequiredKeys() {
        Response response = cardService.getMasterLists();
        assertNotNull(response.jsonPath().get("data.companies"),    "Expected data.companies");
        assertNotNull(response.jsonPath().get("data.designations"), "Expected data.designations");
        assertNotNull(response.jsonPath().get("data.professions"),  "Expected data.professions");
        assertNotNull(response.jsonPath().get("data.company_types"), "Expected data.company_types");
    }

    @Test(priority = 3)
    public void postProfessionalDetails_success() {
        ProfessionalDetailsRequestDto request = new ProfessionalDetailsRequestDto(
                ConfigManager.getCompanyName(),
                ConfigManager.getDesignation(),
                ConfigManager.getAnnualIncome(),
                ConfigManager.getCompanyType(),
                ConfigManager.getProfession(),
                ConfigManager.getProfessionalOccupation()
        );
        Response response = cardService.postProfessionalDetails(request);
        assertTrue(response.jsonPath().getBoolean("is_success"), "Expected is_success=true");
    }

    @Test(priority = 4)
    public void postProfessionalDetails_dataIsNull() {
        ProfessionalDetailsRequestDto request = new ProfessionalDetailsRequestDto(
                ConfigManager.getCompanyName(),
                ConfigManager.getDesignation(),
                ConfigManager.getAnnualIncome(),
                ConfigManager.getCompanyType(),
                ConfigManager.getProfession(),
                ConfigManager.getProfessionalOccupation()
        );
        Response response = cardService.postProfessionalDetails(request);
        assertNull(response.jsonPath().get("data"), "Expected data=null");
    }

    @Test(priority = 5)
    public void getProfessionalDetails_success() {
        Response response = cardService.getProfessionalDetails();
        assertTrue(response.jsonPath().getBoolean("is_success"), "Expected is_success=true");
    }

    @Test(priority = 6)
    public void getProfessionalDetails_storedFieldsMatch() {
        Response response = cardService.getProfessionalDetails();
        assertEquals(response.jsonPath().getString("data.professional.company_name"), ConfigManager.getCompanyName());
        assertEquals(response.jsonPath().getString("data.professional.designation"),  ConfigManager.getDesignation());
        assertEquals(response.jsonPath().getString("data.professional.company_type"), ConfigManager.getCompanyType());
        assertEquals(response.jsonPath().getString("data.professional.profession"),   ConfigManager.getProfession());
    }
}
