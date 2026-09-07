package com.popclub.apiTests.tuition;

import com.popclub.api.dto.tuition.*;
import com.popclub.api.enums.Routes;
import com.popclub.api.impl.BaseService;
import com.popclub.api.impl.OmsService;
import com.popclub.api.impl.OnboardingService;
import com.popclub.api.util.ConfigManager;
import com.popclub.core.TestContext;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TuitionOnboardingTest {

    private static final Logger log = LoggerFactory.getLogger(TuitionOnboardingTest.class);

    private OnboardingService onboarding;
    private OmsService        oms;

    private String hashiraToken;
    private String inviteCode;
    private String orderNumber;

    @BeforeClass
    public void setup() {
        onboarding = new OnboardingService();

        // invite_code + order_number set by TuitionPayerJourneyTest.step7
        inviteCode  = TestContext.getScalarData("c2c_invite_code");
        orderNumber = TestContext.getScalarData("c2c_order_number");

        // Fallback: read from config for standalone runs
        if (inviteCode == null || inviteCode.isEmpty()) {
            inviteCode = System.getProperty("c2c.invite.code", "");
        }
        if (orderNumber == null || orderNumber.isEmpty()) {
            orderNumber = System.getProperty("c2c.order.number", "");
        }

        // OMS service needs the student token to check order status
        String studentToken = TestContext.getUserToken();
        if (studentToken != null) {
            oms = new OmsService();
            oms.attachToken(studentToken);
        }

        log.info("TuitionOnboardingTest setup: inviteCode={}, orderNumber={}", inviteCode, orderNumber);
    }

    // ─── Happy Path ──────────────────────────────────────────────────────────

    @Test(groups = "tuition-onboarding")
    public void step1_sendOtp() {
        Response r = onboarding.sendOtp(SendOtpRequest.builder()
                .mobileNumber(ConfigManager.getTutorMobileNumber())
                .countryCode("+91")
                .build());
        BaseService.assertStatus(r, 200, "POST", Routes.ONBOARDING_SEND_OTP.getPath());
        log.info("OTP sent to tutor mobile {}", ConfigManager.getTutorMobileNumber());
    }

    @Test(groups = "tuition-onboarding", dependsOnMethods = "step1_sendOtp")
    public void step2_verifyOtp() {
        Response r = onboarding.verifyOtp(VerifyOtpRequest.builder()
                .mobileNumber(ConfigManager.getTutorMobileNumber())
                .countryCode("+91")
                .otp("1234")  // test OTP
                .build());
        BaseService.assertStatus(r, 200, "POST", Routes.ONBOARDING_VERIFY_OTP.getPath());

        hashiraToken = r.jsonPath().getString("data.token");
        if (hashiraToken == null) {
            hashiraToken = r.jsonPath().getString("data.access_token");
        }
        assertNotNull(hashiraToken, "hashira_token should be returned from VerifyOTP");
        onboarding.attachToken(hashiraToken);
        log.info("Tutor OTP verified, hashira_token acquired");
    }

    @Test(groups = "tuition-onboarding", dependsOnMethods = "step2_verifyOtp")
    public void step3_getPayeeDetails() {
        assertFalse(inviteCode.isEmpty(), "invite_code is required — run TuitionPayerJourneyTest first or pass -Dc2c.invite.code=...");
        Response r = onboarding.getPayeeDetails(inviteCode);
        BaseService.assertStatus(r, 200, "GET", Routes.ONBOARDING_PAYEE_DETAILS.getPath());
        assertNotNull(r.jsonPath().get("data.payee"), "Payee details should be present");
        log.info("Payee details fetched for invite_code={}", inviteCode);
    }

    @Test(groups = "tuition-onboarding", dependsOnMethods = "step3_getPayeeDetails")
    public void step4_payeeConsent_accept() {
        Response r = onboarding.payeeConsent(PayeeConsentRequest.builder()
                .inviteCode(inviteCode)
                .consent(true)
                .build());
        BaseService.assertStatus(r, 200, "POST", Routes.ONBOARDING_PAYEE_CONSENT.getPath());
        log.info("Tutor accepted payment consent");
    }

    @Test(groups = "tuition-onboarding", dependsOnMethods = "step4_payeeConsent_accept")
    public void step5_pollOrderStatus_accepted() {
        if (oms == null || orderNumber.isEmpty()) {
            log.warn("Skipping order status poll — no order context available");
            return;
        }
        Response r = oms.pollOrderStatus(orderNumber, "ACCEPTED", 10);
        BaseService.assertStatus(r, 200, "GET", Routes.OMS_ORDER_STATUS.kongUrl(orderNumber));
        String status = r.jsonPath().getString("data.status");
        log.info("Order status after tutor acceptance: {}", status);
        assertTrue("ACCEPTED".equals(status) || "COMPLETED".equals(status),
                "Order should be ACCEPTED or COMPLETED after consent, was: " + status);
    }

    // ─── Error Cases ─────────────────────────────────────────────────────────

    @Test(groups = "tuition-onboarding-error")
    public void error_sendOtp_invalidMobile() {
        Response r = onboarding.sendOtp(SendOtpRequest.builder()
                .mobileNumber("0000000000")
                .countryCode("+91")
                .build());
        assertTrue(r.statusCode() == 400 || r.statusCode() == 422,
                "Invalid mobile should return 400/422, got: " + r.statusCode());
        log.info("Error case: invalid mobile for SendOTP → {}", r.statusCode());
    }

    @Test(groups = "tuition-onboarding-error")
    public void error_verifyOtp_wrongOtp() {
        // First send OTP to get a session
        onboarding.sendOtp(SendOtpRequest.builder()
                .mobileNumber(ConfigManager.getTutorMobileNumber())
                .countryCode("+91")
                .build());

        Response r = onboarding.verifyOtp(VerifyOtpRequest.builder()
                .mobileNumber(ConfigManager.getTutorMobileNumber())
                .countryCode("+91")
                .otp("0000")  // wrong OTP
                .build());
        assertTrue(r.statusCode() == 400 || r.statusCode() == 401,
                "Wrong OTP should return 400/401, got: " + r.statusCode());
        log.info("Error case: wrong OTP → {}", r.statusCode());
    }

    @Test(groups = "tuition-onboarding-error")
    public void error_getPayeeDetails_invalidInviteCode() {
        if (hashiraToken == null) {
            log.warn("Skipping — no hashira_token (step2 not run)");
            return;
        }
        Response r = onboarding.getPayeeDetails("INVALID-CODE-000");
        assertTrue(r.statusCode() == 400 || r.statusCode() == 404,
                "Invalid invite code should return 400/404, got: " + r.statusCode());
        log.info("Error case: invalid invite code → {}", r.statusCode());
    }

    @Test(groups = "tuition-onboarding-error")
    public void error_payeeConsent_reject() {
        // Test rejection flow — tutor rejects the payment
        if (inviteCode.isEmpty() || hashiraToken == null) {
            log.warn("Skipping consent rejection test — no invite_code or token");
            return;
        }
        Response r = onboarding.payeeConsent(PayeeConsentRequest.builder()
                .inviteCode(inviteCode + "-copy")  // simulate a different/stale invite
                .consent(false)
                .build());
        assertTrue(r.statusCode() == 200 || r.statusCode() == 400 || r.statusCode() == 404,
                "Reject consent should return 200, 400 or 404, got: " + r.statusCode());
        log.info("Error case: reject consent → {}", r.statusCode());
    }

    @Test(groups = "tuition-onboarding-error")
    public void error_payeeConsent_noAuth() {
        OnboardingService unauthService = new OnboardingService();  // no token attached
        Response r = unauthService.payeeConsent(PayeeConsentRequest.builder()
                .inviteCode(inviteCode.isEmpty() ? "test-invite" : inviteCode)
                .consent(true)
                .build());
        assertEquals(r.statusCode(), 401, "Missing auth should return 401, got: " + r.statusCode());
        log.info("Error case: no auth on consent → {}", r.statusCode());
    }
}
