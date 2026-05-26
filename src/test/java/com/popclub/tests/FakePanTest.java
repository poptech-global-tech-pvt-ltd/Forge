package com.popclub.tests;

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
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Submits a fake PAN on a fresh user and verifies YBL rejects it.
 * The mobile number used is configured via fake.pan.mobile in sit.properties.
 */
public class FakePanTest {

    private static final Logger log = LoggerFactory.getLogger(FakePanTest.class);

    private PopService popService;
    private YblService yblService;

    @BeforeClass
    public void setup() {
        String mobile = ConfigManager.getFakePanMobile();

        popService = new PopService();
        yblService = new YblService();

        // Step 1: SSO with new mobile
        Response ssoRaw = popService.verifySSO(new VerifySSORequestDto(mobile));
        BaseService.assertStatus(ssoRaw, 200, "POST", Routes.SSO_VERIFY.getPath());

        VerifySSOResponseDto ssoResponse = ssoRaw.as(VerifySSOResponseDto.class);
        assertTrue(ssoResponse.isSuccess(), "SSO verification failed for mobile: " + mobile);

        String token = ssoResponse.getData().getToken();
        popService.attachToken(token);
        yblService.attachToken(token);

        TestUser user = TestUser.defaultFixed();

        // Step 2: POST POP consents
        PopConsentRequestDto consentReq = PopConsentRequestDto.builder()
                .mobileNumber(mobile)
                .consents(java.util.Arrays.asList(
                        PopConsentRequestDto.Consent.builder().name("TandC")
                                .title("I accept the important terms & conditions to apply for the POPcard")
                                .isParent(true).isMandatory(true).value(true).build(),
                        PopConsentRequestDto.Consent.builder().name("call-sms-email")
                                .title("I authorise POP to call/SMS/e-mail/send Whatsapp messages to me")
                                .isParent(false).isMandatory(true).value(true).build(),
                        PopConsentRequestDto.Consent.builder().name("PAN")
                                .title("I authorise POP to verify my official details from NSDL using my PAN card.")
                                .isParent(false).isMandatory(true).value(true).build()
                ))
                .build();
        BaseService.assertStatus(popService.postPopConsent(consentReq), 200, "POST", Routes.POP_CONSENTS.getPath());

        // Step 3: Submit FAKE PAN + pincode
        UserDetailsPanRequestDto fakePanDto = UserDetailsPanRequestDto.builder()
                .pan(InvalidCases.fakeValidPan())
                .pinCode(user.getPinCode())
                .build();
        BaseService.assertStatus(popService.updateUserDetailsPan(fakePanDto), 200, "POST", Routes.POP_USER_DETAILS.getPath());

        // Step 4: Verify pincode
        BaseService.assertStatus(popService.verifyPincode(user.getPinCode()), 200, "GET", Routes.POP_VERIFY_PINCODE.getPath());

        // Step 5: GET user details (auto-filled from PAN)
        BaseService.assertStatus(popService.getUserDetails(), 200, "GET", Routes.POP_USER_DETAILS.getPath());

        // Step 6: POST basic user details
        BaseService.assertStatus(popService.updateUserDetailsBasic(user.toBasicDetailsDto()), 200, "POST", Routes.POP_USER_DETAILS.getPath());

        System.out.println("[FakePanTest] Setup complete — ready to test YBL consents rejection for mobile: " + mobile);
    }

    @Test(description = "Reject YBL consents when submitted PAN is fake (NSDL rejection)")
    @TestSigmaId(
        value = "PO-12672",
        folder = "negative",
        labels = {"sanity", "regression", "can_automate"},
        preconditions = "User authenticated via SSO with valid token",
        steps = "1. Send POST /pop/user-details with PAN = 'AAAAA1111A' (valid format, fake)\n2. Verify 200 on PAN step\n3. Send POST /ybl/consents\n4. Verify 4xx with error code ppc40001",
        expectedResults = "YBL consents returns 4xx with error code ppc40001"
    )
    public void pan_validFormatButFakePan() {
        log.info("Running test: {}", "pan_validFormatButFakePan");
        Response response = yblService.saveYblConsents(new YblConsentRequestDto());
        int status = response.statusCode();
        assertTrue(status >= 400 && status < 500, "Expected 4xx, got " + status);
        assertEquals(response.jsonPath().getString("error.code"), InvalidCases.errInvalidPan(),
                "Expected error code " + InvalidCases.errInvalidPan());
    }
}
