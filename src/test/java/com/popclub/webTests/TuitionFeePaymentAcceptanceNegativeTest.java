package com.popclub.webTests;

import com.popclub.web.base.WebBaseTest;
import com.popclub.web.constants.TestCaseId;
import com.popclub.web.listeners.RetryAnalyzer;
import com.popclub.web.pages.TuitionInviteOtpPage;
import com.popclub.web.pages.TuitionReceivePaymentPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Negative/error-path coverage for the tuition fee web acceptance flow.
 *
 * The tuition invite link + OTP are single-use, so each scenario below needs its
 * OWN fresh invite — reusing one across tests isn't possible once it's submitted.
 * Required system properties (pass only the ones needed for the test(s) you're running):
 *
 *   -DtuitionInviteUrlWrongOtp=<fresh invite>      — wrong OTP entry
 *   -DtuitionInviteUrlExpired=<a known-expired/already-consumed invite>
 *   -DtuitionInviteUrlInvalid=<malformed invite, e.g. .../verify/INV-DOES-NOT-EXIST>
 *   -DtuitionInviteUrlReject=<fresh invite> -DtuitionOtpReject=<its real OTP>  — reject flow
 *
 * The exact copy for "invalid OTP" / "invite expired" messages hasn't been confirmed
 * against the live page yet (see TuitionInviteOtpPage.OTP_ERROR_TEXT / INVITE_INVALID_TEXT) —
 * verify and tighten those selectors once run against the real environment.
 */
public class TuitionFeePaymentAcceptanceNegativeTest extends WebBaseTest {

    private static final Logger log = LoggerFactory.getLogger(TuitionFeePaymentAcceptanceNegativeTest.class);

    @Test(description = "Entering an incorrect OTP shows an error and does not proceed to receive-payment",
          groups = {"tuition-web-error"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-TUITION-WEB-ERR-WRONG-OTP")
    public void error_wrongOtp_showsErrorAndBlocksProgress() {
        String inviteUrl = requireProperty("tuitionInviteUrlWrongOtp");
        log.info("Running test: error_wrongOtp_showsErrorAndBlocksProgress");

        TuitionInviteOtpPage otpPage = new TuitionInviteOtpPage(page);
        otpPage.navigate(inviteUrl);
        Assert.assertTrue(otpPage.isPageLoaded(), "OTP input boxes should be visible");

        otpPage.fillOtp("000000"); // deliberately wrong — real OTP is never 000000
        Assert.assertTrue(otpPage.isVerifyButtonEnabled(), "Verify OTP button should enable once all 6 digits are filled");
        otpPage.clickVerifyOtp();

        Assert.assertTrue(otpPage.isOtpErrorVisible(), "An invalid-OTP error should be shown");
        Assert.assertTrue(otpPage.isPageLoaded(), "Should remain on the OTP screen, not advance to receive-payment");
    }

    @Test(description = "An expired or already-consumed invite link does not show the OTP screen",
          groups = {"tuition-web-error"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-TUITION-WEB-ERR-EXPIRED-INVITE")
    public void error_expiredInvite_showsExpiredState() {
        String inviteUrl = requireProperty("tuitionInviteUrlExpired");
        log.info("Running test: error_expiredInvite_showsExpiredState");

        TuitionInviteOtpPage otpPage = new TuitionInviteOtpPage(page);
        page.navigate(inviteUrl);
        page.waitForLoadState();

        Assert.assertTrue(otpPage.isInviteInvalidOrExpired(),
                "Expired/consumed invite should show an expired-or-invalid message");
    }

    @Test(description = "A malformed/non-existent invite code does not show the OTP screen",
          groups = {"tuition-web-error"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-TUITION-WEB-ERR-INVALID-INVITE-FORMAT")
    public void error_malformedInviteCode_showsInvalidState() {
        String inviteUrl = requireProperty("tuitionInviteUrlInvalid");
        log.info("Running test: error_malformedInviteCode_showsInvalidState");

        TuitionInviteOtpPage otpPage = new TuitionInviteOtpPage(page);
        page.navigate(inviteUrl);
        page.waitForLoadState();

        Assert.assertTrue(otpPage.isInviteInvalidOrExpired(),
                "A malformed/non-existent invite code should show an invalid-invite message");
    }

    @Test(description = "Tutor can reject the payment after OTP verification instead of confirming",
          groups = {"tuition-web-error"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-TUITION-WEB-ERR-REJECT-PAYMENT")
    public void error_tutorRejectsPayment_doesNotReachSuccessPage() {
        String inviteUrl = requireProperty("tuitionInviteUrlReject");
        String otp       = requireProperty("tuitionOtpReject");
        log.info("Running test: error_tutorRejectsPayment_doesNotReachSuccessPage");

        TuitionInviteOtpPage otpPage = new TuitionInviteOtpPage(page);
        otpPage.navigate(inviteUrl);
        Assert.assertTrue(otpPage.isPageLoaded(), "OTP input boxes should be visible");
        otpPage.fillOtp(otp);
        Assert.assertTrue(otpPage.isVerifyButtonEnabled(), "Verify OTP button should enable once all digits are filled");
        otpPage.clickVerifyOtp();

        TuitionReceivePaymentPage receivePage = new TuitionReceivePaymentPage(page);
        Assert.assertTrue(receivePage.isPageLoaded(), "Receive-payment confirmation page should load after OTP verification");
        receivePage.clickRejectPayment();

        Assert.assertFalse(receivePage.isConfirmButtonEnabled(),
                "Confirm & receive should no longer be actionable after rejecting the payment");
    }

    private static String requireProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required system property '" + key + "'. Pass it with -D" + key + "=<value>.");
        }
        return value;
    }
}
