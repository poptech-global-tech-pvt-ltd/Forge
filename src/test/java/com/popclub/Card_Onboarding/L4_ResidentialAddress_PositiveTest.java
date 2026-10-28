package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.dto.YblAddressRequestDto;
import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import com.popclub.cardapi.util.ConfigManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L4_ResidentialAddress_PositiveTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level4_PositiveTest (+ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
    }

    private YblAddressRequestDto defaultRequest() {
        return new YblAddressRequestDto(
                "residential",
                ConfigManager.getAddressLine1(),
                ConfigManager.getAddressLine2(),
                ConfigManager.getAddressLine3(),
                "",
                ConfigManager.getAddressCity(),
                ConfigManager.getAddressState(),
                ConfigManager.getAddressCountry(),
                ConfigManager.getAddressPincode()
        );
    }

    // ===================== POST YBL Address =====================

    @Test(priority = 1)
    public void postYblAddress_success() {
        Response response = cardService.postYblAddress(defaultRequest());

        assertEquals(response.statusCode(), 200);
        assertTrue(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("message"), "success");
    }

    @Test(priority = 2)
    public void postYblAddress_responseHasAddressData() {
        Response response = cardService.postYblAddress(defaultRequest());

        assertEquals(response.statusCode(), 200);
        assertEquals(response.jsonPath().getString("data.address.address_line_1"), ConfigManager.getAddressLine1());
        assertEquals(response.jsonPath().getString("data.address.address_line_2"), ConfigManager.getAddressLine2());
        assertEquals(response.jsonPath().getString("data.address.state"), ConfigManager.getAddressState());
    }

    // ===================== GET YBL Addresses (verify stored) =====================

    @Test(priority = 3)
    public void getYblAddresses_success() {
        Response response = cardService.getYblAddresses();

        assertEquals(response.statusCode(), 200);
        assertTrue(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("message"), "success");
    }

    @Test(priority = 4)
    public void getYblAddresses_currentAddressStored() {
        Response response = cardService.getYblAddresses();
        String base = "data.addresses.CURRENT.current_addresses[0]";

        assertEquals(response.statusCode(), 200);
        assertNotNull(response.jsonPath().get("data.addresses.CURRENT"),
                "CURRENT address block should exist");
        assertEquals(response.jsonPath().getString(base + ".address_line_1"), ConfigManager.getAddressLine1());
        assertEquals(response.jsonPath().getString(base + ".address_line_2"), ConfigManager.getAddressLine2());
        assertEquals(response.jsonPath().getString(base + ".address_line_3"), ConfigManager.getAddressLine3());
        assertEquals(response.jsonPath().getString(base + ".state"),          ConfigManager.getAddressState());
        assertEquals(response.jsonPath().getString(base + ".postal_code"),    ConfigManager.getAddressPincode());
        assertEquals(response.jsonPath().getString(base + ".address_type"),   "CURRENT");
    }
}
