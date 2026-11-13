package com.popclub.apiTests.tuition;

import com.popclub.api.dto.tuition.*;
import com.popclub.api.enums.Routes;
import com.popclub.api.impl.OmsService;
import com.popclub.api.impl.OnboardingService;
import com.popclub.api.util.ConfigManager;
import com.popclub.core.TestContext;
import com.popclub.testdata.TuitionTestData;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import com.popclub.web.constants.TestCaseId;
import org.testng.annotations.Test;

import java.util.List;

import static com.popclub.api.impl.BaseService.assertStatus;
import static org.testng.Assert.*;

public class TuitionOnboardingTest {

    private static final Logger log = LoggerFactory.getLogger(TuitionOnboardingTest.class);

    private OnboardingService onboarding;
    private OmsService        oms;

    private String hashiraToken;
    private String inviteCode;
    private String orderNumber;

    @BeforeClass(alwaysRun = true)
    public void setup() {
        onboarding = new OnboardingService();

        // invite_code + order_number set by TuitionPayerJourneyTest.step7
        inviteCode  = TestContext.getScalarData("c2c_invite_code");
        orderNumber = TestContext.getScalarData("c2c_order_number");

        if (inviteCode == null || inviteCode.isEmpty()) {
            inviteCode = System.getProperty("c2c.invite.code", "");
        }
        if (orderNumber == null || orderNumber.isEmpty()) {
            orderNumber = System.getProperty("c2c.order.number", "");
        }

        String studentToken = TestContext.getUserToken();
        if (studentToken != null) {
            oms = new OmsService();
            oms.attachToken(studentToken);
        }

        log.info("TuitionOnboardingTest setup: inviteCode={}, orderNumber={}", inviteCode, orderNumber);
    }

    private static List<PayeeConsentRequest.ConsentItem> consentItems(String action) {
        TuitionTestData.Consent consent = TuitionTestData.get().getConsent();
        return List.of(
                PayeeConsentRequest.ConsentItem.of(
                        consent.getConsentTypeId(), consent.getConsentVersionId(), action)
        );
    }

    // precondition: Tutor mobile number is configured (ConfigManager.getTutorMobileNumber()).
    // steps: Call POST /onboarding/send-otp with the tutor's mobile number.
    // expected: HTTP 200 — OTP is sent to the tutor's mobile.
    @Test(groups = "tuition-onboarding")
    @TestCaseId("PO-17249")
    public void step1_sendOtp() {
        Response r = onboarding.sendOtp(SendOtpRequest.forMobile(ConfigManager.getTutorMobileNumber()));
        assertStatus(r, 200, "POST", Routes.ONBOARDING_SEND_OTP.getPath());
        log.info("OTP sent to tutor mobile {}", ConfigManager.getTutorMobileNumber());
    }

    // precondition: OTP has been sent in step1.
    // steps: Call POST /onboarding/verify-otp with the tutor's mobile number and the test OTP.
    // expected: HTTP 200 and a hashira_token (tutor session token) is returned; attach it for subsequent calls.
    @Test(groups = "tuition-onboarding", dependsOnMethods = "step1_sendOtp")
    @TestCaseId("PO-17250")
    public void step2_verifyOtp() {
        Response r = onboarding.verifyOtp(VerifyOtpRequest.of(ConfigManager.getTutorMobileNumber(), "1234"));
        assertStatus(r, 200, "POST", Routes.ONBOARDING_VERIFY_OTP.getPath());

        VerifyOtpResponse verifyOtpResponse = r.as(VerifyOtpResponse.class);
        hashiraToken = verifyOtpResponse.getData().getAccessToken();
        assertNotNull(hashiraToken, "hashira_token should be returned from VerifyOTP");
        onboarding.attachToken(hashiraToken);
        log.info("Tutor OTP verified, hashira_token acquired");
    }

    // precondition: hashira_token from step2 is attached; invite_code is available (from TuitionPayerJourneyTest.step7 or -Dc2c.invite.code).
    // steps: Call GET /onboarding/payee-details/{inviteCode}.
    // expected: HTTP 200 and the response's payee details object is present.
    @Test(groups = "tuition-onboarding", dependsOnMethods = "step2_verifyOtp")
    @TestCaseId("PO-17251")
    public void step3_getPayeeDetails() {
        assertFalse(inviteCode.isEmpty(), "invite_code is required — run TuitionPayerJourneyTest first or pass -Dc2c.invite.code=...");
        Response r = onboarding.getPayeeDetails(inviteCode);
        assertStatus(r, 200, "GET", Routes.ONBOARDING_PAYEE_DETAILS.getPath());

        PayeeDetailsResponse payeeDetailsResponse = r.as(PayeeDetailsResponse.class);
        assertNotNull(payeeDetailsResponse.getData().getPayee(), "Payee details should be present");
        log.info("Payee details fetched for invite_code={}", inviteCode);
    }

    // precondition: Payee details fetched in step3; invite_code is available.
    // steps: Call POST /onboarding/payee-consent with action="confirm", the invite_code, and a "granted" consent item.
    // expected: HTTP 200 — tutor's acceptance of the payment is recorded.
    @Test(groups = "tuition-onboarding", dependsOnMethods = "step3_getPayeeDetails")
    @TestCaseId("PO-17252")
    public void step4_payeeConsent_accept() {
        Response r = onboarding.payeeConsent(
                PayeeConsentRequest.from("confirm", inviteCode, consentItems("granted")));
        assertStatus(r, 200, "POST", Routes.ONBOARDING_PAYEE_CONSENT.getPath());
        log.info("Tutor accepted payment consent");
    }

    // precondition: Tutor consent accepted in step4; order_number and the student access_token are available.
    // steps: Poll GET /order-status/{orderNumber} (up to 10 attempts) until status becomes ACCEPTED/COMPLETED.
    // expected: Order status reaches ACCEPTED or COMPLETED.
    @Test(groups = "tuition-onboarding", dependsOnMethods = "step4_payeeConsent_accept")
    @TestCaseId("PO-17253")
    public void step5_pollOrderStatus_accepted() {
        if (oms == null || orderNumber.isEmpty()) {
            log.warn("Skipping order status poll — no order context available");
            return;
        }
        Response r = oms.pollOrderStatus(orderNumber, "ACCEPTED", 10);
        assertStatus(r, 200, "GET", Routes.OMS_ORDER_STATUS.kongUrl(orderNumber));

        OrderStatusResponse statusResponse = r.as(OrderStatusResponse.class);
        String status = statusResponse.getData().getStatus();
        log.info("Order status after tutor acceptance: {}", status);
        assertTrue("ACCEPTED".equals(status) || "COMPLETED".equals(status),
                "Order should be ACCEPTED or COMPLETED after consent, was: " + status);
    }

    // precondition: None.
    // steps: Call POST /onboarding/send-otp with an all-zero, clearly invalid mobile number ("0000000000").
    // expected: HTTP 400 or 422.
    @Test(groups = "tuition-onboarding-error")
    @TestCaseId("PO-17254")
    public void error_sendOtp_invalidMobile() {
        Response r = onboarding.sendOtp(SendOtpRequest.forMobile("0000000000"));
        assertTrue(r.statusCode() == 400 || r.statusCode() == 422,
                "Invalid mobile should return 400/422, got: " + r.statusCode());
        log.info("Error case: invalid mobile for SendOTP → {}", r.statusCode());
    }

    // precondition: None — sends a fresh OTP first, then deliberately verifies with the wrong code.
    // steps: Call POST /onboarding/send-otp, then POST /onboarding/verify-otp with OTP="0000" (wrong).
    // expected: HTTP 400 or 401.
    @Test(groups = "tuition-onboarding-error")
    @TestCaseId("PO-17255")
    public void error_verifyOtp_wrongOtp() {
        onboarding.sendOtp(SendOtpRequest.forMobile(ConfigManager.getTutorMobileNumber()));

        Response r = onboarding.verifyOtp(
                VerifyOtpRequest.of(ConfigManager.getTutorMobileNumber(), "0000")); // wrong OTP
        assertTrue(r.statusCode() == 400 || r.statusCode() == 401,
                "Wrong OTP should return 400/401, got: " + r.statusCode());
        log.info("Error case: wrong OTP → {}", r.statusCode());
    }

    // precondition: hashira_token from step2 must already be set (skips with a warning otherwise).
    // steps: Call GET /onboarding/payee-details/INVALID-CODE-000 (a non-existent invite code).
    // expected: HTTP 400 or 404.
    @Test(groups = "tuition-onboarding-error")
    @TestCaseId("PO-17256")
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

    // precondition: invite_code and hashira_token must already be set (skips with a warning otherwise).
    // steps: Call POST /onboarding/payee-consent with action="reject" against a stale/different invite_code ("<inviteCode>-copy").
    // expected: HTTP 200, 400 or 404 (any is acceptable — asserts the call completes without a 5xx).
    @Test(groups = "tuition-onboarding-error")
    @TestCaseId("PO-17257")
    public void error_payeeConsent_reject() {
        if (inviteCode.isEmpty() || hashiraToken == null) {
            log.warn("Skipping consent rejection test — no invite_code or token");
            return;
        }
        Response r = onboarding.payeeConsent(
                PayeeConsentRequest.from(
                        "reject", inviteCode + "-copy", // simulate a different/stale invite
                        consentItems("rejected")));
        assertTrue(r.statusCode() == 200 || r.statusCode() == 400 || r.statusCode() == 404,
                "Reject consent should return 200, 400 or 404, got: " + r.statusCode());
        log.info("Error case: reject consent → {}", r.statusCode());
    }

    // precondition: None — uses a freshly created OnboardingService with no token attached.
    // steps: Call POST /onboarding/payee-consent with action="confirm" and no auth token attached.
    // expected: HTTP 401 — unauthenticated request is rejected.
    @Test(groups = "tuition-onboarding-error")
    @TestCaseId("PO-17258")
    public void error_payeeConsent_noAuth() {
        OnboardingService unauthService = new OnboardingService();  // no token attached
        Response r = unauthService.payeeConsent(
                PayeeConsentRequest.from(
                        "confirm",
                        inviteCode.isEmpty() ? "test-invite" : inviteCode,
                        consentItems("granted")));
        assertEquals(r.statusCode(), 401, "Missing auth should return 401, got: " + r.statusCode());
        log.info("Error case: no auth on consent → {}", r.statusCode());
    }
}
