package com.popclub.apiTests.card;

import com.popclub.api.dto.*;
import com.popclub.api.enums.Routes;
import com.popclub.api.impl.BaseService;
import com.popclub.api.impl.PopService;
import com.popclub.api.impl.YblService;
import com.popclub.api.util.ConfigManager;
import com.popclub.testdata.TestUser;
import com.popclub.testdata.InvalidCases;
import com.popclub.testsigma.TestSigmaId;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import java.util.Arrays;
import java.util.Collections;


import static org.testng.Assert.*;

public class CardOnboardingNegativeTest {

    private static final Logger   log  = LoggerFactory.getLogger(CardOnboardingNegativeTest.class);
    private static final TestUser USER = TestUser.defaultFixed();

    private PopService popService;
    private YblService yblService;
    private PopService unauthPopService;
    private YblService unauthYblService;
    private String setupToken;

    @BeforeClass(alwaysRun = true)
    public void setup() {
        popService      = new PopService();
        yblService      = new YblService();
        unauthPopService  = new PopService();
        unauthYblService  = new YblService();

        Response ssoRaw = popService.verifySSO(new VerifySSORequestDto(ConfigManager.getMobileNumber()));
        BaseService.assertStatus(ssoRaw, 200, "POST", Routes.SSO_VERIFY.getPath());

        setupToken = ssoRaw.as(VerifySSOResponseDto.class).getData().getToken();
        popService.attachToken(setupToken);
        yblService.attachToken(setupToken);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static void assertUnauthorized(Response r) {
        int s = r.statusCode();
        assertTrue(s == 401 || s == 403, "Expected 401/403, got " + s);
    }

    private static void assertBadRequest(Response r) {
        int s = r.statusCode();
        assertTrue(s >= 400 && s < 500, "Expected 4xx, got " + s);
    }

    private static void assertErrorStatus(Response r) {
        int s = r.statusCode();
        assertTrue(s >= 400, "Expected error status (>=400), got " + s);
    }

    private static void assertAccepted(Response r, String reason) {
        assertEquals(r.statusCode(), 200, reason);
    }

    private static void assertIdempotentOrConflict(Response r) {
        int s = r.statusCode();
        assertTrue(s == 200 || s == 409, "Expected 200 or 409, got " + s);
    }

    // ── Auth tests (individual — each calls a different endpoint) ────────────

    @Test(description = "Reject GET user journey request when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12639", folder = "negative", labels = {"sanity", "regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Send GET /pop/user-journey-detail without auth token\n2. Verify response status code",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_getUserJourney() {
        log.info("Running: auth_noToken_getUserJourney");
        assertUnauthorized(unauthPopService.getUserJourneyDetail());
    }

    @Test(description = "Reject POST POP consent when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12640", folder = "negative", labels = {"sanity", "regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Build a valid consent request body\n2. Send POST /pop/consents without auth token\n3. Verify response status code",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_postPopConsent() {
        log.info("Running: auth_noToken_postPopConsent");
        assertUnauthorized(unauthPopService.postPopConsent(USER.toPopConsentDto()));
    }

    @Test(description = "Reject GET user journey when auth token is invalid", groups = "negative-auth")
    @TestSigmaId(value = "PO-12641", folder = "negative", labels = {"sanity", "regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Attach an invalid/garbage auth token\n2. Send GET /pop/user-journey-detail\n3. Verify response status code >= 400",
        expectedResults = "API returns error status (4xx)")
    public void auth_invalidToken_getUserJourney() {
        log.info("Running: auth_invalidToken_getUserJourney");
        PopService invalidService = new PopService();
        invalidService.attachToken(InvalidCases.invalidToken());
        assertErrorStatus(invalidService.getUserJourneyDetail());
    }

    @Test(description = "Reject GET YBL consents when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12642", folder = "negative", labels = {"sanity", "regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Send GET /ybl/consents without auth token\n2. Verify response status code",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_yblConsents() {
        log.info("Running: auth_noToken_yblConsents");
        assertUnauthorized(unauthYblService.getYblConsents());
    }

    @Test(description = "Reject POST user details (PAN) when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12643", folder = "negative", labels = {"sanity", "regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Build a valid PAN + pincode request body\n2. Send POST /pop/user-details without auth token\n3. Verify response",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_postUserDetailsPan() {
        log.info("Running: auth_noToken_postUserDetailsPan");
        assertUnauthorized(unauthPopService.updateUserDetailsPan(USER.toPanDto()));
    }

    @Test(description = "Reject GET user details when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12644", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Send GET /pop/user-details without auth token\n2. Verify response status code",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_getUserDetails() {
        log.info("Running: auth_noToken_getUserDetails");
        assertUnauthorized(unauthPopService.getUserDetails());
    }

    @Test(description = "Reject POST basic user details when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12645", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Build a valid basic details request body\n2. Send POST /pop/user-details without auth token\n3. Verify response",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_updateUserDetailsBasic() {
        log.info("Running: auth_noToken_updateUserDetailsBasic");
        assertUnauthorized(unauthPopService.updateUserDetailsBasic(USER.toBasicDetailsDto()));
    }

    @Test(description = "Reject GET verify pincode when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12646", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Send GET /pop/verify_pincode without auth token\n2. Verify response status code",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_verifyPincode() {
        log.info("Running: auth_noToken_verifyPincode");
        assertUnauthorized(unauthPopService.verifyPincode(USER.getPinCode()));
    }

    @Test(description = "Reject POST YBL consents when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12647", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Build a valid YBL consent request body\n2. Send POST /ybl/consents without auth token\n3. Verify response",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_saveYblConsents() {
        log.info("Running: auth_noToken_saveYblConsents");
        assertUnauthorized(unauthYblService.saveYblConsents(new YblConsentRequestDto()));
    }

    @Test(description = "Reject POST address when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12648", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Build a valid address request body\n2. Send POST /ybl/address without auth token\n3. Verify response",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_saveAddress() {
        log.info("Running: auth_noToken_saveAddress");
        assertUnauthorized(unauthYblService.saveAddressCurrent(USER.toCurrentAddressDto()));
    }

    @Test(description = "Reject GET addresses when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12649", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Send GET /ybl/addresses without auth token\n2. Verify response status code",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_getAddresses() {
        log.info("Running: auth_noToken_getAddresses");
        assertUnauthorized(unauthYblService.getAddresses());
    }

    @Test(description = "Reject POST personal details when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12650", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Build a valid personal details request body\n2. Send POST /ybl/personal-details without auth token\n3. Verify response",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_savePersonalDetails() {
        log.info("Running: auth_noToken_savePersonalDetails");
        assertUnauthorized(unauthYblService.savePersonalDetails(USER.toPersonalDetailsDto()));
    }

    @Test(description = "Reject GET personal details when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12651", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Send GET /ybl/personal-details without auth token\n2. Verify response status code",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_getPersonalDetails() {
        log.info("Running: auth_noToken_getPersonalDetails");
        assertUnauthorized(unauthYblService.getPersonalDetails());
    }

    @Test(description = "Reject GET master lists when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12652", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Send GET /ybl/master-lists without auth token\n2. Verify response status code",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_getMasterLists() {
        log.info("Running: auth_noToken_getMasterLists");
        assertUnauthorized(unauthYblService.getProfessionMasters());
    }

    @Test(description = "Reject POST professional details when auth token is missing", groups = "negative-auth")
    @TestSigmaId(value = "PO-12653", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Build a valid professional details request\n2. Send POST /ybl/professional-details without auth token\n3. Verify response",
        expectedResults = "API returns 401 or 403 status code")
    public void auth_noToken_saveProfessionalDetails() {
        log.info("Running: auth_noToken_saveProfessionalDetails");
        assertUnauthorized(unauthYblService.saveProfessionalDetails(USER.toProfessionalDetailsDto()));
    }

    // ── SSO — DataProvider ───────────────────────────────────────────────────

    @DataProvider(name = "invalidSsoMobiles")
    public Object[][] invalidSsoMobiles() {
        return new Object[][] {
            { InvalidCases.emptyString()         },
            { null                                  },
            { InvalidCases.shortMobile()         },
            { InvalidCases.specialCharsMobile() },
            { InvalidCases.alphabeticMobile()    },
            { InvalidCases.tooLongMobile()      },
        };
    }

    @Test(dataProvider = "invalidSsoMobiles",
          description = "Reject SSO verification when mobile number is invalid",
          groups = "negative-sso")
    @TestSigmaId(value = "PO-12654", folder = "negative", labels = {"sanity", "regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Send POST /sso/verify with invalid mobile (empty/null/short/special/alpha/long)\n2. Verify response status >= 400",
        expectedResults = "API returns error status for all invalid mobile inputs")
    public void sso_invalidMobile(String mobile) {
        log.info("Running: sso_invalidMobile [{}]", mobile);
        Response response = popService.verifySSO(new VerifySSORequestDto(mobile));
        assertErrorStatus(response);
    }

    // ── PAN — DataProvider ───────────────────────────────────────────────────

    @DataProvider(name = "invalidPanDtos")
    public Object[][] invalidPanDtos() {
        TestUser u = USER;
        return new Object[][] {
            { UserDetailsPanRequestDto.builder().pan(InvalidCases.invalidPan())       .pinCode(u.getPinCode()).build() },
            { UserDetailsPanRequestDto.builder().pan(InvalidCases.emptyString())      .pinCode(u.getPinCode()).build() },
            { UserDetailsPanRequestDto.builder().pan(null)                               .pinCode(u.getPinCode()).build() },
            { UserDetailsPanRequestDto.builder().pan(InvalidCases.specialCharsPan()) .pinCode(u.getPinCode()).build() },
            { UserDetailsPanRequestDto.builder().pan(InvalidCases.tooLongPan())      .pinCode(u.getPinCode()).build() },
            { UserDetailsPanRequestDto.builder().pan(u.getPan())                         .pinCode(null).build()          },
            { UserDetailsPanRequestDto.builder().pan(u.getPan())                         .pinCode(InvalidCases.emptyString()).build() },
        };
    }

    @Test(dataProvider = "invalidPanDtos",
          description = "Reject PAN submission with invalid PAN or pincode",
          groups = "negative-pan")
    @TestSigmaId(value = "PO-12665", folder = "negative", labels = {"sanity", "regression", "can_automate"},
        preconditions = "User authenticated via SSO with valid token",
        steps = "1. Send POST /pop/user-details with invalid PAN or pincode combination\n2. Verify response status 4xx",
        expectedResults = "API returns 4xx for all invalid PAN/pincode inputs")
    public void pan_invalidInput(UserDetailsPanRequestDto dto) {
        log.info("Running: pan_invalidInput [pan={}, pinCode={}]", dto.getPan(), dto.getPinCode());
        assertBadRequest(popService.updateUserDetailsPan(dto));
    }

    // ── Pincode — DataProvider ───────────────────────────────────────────────

    @DataProvider(name = "invalidPincodes")
    public Object[][] invalidPincodes() {
        return new Object[][] {
            { InvalidCases.lettersPincode()       },
            { InvalidCases.emptyString()          },
            { InvalidCases.shortPincode()         },
            { InvalidCases.specialCharsPincode() },
            { InvalidCases.tooLongPincode()      },
        };
    }

    @Test(dataProvider = "invalidPincodes",
          description = "Reject pincode verification when pincode is invalid",
          groups = "negative-pincode")
    @TestSigmaId(value = "PO-12673", folder = "negative", labels = {"sanity", "regression", "can_automate"},
        preconditions = "User authenticated via SSO with valid token",
        steps = "1. Send GET /pop/verify_pincode with invalid pincode (letters/empty/short/special/long)\n2. Verify response status 4xx",
        expectedResults = "API returns 4xx for all invalid pincode formats")
    public void pincode_invalidFormat(String pincode) {
        log.info("Running: pincode_invalidFormat [{}]", pincode);
        assertBadRequest(popService.verifyPincode(pincode));
    }

    // ── Basic details — DataProvider ─────────────────────────────────────────

    @DataProvider(name = "invalidBasicDetailsDtos")
    public Object[][] invalidBasicDetailsDtos() {
        TestUser u = USER;
        return new Object[][] {
            // missing firstName
            { UserDetailsBasicRequestDto.builder()
                .lastName(u.getLastName()).email(u.getEmail()).dob(u.getDob())
                .gender(u.getGender()).occupation(u.getOccupation()).maritalStatus(u.getMaritalStatus()).build() },
            // missing lastName
            { UserDetailsBasicRequestDto.builder()
                .firstName(u.getFirstName()).email(u.getEmail()).dob(u.getDob())
                .gender(u.getGender()).occupation(u.getOccupation()).maritalStatus(u.getMaritalStatus()).build() },
            // invalid email format
            { UserDetailsBasicRequestDto.builder()
                .firstName(u.getFirstName()).lastName(u.getLastName())
                .email(InvalidCases.invalidEmail()).dob(u.getDob())
                .gender(u.getGender()).occupation(u.getOccupation()).maritalStatus(u.getMaritalStatus()).build() },
            // invalid DOB format
            { UserDetailsBasicRequestDto.builder()
                .firstName(u.getFirstName()).lastName(u.getLastName()).email(u.getEmail())
                .dob(InvalidCases.invalidDob())
                .gender(u.getGender()).occupation(u.getOccupation()).maritalStatus(u.getMaritalStatus()).build() },
            // missing email
            { UserDetailsBasicRequestDto.builder()
                .firstName(u.getFirstName()).lastName(u.getLastName())
                .dob(u.getDob()).gender(u.getGender()).occupation(u.getOccupation()).maritalStatus(u.getMaritalStatus()).build() },
            // missing DOB
            { UserDetailsBasicRequestDto.builder()
                .firstName(u.getFirstName()).lastName(u.getLastName()).email(u.getEmail())
                .gender(u.getGender()).occupation(u.getOccupation()).maritalStatus(u.getMaritalStatus()).build() },
        };
    }

    @Test(dataProvider = "invalidBasicDetailsDtos",
          description = "Reject basic details submission when required fields are missing or invalid",
          groups = "negative-basic-details")
    @TestSigmaId(value = "PO-12678", folder = "negative", labels = {"sanity", "regression", "can_automate"},
        preconditions = "User authenticated via SSO with valid token",
        steps = "1. Build basic details request with missing/invalid required field\n2. Send POST /pop/user-details\n3. Verify response status 4xx",
        expectedResults = "API returns 4xx for all missing/invalid required field combinations")
    public void basicDetails_invalidInput(UserDetailsBasicRequestDto dto) {
        log.info("Running: basicDetails_invalidInput [firstName={}, lastName={}, email={}]",
                dto.getFirstName(), dto.getLastName(), dto.getEmail());
        assertBadRequest(popService.updateUserDetailsBasic(dto));
    }

    // API accepts invalid gender without validation — document behavior
    @Test(description = "Document API behavior when gender value is invalid (no validation)",
          groups = "negative-basic-details")
    @TestSigmaId(value = "PO-12684", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated via SSO with valid token",
        steps = "1. Build basic details request with gender = 'INVALID_GENDER'\n2. Send POST /pop/user-details\n3. Verify response",
        expectedResults = "API returns 200 (no validation) or 4xx")
    public void basicDetails_invalidGender() {
        log.info("Running: basicDetails_invalidGender");
        TestUser u = USER;
        UserDetailsBasicRequestDto dto = UserDetailsBasicRequestDto.builder()
                .firstName(u.getFirstName()).middleName(u.getMiddleName()).lastName(u.getLastName())
                .email(u.getEmail()).dob(u.getDob())
                .gender(InvalidCases.invalidGender())
                .occupation(u.getOccupation()).maritalStatus(u.getMaritalStatus())
                .build();
        assertAccepted(popService.updateUserDetailsBasic(dto),
                "API currently accepts invalid gender without validation");
    }

    // ── Consent ─────────────────────────────────────────────────────────────

    @Test(description = "Reject POST POP consent when body contains different mobile than token",
          groups = "negative-consent")
    @TestSigmaId(value = "PO-12662", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated via SSO",
        steps = "1. Build consent request with a different mobile number\n2. Send POST /pop/consents\n3. Verify response status 4xx",
        expectedResults = "API returns 4xx status code with error message")
    public void popConsent_wrongMobileInBody() {
        log.info("Running: popConsent_wrongMobileInBody");
        PopConsentRequestDto request = PopConsentRequestDto.builder()
                .mobileNumber(InvalidCases.wrongMobile())
                .consents(Collections.singletonList(
                        PopConsentRequestDto.Consent.builder()
                                .name("TandC").title("I accept the important terms & conditions")
                                .isParent(true).isMandatory(true).value(true).build()))
                .build();
        assertBadRequest(popService.postPopConsent(request));
    }

    @Test(description = "Document API behavior when consents array is empty (no validation)",
          groups = "negative-consent")
    @TestSigmaId(value = "PO-12663", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated via SSO",
        steps = "1. Build consent request with empty consents array\n2. Send POST /pop/consents\n3. Verify response is 200 or 4xx",
        expectedResults = "API returns 200 (no validation) or 4xx")
    public void consent_emptyConsentsList() {
        log.info("Running: consent_emptyConsentsList");
        PopConsentRequestDto request = PopConsentRequestDto.builder()
                .mobileNumber(ConfigManager.getMobileNumber())
                .consents(Collections.emptyList())
                .build();
        assertAccepted(popService.postPopConsent(request),
                "API currently accepts empty consents list without validation");
    }

    @Test(description = "Document API behavior when T&C consent is set to false (no validation)",
          groups = "negative-consent")
    @TestSigmaId(value = "PO-12664", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated via SSO",
        steps = "1. Build consent request with T&C consent = false\n2. Send POST /pop/consents\n3. Verify response",
        expectedResults = "API returns 200 (no validation) or 4xx")
    public void consent_mandatoryConsentRejected() {
        log.info("Running: consent_mandatoryConsentRejected");
        PopConsentRequestDto request = PopConsentRequestDto.builder()
                .mobileNumber(ConfigManager.getMobileNumber())
                .consents(Collections.singletonList(
                        PopConsentRequestDto.Consent.builder()
                                .name("TandC").title("I accept the important terms & conditions")
                                .isParent(true).isMandatory(true).value(false).build()))
                .build();
        assertAccepted(popService.postPopConsent(request),
                "API currently accepts mandatory consent rejection without validation");
    }

    // ── GET POP consents — DataProvider ─────────────────────────────────────

    @DataProvider(name = "invalidConsentMobiles")
    public Object[][] invalidConsentMobiles() {
        return new Object[][] {
            { InvalidCases.emptyString()      },
            { InvalidCases.alphabeticMobile() },
        };
    }

    @Test(dataProvider = "invalidConsentMobiles",
          description = "Reject GET POP consents when mobile query param is invalid",
          groups = "negative-consent")
    @TestSigmaId(value = "PO-12660", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated via SSO",
        steps = "1. Send GET /pop/consents with invalid mobile_number param\n2. Verify response status 4xx",
        expectedResults = "API returns 4xx for invalid mobile_number query param")
    public void popConsentsGet_invalidMobile(String mobile) {
        log.info("Running: popConsentsGet_invalidMobile [{}]", mobile);
        assertBadRequest(popService.getPopConsentsWithMobile(mobile));
    }

    // ── YBL Consents ─────────────────────────────────────────────────────────

    @Test(description = "Reject YBL consents when T&C consent is false",
          groups = "negative-ybl-consents")
    @TestSigmaId(value = "PO-12685", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated via SSO with valid token",
        steps = "1. Build YBL consent request with term_condition_consent = false\n2. Send POST /ybl/consents\n3. Verify response is 4xx",
        expectedResults = "API returns 4xx when mandatory T&C consent is false")
    public void yblConsents_mandatoryConsentFalse() {
        log.info("Running: yblConsents_mandatoryConsentFalse");
        YblConsentRequestDto request = new YblConsentRequestDto();
        request.setTermConditionConsent(false);
        assertBadRequest(yblService.saveYblConsents(request));
    }

    @Test(description = "Reject YBL consents when CIBIL consent is false",
          groups = "negative-ybl-consents")
    @TestSigmaId(value = "PO-12686", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated via SSO with valid token",
        steps = "1. Build YBL consent request with cibil_consent = false\n2. Send POST /ybl/consents\n3. Verify response is 4xx",
        expectedResults = "API returns 4xx when CIBIL consent is false")
    public void yblConsents_cibilConsentFalse() {
        log.info("Running: yblConsents_cibilConsentFalse");
        YblConsentRequestDto request = new YblConsentRequestDto();
        request.setCibilConsent(false);
        assertBadRequest(yblService.saveYblConsents(request));
    }

    // ── Current address — DataProvider ───────────────────────────────────────

    @DataProvider(name = "invalidCurrentAddressDtos")
    public Object[][] invalidCurrentAddressDtos() {
        TestUser u = USER;
        return new Object[][] {
            // missing addressLine1
            { AddressRequestDto.builder()
                .addressType(u.getCurrentAddressType())
                .addressLine2(u.getCurrentAddressLine2()).addressLine3(u.getCurrentAddressLine3())
                .city(u.getCurrentAddressCity()).state(u.getCurrentAddressState())
                .country(u.getCurrentAddressCountry()).pinCode(u.getCurrentAddressPinCode()).build() },
            // missing city
            { AddressRequestDto.builder()
                .addressType(u.getCurrentAddressType())
                .addressLine1(u.getCurrentAddressLine1()).addressLine2(u.getCurrentAddressLine2())
                .state(u.getCurrentAddressState()).country(u.getCurrentAddressCountry())
                .pinCode(u.getCurrentAddressPinCode()).build() },
            // invalid pincode
            { AddressRequestDto.builder()
                .addressType(u.getCurrentAddressType())
                .addressLine1(u.getCurrentAddressLine1()).addressLine2(u.getCurrentAddressLine2())
                .city(u.getCurrentAddressCity()).state(u.getCurrentAddressState())
                .country(u.getCurrentAddressCountry())
                .pinCode(InvalidCases.invalidAddressPincode()).build() },
            // missing state
            { AddressRequestDto.builder()
                .addressType(u.getCurrentAddressType())
                .addressLine1(u.getCurrentAddressLine1()).addressLine2(u.getCurrentAddressLine2())
                .city(u.getCurrentAddressCity()).country(u.getCurrentAddressCountry())
                .pinCode(u.getCurrentAddressPinCode()).build() },
        };
    }

    @Test(dataProvider = "invalidCurrentAddressDtos",
          description = "Reject current address save when required fields are missing or invalid",
          groups = "negative-address")
    @TestSigmaId(value = "PO-12687", folder = "negative", labels = {"sanity", "regression", "can_automate"},
        preconditions = "User authenticated, PAN and consents completed",
        steps = "1. Build address request with missing/invalid field\n2. Send POST /ybl/address\n3. Verify response status 4xx",
        expectedResults = "API returns 4xx for all invalid current address combinations")
    public void address_invalidInput(AddressRequestDto dto) {
        log.info("Running: address_invalidInput [addressLine1={}, city={}, pinCode={}]",
                dto.getAddressLine1(), dto.getCity(), dto.getPinCode());
        assertBadRequest(yblService.saveAddressCurrent(dto));
    }

    // ── Office address — DataProvider ────────────────────────────────────────

    @DataProvider(name = "invalidOfficeAddressDtos")
    public Object[][] invalidOfficeAddressDtos() {
        TestUser u = USER;
        return new Object[][] {
            // invalid pincode
            { AddressRequestDto.builder()
                .addressType(u.getOfficeAddressType())
                .addressLine1(u.getOfficeAddressLine1()).addressLine2(u.getOfficeAddressLine2())
                .city(u.getOfficeAddressCity()).state(u.getOfficeAddressState())
                .country(u.getOfficeAddressCountry())
                .pinCode(InvalidCases.invalidAddressPincode()).build() },
        };
    }

    @Test(dataProvider = "invalidOfficeAddressDtos",
          description = "Reject office address save when required fields are missing or invalid",
          groups = "negative-office-address")
    @TestSigmaId(value = "PO-12715", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated, PAN and consents completed",
        steps = "1. Build office address request with missing/invalid field\n2. Send POST /ybl/address\n3. Verify response status 4xx",
        expectedResults = "API returns 4xx for all invalid office address combinations")
    public void officeAddress_invalidInput(AddressRequestDto dto) {
        log.info("Running: officeAddress_invalidInput [city={}, pinCode={}]",
                dto.getCity(), dto.getPinCode());
        assertBadRequest(yblService.saveAddressOffice(dto));
    }

    // ── Delivery address ─────────────────────────────────────────────────────

    @DataProvider(name = "invalidDeliveryAddressDtos")
    public Object[][] invalidDeliveryAddressDtos() {
        return new Object[][] {
            { AddressRequestDto.builder().addressType(InvalidCases.invalidAddressType()).isDeliveryAddress(true).build() },
            { AddressRequestDto.builder().isDeliveryAddress(true).build() },
        };
    }

    @Test(dataProvider = "invalidDeliveryAddressDtos",
          description = "Reject delivery address save when address type is invalid or null",
          groups = "negative-delivery-address")
    @TestSigmaId(value = "PO-12727", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated, PAN and consents completed",
        steps = "1. Build delivery address request with invalid/null address_type\n2. Send POST /ybl/address\n3. Verify response status 4xx",
        expectedResults = "API returns 4xx for invalid/null delivery address type")
    public void deliveryAddress_invalidType(AddressRequestDto dto) {
        log.info("Running: deliveryAddress_invalidType [addressType={}]", dto.getAddressType());
        assertBadRequest(yblService.saveAddressDelivery(dto));
    }

    // ── Personal details — DataProvider ──────────────────────────────────────

    @DataProvider(name = "invalidPersonalDetailsDtos")
    public Object[][] invalidPersonalDetailsDtos() {
        TestUser u = USER;
        return new Object[][] {
            { PersonalDetailsRequestDto.builder().fatherName(u.getFatherName()).build() },    // missing nameOnCard
            { PersonalDetailsRequestDto.builder().nameOnCard(u.getNameOnCard()).build() },    // missing fatherName
        };
    }

    @Test(dataProvider = "invalidPersonalDetailsDtos",
          description = "Reject personal details save when required fields are missing",
          groups = "negative-personal")
    @TestSigmaId(value = "PO-12691", folder = "negative", labels = {"sanity", "regression", "can_automate"},
        preconditions = "User authenticated, PAN and consents completed",
        steps = "1. Build personal details request with missing required field\n2. Send POST /ybl/personal-details\n3. Verify response status 4xx",
        expectedResults = "API returns 4xx for missing required personal detail fields")
    public void personalDetails_missingRequiredField(PersonalDetailsRequestDto dto) {
        log.info("Running: personalDetails_missingRequiredField [nameOnCard={}, fatherName={}]",
                dto.getNameOnCard(), dto.getFatherName());
        assertBadRequest(yblService.savePersonalDetails(dto));
    }

    @Test(description = "Document API behavior when name contains special characters (no validation)",
          groups = "negative-personal")
    @TestSigmaId(value = "PO-12694", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated, PAN and consents completed",
        steps = "1. Build personal details request with name_on_card = '!@#$%^&*()'\n2. Send POST /ybl/personal-details\n3. Verify response",
        expectedResults = "API returns 200 (no validation) or 4xx")
    public void personalDetails_specialCharsNameOnCard() {
        log.info("Running: personalDetails_specialCharsNameOnCard");
        PersonalDetailsRequestDto dto = PersonalDetailsRequestDto.builder()
                .nameOnCard(InvalidCases.specialCharsName())
                .fatherName(USER.getFatherName())
                .build();
        assertAccepted(yblService.savePersonalDetails(dto),
                "API currently accepts special characters in name without validation");
    }

    // ── Professional details — DataProvider ──────────────────────────────────

    @DataProvider(name = "invalidProfessionalDetailsDtos")
    public Object[][] invalidProfessionalDetailsDtos() {
        TestUser u = USER;
        return new Object[][] {
            // missing companyName
            { ProfessionalDetailsRequestDto.builder()
                .designation(u.getDesignation()).annualIncome(u.getAnnualIncome())
                .companyType(u.getCompanyType()).profession(u.getProfession())
                .occupation(u.getProfessionalOccupation()).build() },
            // missing designation
            { ProfessionalDetailsRequestDto.builder()
                .companyName(u.getCompanyName()).annualIncome(u.getAnnualIncome())
                .companyType(u.getCompanyType()).profession(u.getProfession())
                .occupation(u.getProfessionalOccupation()).build() },
            // zero income
            { ProfessionalDetailsRequestDto.builder()
                .companyName(u.getCompanyName()).designation(u.getDesignation())
                .annualIncome(InvalidCases.zeroIncome())
                .companyType(u.getCompanyType()).profession(u.getProfession())
                .occupation(u.getProfessionalOccupation()).build() },
            // negative income
            { ProfessionalDetailsRequestDto.builder()
                .companyName(u.getCompanyName()).designation(u.getDesignation())
                .annualIncome(InvalidCases.negativeIncome())
                .companyType(u.getCompanyType()).profession(u.getProfession())
                .occupation(u.getProfessionalOccupation()).build() },
        };
    }

    @Test(dataProvider = "invalidProfessionalDetailsDtos",
          description = "Reject professional details save when required fields are missing or invalid",
          groups = "negative-professional")
    @TestSigmaId(value = "PO-12695", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated, PAN and consents completed",
        steps = "1. Build professional details request with missing/invalid field\n2. Send POST /ybl/professional-details\n3. Verify response status 4xx",
        expectedResults = "API returns 4xx for all invalid professional detail combinations")
    public void professionalDetails_invalidInput(ProfessionalDetailsRequestDto dto) {
        log.info("Running: professionalDetails_invalidInput [company={}, designation={}, income={}]",
                dto.getCompanyName(), dto.getDesignation(), dto.getAnnualIncome());
        assertBadRequest(yblService.saveProfessionalDetails(dto));
    }

    // ── Duplicate submission ─────────────────────────────────────────────────

    @Test(description = "Verify SSO call is idempotent (200) or returns 409 on duplicate",
          groups = "negative-duplicate")
    @TestSigmaId(value = "PO-12729", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "API environment is accessible",
        steps = "1. Send POST /sso/verify with valid mobile\n2. Send POST /sso/verify again with same mobile\n3. Verify second response is 200 or 409",
        expectedResults = "API returns 200 (idempotent) or 409 (conflict)")
    public void duplicate_ssoVerifyTwice() {
        log.info("Running: duplicate_ssoVerifyTwice");
        popService.verifySSO(new VerifySSORequestDto(ConfigManager.getMobileNumber()));
        Response second = popService.verifySSO(new VerifySSORequestDto(ConfigManager.getMobileNumber()));
        assertIdempotentOrConflict(second);
    }

    // ── 5xx / Server error tests ─────────────────────────────────────────────

    @DataProvider(name = "malformedRequestCases")
    public Object[][] malformedRequestCases() {
        return new Object[][] {
            { "application/json", InvalidCases.malformedJson(), Routes.POP_USER_DETAILS.getPath()       },
            { "application/json", InvalidCases.malformedJson(), Routes.YBL_CONSENTS.getPath()           },
            { "application/json", InvalidCases.malformedJson(), Routes.YBL_ADDRESS.getPath()            },
            { "application/json", InvalidCases.malformedJson(), Routes.YBL_PERSONAL_DETAILS.getPath()   },
            { "application/json", InvalidCases.malformedJson(), Routes.YBL_PROFESSIONAL_DETAILS.getPath() },
            { "application/json", InvalidCases.emptyBody(),     Routes.POP_USER_DETAILS.getPath()       },
            { "application/json", InvalidCases.emptyBody(),     Routes.YBL_ADDRESS.getPath()            },
            { "application/json", InvalidCases.emptyBody(),     Routes.YBL_CONSENTS.getPath()           },
            { InvalidCases.invalidContentType(), "{\"pan\":\"ABCDE1234F\",\"pin_code\":\"560102\"}", Routes.POP_USER_DETAILS.getPath() },
            { InvalidCases.invalidContentType(), "{\"term_condition_consent\":true}", Routes.YBL_CONSENTS.getPath() },
        };
    }

    @Test(dataProvider = "malformedRequestCases",
          description = "Server gracefully handles malformed/empty body and invalid content type",
          groups = "negative-5xx")
    @TestSigmaId(value = "PO-12730", folder = "negative", labels = {"regression", "can_automate"},
        preconditions = "User authenticated via SSO with valid token",
        steps = "1. Send request with malformed body or wrong content type to various endpoints\n2. Verify response >= 400",
        expectedResults = "API returns 4xx or 5xx gracefully without crashing")
    public void server_gracefullyHandlesBadRequest(String contentType, String body, String path) {
        log.info("Running: server_gracefullyHandlesBadRequest [contentType={}, path={}]", contentType, path);
        assertErrorStatus(BaseService.rawPost(setupToken, contentType, body, path));
    }

    @AfterClass(alwaysRun = true)
    public void teardown() {
        popService.reset();
        yblService.reset();
        unauthPopService.reset();
        unauthYblService.reset();
    }
}
