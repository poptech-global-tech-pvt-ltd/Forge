package com.popclub.cardApiTests.Card_Onboarding;

import com.popclub.cardapi.impl.CardService;
import com.popclub.cardapi.util.AuthManager;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class L3_YblConsents_NegativeTest {

    private CardService cardService;

    @BeforeClass
    public void setup() {
        System.out.println(">>> Running: Level3_NegativeTest (-ve)");
        cardService = new CardService();
        cardService.attachToken(AuthManager.getToken());
    }

    private String msg(String testName, Response response) {
        return "[" + testName + "] Expected is_success=false, got status=" + response.statusCode()
                + " | API message: " + response.jsonPath().getString("message");
    }

    // All 9 consent fields must be true — verified by running each field=false individually.
    // Fields returning "Required field is missing": term_condition_consent, cibil_consent,
    //   kfs_consent, yesbank_gogreen_consent, digit_app_consent
    // Fields returning "Bank API error" (fails at YesBank level): yesbank_authorize_consent,
    //   promo_consent, user_comm_consent, yesbank_cross_selling

    // ===================== POST YBL Consents — Negative =====================

    @Test
    public void postYblConsents_emptyBody() {
        Response response = cardService.postYblConsentsRaw("{}");
        assertFalse(response.jsonPath().getBoolean("is_success"),
                msg("postYblConsents_emptyBody", response));
    }

    @Test
    public void postYblConsents_termConditionConsentFalse() {
        String body = "{\"is_politically_exposed\": false, \"officer_relation_consent\": false, " +
                "\"bank_officer_name\": \"\", \"relationships_with_officer\": \"\", " +
                "\"term_condition_consent\": false, \"yesbank_authorize_consent\": true, " +
                "\"promo_consent\": true, \"cibil_consent\": true, \"user_comm_consent\": true, " +
                "\"kfs_consent\": true, \"yesbank_gogreen_consent\": true, " +
                "\"yesbank_cross_selling\": true, \"digit_app_consent\": true}";
        Response response = cardService.postYblConsentsRaw(body);
        assertFalse(response.jsonPath().getBoolean("is_success"),
                msg("postYblConsents_termConditionConsentFalse", response));
    }

    @Test
    public void postYblConsents_cibilConsentFalse() {
        String body = "{\"is_politically_exposed\": false, \"officer_relation_consent\": false, " +
                "\"bank_officer_name\": \"\", \"relationships_with_officer\": \"\", " +
                "\"term_condition_consent\": true, \"yesbank_authorize_consent\": true, " +
                "\"promo_consent\": true, \"cibil_consent\": false, \"user_comm_consent\": true, " +
                "\"kfs_consent\": true, \"yesbank_gogreen_consent\": true, " +
                "\"yesbank_cross_selling\": true, \"digit_app_consent\": true}";
        Response response = cardService.postYblConsentsRaw(body);
        assertFalse(response.jsonPath().getBoolean("is_success"),
                msg("postYblConsents_cibilConsentFalse", response));
    }

    @Test
    public void postYblConsents_kfsConsentFalse() {
        String body = "{\"is_politically_exposed\": false, \"officer_relation_consent\": false, " +
                "\"bank_officer_name\": \"\", \"relationships_with_officer\": \"\", " +
                "\"term_condition_consent\": true, \"yesbank_authorize_consent\": true, " +
                "\"promo_consent\": true, \"cibil_consent\": true, \"user_comm_consent\": true, " +
                "\"kfs_consent\": false, \"yesbank_gogreen_consent\": true, " +
                "\"yesbank_cross_selling\": true, \"digit_app_consent\": true}";
        Response response = cardService.postYblConsentsRaw(body);
        assertFalse(response.jsonPath().getBoolean("is_success"),
                msg("postYblConsents_kfsConsentFalse", response));
    }

    @Test
    public void postYblConsents_digitAppConsentFalse() {
        String body = "{\"is_politically_exposed\": false, \"officer_relation_consent\": false, " +
                "\"bank_officer_name\": \"\", \"relationships_with_officer\": \"\", " +
                "\"term_condition_consent\": true, \"yesbank_authorize_consent\": true, " +
                "\"promo_consent\": true, \"cibil_consent\": true, \"user_comm_consent\": true, " +
                "\"kfs_consent\": true, \"yesbank_gogreen_consent\": true, " +
                "\"yesbank_cross_selling\": true, \"digit_app_consent\": false}";
        Response response = cardService.postYblConsentsRaw(body);
        assertFalse(response.jsonPath().getBoolean("is_success"),
                msg("postYblConsents_digitAppConsentFalse", response));
    }
}
