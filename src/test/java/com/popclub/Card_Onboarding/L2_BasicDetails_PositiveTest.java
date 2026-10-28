package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.dto.BasicDetailsRequestDto;
import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import com.popclub.cardapi.util.ConfigManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L2_BasicDetails_PositiveTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level2_PositiveTest (+ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
    }

    // ===================== POST Basic Details =====================

    @Test(priority = 1)
    public void postBasicDetails_success() {
        BasicDetailsRequestDto request = new BasicDetailsRequestDto(
                ConfigManager.getFirstName(),
                ConfigManager.getLastName(),
                ConfigManager.getEmail(),
                ConfigManager.getDob(),
                ConfigManager.getGender(),
                ConfigManager.getOccupation(),
                ConfigManager.getMaritalStatus()
        );

        Response response = cardService.postBasicDetails(request);

        assertEquals(response.statusCode(), 200);
        assertTrue(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("message"), "success");
    }

    @Test(priority = 2)
    public void postBasicDetails_responseHasUserDetails() {
        BasicDetailsRequestDto request = new BasicDetailsRequestDto(
                ConfigManager.getFirstName(),
                ConfigManager.getLastName(),
                ConfigManager.getEmail(),
                ConfigManager.getDob(),
                ConfigManager.getGender(),
                ConfigManager.getOccupation(),
                ConfigManager.getMaritalStatus()
        );

        Response response = cardService.postBasicDetails(request);

        assertEquals(response.jsonPath().getString("data.first_name"), ConfigManager.getFirstName());
        assertEquals(response.jsonPath().getString("data.last_name"), ConfigManager.getLastName());
        assertEquals(response.jsonPath().getString("data.email"), ConfigManager.getEmail());
        assertEquals(response.jsonPath().getString("data.dob"), ConfigManager.getDob());
        assertEquals(response.jsonPath().getString("data.gender"), ConfigManager.getGender());
        assertEquals(response.jsonPath().getString("data.occupation"), ConfigManager.getOccupation());
        assertEquals(response.jsonPath().getString("data.marital_status"), ConfigManager.getMaritalStatus());
    }

    @Test(priority = 3)
    public void postBasicDetails_breDecisionPresent() {
        BasicDetailsRequestDto request = new BasicDetailsRequestDto(
                ConfigManager.getFirstName(),
                ConfigManager.getLastName(),
                ConfigManager.getEmail(),
                ConfigManager.getDob(),
                ConfigManager.getGender(),
                ConfigManager.getOccupation(),
                ConfigManager.getMaritalStatus()
        );

        Response response = cardService.postBasicDetails(request);

        assertNotNull(response.jsonPath().getString("data.bre_datas.bre_decision"));
        assertNotNull(response.jsonPath().getString("data.bre_datas.report_id"));
    }
}
