package com.popclub.apiTests.card;

import com.popclub.api.util.ConfigManager;
import com.popclub.api.util.SchemaValidator;
import com.popclub.testdata.TestUser;
import com.popclub.api.dto.*;
import com.popclub.api.enums.Routes;
import com.popclub.api.impl.BaseService;
import com.popclub.api.impl.PopService;
import com.popclub.api.impl.YblService;
import com.popclub.testsigma.TestSigmaClient;
import com.popclub.testsigma.TestSigmaId;

import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


import static org.hamcrest.Matchers.equalTo;
import static org.testng.Assert.*;

public class CardOnboardingTest {

    private static final Logger   log  = LoggerFactory.getLogger(CardOnboardingTest.class);
    private static final TestUser USER = TestUser.defaultFixed();

    private PopService popService;
    private YblService yblService;
    private  TestSigmaClient testSigmaClient;

    @BeforeClass
    public void setup() {
        popService = new PopService();
        yblService = new YblService();

        Response ssoRaw = popService.verifySSO(new VerifySSORequestDto(ConfigManager.getMobileNumber()));
        BaseService.assertStatus(ssoRaw, 200, "POST", Routes.SSO_VERIFY.getPath());
        SchemaValidator.validate(ssoRaw, "sso_verify.json");

        VerifySSOResponseDto ssoResponse = ssoRaw.as(VerifySSOResponseDto.class);

        assertTrue(ssoResponse.isSuccess(), "SSO verification failed");
        assertNotNull(ssoResponse.getData().getToken(), "Token is null");
        assertNotNull(ssoResponse.getData().getUserId(), "User ID is null");
        assertNotNull(ssoResponse.getData().getMobileNumber(), "Mobile number is null");

        String token = ssoResponse.getData().getToken();
        popService.attachToken(token);
        yblService.attachToken(token);

        // testSigmaClient = new TestSigmaClient();


        Response journeyRaw = popService.getUserJourneyDetail();
        BaseService.assertStatus(journeyRaw, 200, "GET", Routes.POP_USER_JOURNEY.getPath());
        SchemaValidator.validate(journeyRaw, "user_journey.json");
        journeyRaw.then().body("data.last_saved_level", equalTo("POP_SSO_VERIFICATION_DONE"));
    }

    // Step 1.1
    @Test(description = "Fetch available consents for POP card application",
          groups = "card-onboarding")
    @TestSigmaId(
        value = "PO-12740",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Authenticate via POST /sso/verify\n2. Send GET /pop/consents with mobile_number\n3. Verify status 200\n4. Validate response against pop_consents.json schema",
        expectedResults = "API returns 200 with valid response body and schema"
    )
    public void step1_1_getPopConsents() {
        log.info("Running test: {}", "step1_1_getPopConsents");
        Response response = popService.getPopConsents();
        BaseService.assertStatus(response, 200, "GET", Routes.POP_CONSENTS.getPath());
        SchemaValidator.validate(response, "pop_consents.json");
    }

    // Step 1.2
    @Test(description = "Submit user acceptance of T&C, communication, and PAN consents",
          groups = "card-onboarding", dependsOnMethods = "step1_1_getPopConsents")
    @TestSigmaId(
        value = "PO-12741",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Build consent request with T&C, call-sms-email, PAN consents\n2. Send POST /pop/consents\n3. Verify status 200\n4. Validate response against generic_success.json schema",
        expectedResults = "API returns 200 with valid response body and schema"
    )
    public void step1_2_postPopConsent() {
        log.info("Running test: {}", "step1_2_postPopConsent");
        Response response = popService.postPopConsent(USER.toPopConsentDto());
        BaseService.assertStatus(response, 200, "POST", Routes.POP_CONSENTS.getPath());
        SchemaValidator.validate(response, "generic_success.json");
    }

    // Step 2.0
    @Test(description = "Submit PAN and pincode for NSDL verification",
          groups = "card-onboarding", dependsOnMethods = "step1_2_postPopConsent")
    @TestSigmaId(
        value = "PO-12742",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Send POST /pop/user-details with valid PAN + pincode\n2. Verify status 200\n3. Verify journey level = POP_PAN_SUBMISSION_DONE",
        expectedResults = "API returns 200 with valid response body and schema\nJourney level updates to POP_PAN_SUBMISSION_DONE"
    )
    public void step2_0_updateUserDetailsPan() {
        log.info("Running test: {}", "step2_0_updateUserDetailsPan");
        Response response = popService.updateUserDetailsPan(USER.toPanDto());
        BaseService.assertStatus(response, 200, "POST", Routes.POP_USER_DETAILS.getPath());

        assertJourneyLevel("POP_PAN_SUBMISSION_DONE");
    }

    // Step 2.1
    @Test(description = "Verify pincode is serviceable for card delivery",
          groups = "card-onboarding", dependsOnMethods = "step2_0_updateUserDetailsPan")
    @TestSigmaId(
        value = "PO-12743",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Send GET /pop/verify_pincode with valid pincode\n2. Verify status 200",
        expectedResults = "API returns 200 with valid response body and schema"
    )
    public void step2_1_verifyPincode() {
        log.info("Running test: {}", "step2_1_verifyPincode");
        Response response = popService.verifyPincode(USER.getPinCode());
        BaseService.assertStatus(response, 200, "GET", Routes.POP_VERIFY_PINCODE.getPath());
    }

    // Step 3.1
    @Test(description = "Retrieve user details auto-filled from PAN verification",
          groups = "card-onboarding", dependsOnMethods = "step2_1_verifyPincode")
    @TestSigmaId(
        value = "PO-12744",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Send GET /pop/user-details\n2. Verify status 200\n3. Verify response contains name, DOB from PAN verification",
        expectedResults = "API returns 200 with valid response body and schema"
    )
    public void step3_1_getUserDetails() {
        log.info("Running test: {}", "step3_1_getUserDetails");
        Response response = popService.getUserDetails();
        BaseService.assertStatus(response, 200, "GET", Routes.POP_USER_DETAILS.getPath());
    }

    // Step 3.0
    @Test(description = "Submit user basic details (name, email, DOB, gender, occupation)",
          groups = "card-onboarding", dependsOnMethods = "step3_1_getUserDetails")

    @TestSigmaId(
        value = "PO-12745",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Build basic details request with all required fields\n2. Send POST /pop/user-details\n3. Verify status 200\n4. Verify journey level = POP_USER_DETAIL_SUBMISSION_DONE",
        expectedResults = "API returns 200 with valid response body and schema\nJourney level updates to POP_USER_DETAIL_SUBMISSION_DONE"
    )
    public void step3_0_updateUserDetailsBasic() {
        log.info("Running test: {}", "step3_0_updateUserDetailsBasic");
        Response response = popService.updateUserDetailsBasic(USER.toBasicDetailsDto());
        BaseService.assertStatus(response, 200, "POST", Routes.POP_USER_DETAILS.getPath());
        assertJourneyLevel("POP_USER_DETAIL_SUBMISSION_DONE");
    }

    // Step 4.1
    @Test(description = "Fetch YBL bank consent requirements",
          groups = "card-onboarding", dependsOnMethods = "step3_0_updateUserDetailsBasic")
    @TestSigmaId(
        value = "PO-12746",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Send GET /ybl/consents\n2. Verify status 200\n3. Validate response schema",
        expectedResults = "API returns 200 with valid response body and schema"
    )
    public void step4_1_getYblConsents() {
        log.info("Running test: {}", "step4_1_getYblConsents");
        Response response = yblService.getYblConsents();
        BaseService.assertStatus(response, 200, "GET", Routes.YBL_CONSENTS.getPath());
        SchemaValidator.validate(response, "generic_success.json");
    }

    // Step 4.0
    @Test(description = "Submit all YBL bank consents including T&C and CIBIL",
          groups = "card-onboarding", dependsOnMethods = "step4_1_getYblConsents")
    @TestSigmaId(
        value = "PO-12747",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Build YBL consent request with all consents = true\n2. Send POST /ybl/consents\n3. Verify status 200\n4. Verify journey level = YBL_CONSENT_SUBMISSION_DONE",
        expectedResults = "API returns 200 with valid response body and schema\nJourney level updates to YBL_CONSENT_SUBMISSION_DONE"
    )
    public void step4_0_saveYblConsents() {
        log.info("Running test: {}", "step4_0_saveYblConsents");
        Response response = yblService.saveYblConsents(new YblConsentRequestDto());
        BaseService.assertStatus(response, 200, "POST", Routes.YBL_CONSENTS.getPath());

        assertJourneyLevel("YBL_CONSENT_SUBMISSION_DONE");
    }

    // Step 5.0
    @Test(description = "Save user current/residential address",
          groups = "card-onboarding", dependsOnMethods = "step4_0_saveYblConsents")
    @TestSigmaId(
        value = "PO-12748",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Build address request with type, line1-3, city, state, country, pincode\n2. Send POST /ybl/address\n3. Verify status 200\n4. Verify journey level = YBL_RESIDENTIAL_ADDRESS_SUBMISSION_DONE",
        expectedResults = "API returns 200 with valid response body and schema\nJourney level updates to YBL_RESIDENTIAL_ADDRESS_SUBMISSION_DONE"
    )
    public void step5_0_saveAddressCurrent() {
        log.info("Running test: {}", "step5_0_saveAddressCurrent");
        Response response = yblService.saveAddressCurrent(USER.toCurrentAddressDto());
        BaseService.assertStatus(response, 200, "POST", Routes.YBL_ADDRESS.getPath());
        assertJourneyLevel("YBL_RESIDENTIAL_ADDRESS_SUBMISSION_DONE");
    }

    // Step 5.1
    @Test(description = "Retrieve all saved addresses for the user",
          groups = "card-onboarding", dependsOnMethods = "step5_0_saveAddressCurrent")
    @TestSigmaId(
        value = "PO-12749",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Send GET /ybl/addresses\n2. Verify status 200\n3. Validate response against ybl_addresses.json schema",
        expectedResults = "API returns 200 with valid response body and schema"
    )
    public void step5_1_getAddresses() {
        log.info("Running test: {}", "step5_1_getAddresses");
        Response response = yblService.getAddresses();
        BaseService.assertStatus(response, 200, "GET", Routes.YBL_ADDRESSES.getPath());
        SchemaValidator.validate(response, "ybl_addresses.json");
    }

    // Step 6.0
    @Test(description = "Save user name on card and father name",
          groups = "card-onboarding", dependsOnMethods = "step5_1_getAddresses")
    @TestSigmaId(
        value = "PO-12750",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Build personal details request\n2. Send POST /ybl/personal-details\n3. Verify status 200\n4. Verify journey level = YBL_PERSONAL_DETAIL_SUBMISSION_DONE",
        expectedResults = "API returns 200 with valid response body and schema\nJourney level updates to YBL_PERSONAL_DETAIL_SUBMISSION_DONE"
    )
    public void step6_0_savePersonalDetails() {
        log.info("Running test: {}", "step6_0_savePersonalDetails");
        Response response = yblService.savePersonalDetails(USER.toPersonalDetailsDto());
        BaseService.assertStatus(response, 200, "POST", Routes.YBL_PERSONAL_DETAILS.getPath());
        assertJourneyLevel("YBL_PERSONAL_DETAIL_SUBMISSION_DONE");
    }

    // Step 6.1
    @Test(description = "Retrieve saved personal details",
          groups = "card-onboarding", dependsOnMethods = "step6_0_savePersonalDetails")
    @TestSigmaId(
        value = "PO-12751",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Send GET /ybl/personal-details\n2. Verify status 200\n3. Validate response against personal_details.json schema",
        expectedResults = "API returns 200 with valid response body and schema"
    )
    public void step6_1_getPersonalDetails() {
        log.info("Running test: {}", "step6_1_getPersonalDetails");
        Response response = yblService.getPersonalDetails();
        BaseService.assertStatus(response, 200, "GET", Routes.YBL_PERSONAL_DETAILS.getPath());
        SchemaValidator.validate(response, "personal_details.json");
    }

    // Step 7.0 GET
    @Test(description = "Fetch profession master lists for dropdown values",
          groups = "card-onboarding", dependsOnMethods = "step6_1_getPersonalDetails")
    @TestSigmaId(
        value = "PO-12752",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Send GET /ybl/master-lists with filter params\n2. Verify status 200\n3. Validate response against master_lists.json schema",
        expectedResults = "API returns 200 with valid response body and schema"
    )
    public void step7_0_getProfessionMasters() {
        log.info("Running test: {}", "step7_0_getProfessionMasters");
        Response response = yblService.getProfessionMasters();
        BaseService.assertStatus(response, 200, "GET", Routes.YBL_MASTER_LISTS.getPath());
        SchemaValidator.validate(response, "master_lists.json");
    }

    // Step 7.0 POST
    @Test(description = "Save user professional details (company, designation, income)",
          groups = "card-onboarding", dependsOnMethods = "step7_0_getProfessionMasters")
    @TestSigmaId(
        value = "PO-12753",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Build professional details request with all fields\n2. Send POST /ybl/professional-details\n3. Verify status 200\n4. Verify journey level = YBL_PROFESSIONAL_DETAIL_SUBMISSION_DONE",
        expectedResults = "API returns 200 with valid response body and schema\nJourney level updates to YBL_PROFESSIONAL_DETAIL_SUBMISSION_DONE"
    )
    public void step7_0_saveProfessionalDetails() {
        log.info("Running test: {}", "step7_0_saveProfessionalDetails");
        Response response = yblService.saveProfessionalDetails(USER.toProfessionalDetailsDto());
        BaseService.assertStatus(response, 200, "POST", Routes.YBL_PROFESSIONAL_DETAILS.getPath());
        assertJourneyLevel("YBL_PROFESSIONAL_DETAIL_SUBMISSION_DONE");
    }

    // Step 8.0
    @Test(description = "Save user office address",
          groups = "card-onboarding", dependsOnMethods = "step7_0_saveProfessionalDetails")
    @TestSigmaId(
        value = "PO-12754",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Build office address request\n2. Send POST /ybl/address\n3. Verify status 200\n4. Verify journey level = YBL_OFFICE_ADDRESS_SUBMISSION_DONE",
        expectedResults = "API returns 200 with valid response body and schema\nJourney level updates to YBL_OFFICE_ADDRESS_SUBMISSION_DONE"
    )
    public void step8_0_saveAddressOffice() {
        log.info("Running test: {}", "step8_0_saveAddressOffice");
        Response response = yblService.saveAddressOffice(USER.toOfficeAddressDto());
        BaseService.assertStatus(response, 200, "POST", Routes.YBL_ADDRESS.getPath());
        assertJourneyLevel("YBL_OFFICE_ADDRESS_SUBMISSION_DONE");
    }

    // Step 9.0
    @Test(description = "Save user delivery address preference",
          groups = "card-onboarding", dependsOnMethods = "step8_0_saveAddressOffice")
    @TestSigmaId(
        value = "PO-12755",
        folder = "happy",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "Fresh user, API environment accessible, valid test data in config",
        steps = "1. Build delivery address request with address type and same_as_current = true\n2. Send POST /ybl/address\n3. Verify status 200\n4. Verify journey level = YBL_DELIVERY_ADDRESS_SUBMISSION_DONE",
        expectedResults = "API returns 200 with valid response body and schema\nJourney level updates to YBL_DELIVERY_ADDRESS_SUBMISSION_DONE"
    )
    public void step9_0_saveAddressDelivery() {
        log.info("Running test: {}", "step9_0_saveAddressDelivery");
        Response response = yblService.saveAddressDelivery(USER.toDeliveryAddressDto());
        BaseService.assertStatus(response, 200, "POST", Routes.YBL_ADDRESS.getPath());
        assertJourneyLevel("YBL_DELIVERY_ADDRESS_SUBMISSION_DONE");
    }

    private void assertJourneyLevel(String expectedLevel) {
        Response journey = popService.getUserJourneyDetail();
        BaseService.assertStatus(journey, 200, "GET", Routes.POP_USER_JOURNEY.getPath());
        journey.then().body("data.last_saved_level", equalTo(expectedLevel));
    }

    @AfterClass
    public void teardown() {
        popService.reset();
        yblService.reset();
    }
}
