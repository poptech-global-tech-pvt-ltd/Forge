package com.popclub.tests;

import com.popclub.api.util.ConfigManager;
import com.popclub.api.dto.*;
import com.popclub.api.impl.PopService;
import com.popclub.api.impl.YblService;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.*;

public class CardOnboardingTest {

    private PopService popService;
    private YblService yblService;

    @BeforeClass
    public void setup() {
        popService = new PopService();
        yblService = new YblService();

        VerifySSOResponseDto ssoResponse = popService.verifySSO(new VerifySSORequestDto(ConfigManager.getMobileNumber()))
                .then()
                .statusCode(200)
                .extract()
                .as(VerifySSOResponseDto.class);

        assertTrue(ssoResponse.isSuccess(), "SSO verification failed");
        assertNotNull(ssoResponse.getData().getToken(), "Token is null");
        assertNotNull(ssoResponse.getData().getUserId(), "User ID is null");
        assertNotNull(ssoResponse.getData().getMobileNumber(), "Mobile number is null");

        String token = ssoResponse.getData().getToken();
        popService.attachToken(token);
        yblService.attachToken(token);

        popService.getUserJourneyDetail()
                .then()
                .statusCode(200)
                .body("data.last_saved_level", equalTo("POP_SSO_VERIFICATION_DONE"));
    }

    // Step 1.1
    @Test(groups = "card-onboarding")
    public void step1_1_getPopConsents() {
        popService.getPopConsents()
                .then()
                .statusCode(200);
    }

    // Step 1.2
    @Test(groups = "card-onboarding", dependsOnMethods = "step1_1_getPopConsents")
    public void step1_2_postPopConsent() {
        PopConsentRequestDto request = new PopConsentRequestDto(
                ConfigManager.getMobileNumber(),
                Arrays.asList(
                        new PopConsentRequestDto.Consent("TandC",
                                "I accept the important terms & conditions to apply for the POPcard",
                                true, true, true),
                        new PopConsentRequestDto.Consent("call-sms-email",
                                "I authorise POP to call/SMS/e-mail/send Whatsapp messages to me",
                                false, true, true),
                        new PopConsentRequestDto.Consent("PAN",
                                "I authorise POP to verify my official details from NSDL using my PAN card.",
                                false, true, true)
                )
        );

        popService.postPopConsent(request)
                .then()
                .statusCode(200);
    }

    // Step 2.0
    @Test(groups = "card-onboarding", dependsOnMethods = "step1_2_postPopConsent")
    public void step2_0_updateUserDetailsPan() {
        popService.updateUserDetailsPan(new UserDetailsPanRequestDto(ConfigManager.getPan(), ConfigManager.getPinCode()))
                .then()
                .statusCode(200);

        popService.getUserJourneyDetail()
                .then()
                .statusCode(200)
                .body("data.last_saved_level", equalTo("POP_PAN_SUBMISSION_DONE"));
    }

    // Step 2.1
    @Test(groups = "card-onboarding", dependsOnMethods = "step2_0_updateUserDetailsPan")
    public void step2_1_verifyPincode() {
        popService.verifyPincode(ConfigManager.getPinCode())
                .then()
                .statusCode(200);
    }

    // Step 3.1
    @Test(groups = "card-onboarding", dependsOnMethods = "step2_1_verifyPincode")
    public void step3_1_getUserDetails() {
        popService.getUserDetails()
                .then()
                .statusCode(200);
    }

    // Step 3.0
    @Test(groups = "card-onboarding", dependsOnMethods = "step3_1_getUserDetails")
    public void step3_0_updateUserDetailsBasic() {
        UserDetailsBasicRequestDto request = new UserDetailsBasicRequestDto(
                ConfigManager.getFirstName(),
                ConfigManager.getMiddleName(),
                ConfigManager.getLastName(),
                ConfigManager.getEmail(),
                ConfigManager.getDob(),
                ConfigManager.getGender(),
                ConfigManager.getOccupation(),
                ConfigManager.getMaritalStatus()
        );

        popService.updateUserDetailsBasic(request)
                .then()
                .statusCode(200);

        popService.getUserJourneyDetail()
                .then()
                .statusCode(200)
                .body("data.last_saved_level", equalTo("POP_USER_DETAIL_SUBMISSION_DONE"));
    }

    // Step 4.1
    @Test(groups = "card-onboarding", dependsOnMethods = "step3_0_updateUserDetailsBasic")
    public void step4_1_getYblConsents() {
        yblService.getYblConsents()
                .then()
                .statusCode(200);
    }

    // Step 4.0
    @Test(groups = "card-onboarding", dependsOnMethods = "step4_1_getYblConsents")
    public void step4_0_saveYblConsents() {
        yblService.saveYblConsents(new YblConsentRequestDto())
                .then()
                .statusCode(200);

        popService.getUserJourneyDetail()
                .then()
                .statusCode(200)
                .body("data.last_saved_level", equalTo("YBL_CONSENT_SUBMISSION_DONE"));
    }

    // Step 5.0
    @Test(groups = "card-onboarding", dependsOnMethods = "step4_0_saveYblConsents")
    public void step5_0_saveAddressCurrent() {
        AddressRequestDto request = new AddressRequestDto(
                ConfigManager.getCurrentAddressType(),
                ConfigManager.getCurrentAddressLine1(),
                ConfigManager.getCurrentAddressLine2(),
                ConfigManager.getCurrentAddressLine3(),
                ConfigManager.getCurrentAddressLandmark(),
                ConfigManager.getCurrentAddressCity(),
                ConfigManager.getCurrentAddressState(),
                ConfigManager.getCurrentAddressCountry(),
                ConfigManager.getCurrentAddressPincode()
        );

        yblService.saveAddressCurrent(request)
                .then()
                .statusCode(200);

        popService.getUserJourneyDetail()
                .then()
                .statusCode(200)
                .body("data.last_saved_level", equalTo("YBL_RESIDENTIAL_ADDRESS_SUBMISSION_DONE"));
    }

    // Step 5.1
    @Test(groups = "card-onboarding", dependsOnMethods = "step5_0_saveAddressCurrent")
    public void step5_1_getAddresses() {
        yblService.getAddresses()
                .then()
                .statusCode(200);
    }

    // Step 6.0
    @Test(groups = "card-onboarding", dependsOnMethods = "step5_1_getAddresses")
    public void step6_0_savePersonalDetails() {
        PersonalDetailsRequestDto request = new PersonalDetailsRequestDto(
                ConfigManager.getUserLevel(),
                ConfigManager.getNameOnCard(),
                ConfigManager.getFatherName()
        );

        yblService.savePersonalDetails(request)
                .then()
                .statusCode(200);

        popService.getUserJourneyDetail()
                .then()
                .statusCode(200)
                .body("data.last_saved_level", equalTo("YBL_PERSONAL_DETAIL_SUBMISSION_DONE"));
    }

    // Step 6.1
    @Test(groups = "card-onboarding", dependsOnMethods = "step6_0_savePersonalDetails")
    public void step6_1_getPersonalDetails() {
        yblService.getPersonalDetails()
                .then()
                .statusCode(200);
    }

    // Step 7.0 GET
    @Test(groups = "card-onboarding", dependsOnMethods = "step6_1_getPersonalDetails")
    public void step7_0_getProfessionMasters() {
        yblService.getProfessionMasters()
                .then()
                .statusCode(200);
    }

    // Step 7.0 POST
    @Test(groups = "card-onboarding", dependsOnMethods = "step7_0_getProfessionMasters")
    public void step7_0_saveProfessionalDetails() {
        ProfessionalDetailsRequestDto request = new ProfessionalDetailsRequestDto(
                ConfigManager.getCompanyName(),
                ConfigManager.getDesignation(),
                ConfigManager.getAnnualIncome(),
                ConfigManager.getCompanyType(),
                ConfigManager.getProfession(),
                ConfigManager.getProfessionalOccupation()
        );

        yblService.saveProfessionalDetails(request)
                .then()
                .statusCode(200);

        popService.getUserJourneyDetail()
                .then()
                .statusCode(200)
                .body("data.last_saved_level", equalTo("YBL_PROFESSIONAL_DETAIL_SUBMISSION_DONE"));
    }

    // Step 8.0
    @Test(groups = "card-onboarding", dependsOnMethods = "step7_0_saveProfessionalDetails")
    public void step8_0_saveAddressOffice() {
        AddressRequestDto request = new AddressRequestDto(
                ConfigManager.getOfficeAddressType(),
                ConfigManager.getOfficeAddressLine1(),
                ConfigManager.getOfficeAddressLine2(),
                ConfigManager.getOfficeAddressLine3(),
                ConfigManager.getOfficeAddressLandmark(),
                ConfigManager.getOfficeAddressCity(),
                ConfigManager.getOfficeAddressState(),
                ConfigManager.getOfficeAddressCountry(),
                ConfigManager.getOfficeAddressPincode()
        );

        yblService.saveAddressOffice(request)
                .then()
                .statusCode(200);

        popService.getUserJourneyDetail()
                .then()
                .statusCode(200)
                .body("data.last_saved_level", equalTo("YBL_OFFICE_ADDRESS_SUBMISSION_DONE"));
    }

    // Step 9.0
    @Test(groups = "card-onboarding", dependsOnMethods = "step8_0_saveAddressOffice")
    public void step9_0_saveAddressDelivery() {
        AddressRequestDto request = new AddressRequestDto(
                ConfigManager.getDeliveryAddressType(), true
        );

        yblService.saveAddressDelivery(request)
                .then()
                .statusCode(200);

        popService.getUserJourneyDetail()
                .then()
                .statusCode(200)
                .body("data.last_saved_level", equalTo("YBL_DELIVERY_ADDRESS_SUBMISSION_DONE"));
    }

    @AfterClass
    public void teardown() {
        popService.reset();
        yblService.reset();
    }
}
