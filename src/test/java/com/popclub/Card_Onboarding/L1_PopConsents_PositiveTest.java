package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.dto.ConsentItemDto;
import com.popclub.cardapi.dto.SubmitConsentsRequestDto;
import com.popclub.cardapi.dto.UserDetailsRequestDto;
import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import com.popclub.cardapi.util.ConfigManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.*;

public class L1_PopConsents_PositiveTest {

    private CardService cardService;
    private String mobileNumber;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level1_PositiveTest (+ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
        mobileNumber = ConfigManager.getMobileNumber();
    }

    // ===================== Step 1.1: GET POP Consents =====================

    @Test(priority = 1)
    public void getPopConsents_success() {
        Response response = cardService.getPopConsents(mobileNumber);

        assertEquals(response.statusCode(), 200);
        assertTrue(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("message"), "success");
    }

    @Test(priority = 2)
    public void getPopConsents_hasConsents() {
        Response response = cardService.getPopConsents(mobileNumber);

        int count = response.jsonPath().getList("data.consents").size();
        assertTrue(count >= 4, "Expected at least 4 consents, got " + count);
    }

    @Test(priority = 3)
    public void getPopConsents_firstConsentIsTandC() {
        Response response = cardService.getPopConsents(mobileNumber);

        assertEquals(response.jsonPath().getString("data.consents[0].name"), "TandC");
        assertTrue(response.jsonPath().getBoolean("data.consents[0].is_mandatory"));
        assertTrue(response.jsonPath().getBoolean("data.consents[0].is_parent"));
    }

    // ===================== Step 1.2: POST (Submit) Consents =====================

    @Test(priority = 4)
    public void submitConsents_success() {
        List<ConsentItemDto> consents = Arrays.asList(
                new ConsentItemDto("TandC",
                        "I accept the important terms & conditions to apply for the POPcard.",
                        true, true, true),
                new ConsentItemDto("call-sms-email",
                        "I authorise POP to call/SMS/e-mail/send Whatsapp messages to me regarding my applications.",
                        false, true, true),
                new ConsentItemDto("PAN",
                        "I authorise POP to verify my official details from NSDL using my PAN card.",
                        false, true, true),
                new ConsentItemDto("",
                        "I hereby consent to POP or its partners being appointed as my authorized representative to receive my Credit Information from Experian for the purpose of checking the eligibility for the Yes Bank POP-Club Credit Card and also to pre-fill any relevant field(if applicable) from the information received from Experian.",
                        false, true, true)
        );

        SubmitConsentsRequestDto request = new SubmitConsentsRequestDto(mobileNumber, consents);
        Response response = cardService.submitConsents(request);

        assertEquals(response.statusCode(), 200);
        assertTrue(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("message"), "success");
    }

    // ===================== Step 2.0: POST User Details (PAN + Pincode) =====================

    @Test(priority = 5)
    public void postUserDetails_success() {
        String pan = ConfigManager.getPan();
        String pincode = ConfigManager.getPincode();

        UserDetailsRequestDto request = new UserDetailsRequestDto(pan, pincode);
        Response response = cardService.postUserDetails(request);

        assertEquals(response.statusCode(), 200);
        assertTrue(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("data.pan"), pan);
        assertEquals(response.jsonPath().getString("data.pin_code"), pincode);
    }

    // ===================== Step 2.1: GET Verify Pincode =====================

    @Test(priority = 6)
    public void verifyPincode_success() {
        String pincode = ConfigManager.getPincode();

        Response response = cardService.verifyPincode(pincode);

        assertEquals(response.statusCode(), 200);
        assertTrue(response.jsonPath().getBoolean("is_success"));
        assertEquals(response.jsonPath().getString("data.pin_code"), pincode);
        assertNotNull(response.jsonPath().getString("data.state"));
    }

    @Test(priority = 7)
    public void verifyPincode_hasCityData() {
        String pincode = ConfigManager.getPincode();

        Response response = cardService.verifyPincode(pincode);

        int cityCount = response.jsonPath().getList("data.pin_city_data").size();
        assertTrue(cityCount > 0, "Expected at least 1 city");
        assertTrue(response.jsonPath().getBoolean("data.pin_city_data[0].is_valid"));
    }
}
