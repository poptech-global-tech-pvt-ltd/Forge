package com.popclub.webTests;

import com.popclub.web.base.WebBaseTest;
import com.popclub.web.constants.TestCaseId;
import com.popclub.web.listeners.RetryAnalyzer;
import com.popclub.web.pages.TuitionInviteOtpPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Edge-case checks for the tuition invite OTP screen that stop short of actually
 * submitting the OTP, so they don't consume the invite's single-use OTP and can
 * share the same fresh invite link as other read-only checks.
 *
 * Required system property:
 *   -DtuitionInviteUrlEdge="https://onboarding.popclub-web.in/verify/INV-XXXX"
 * (a fresh, unconsumed invite — no real OTP needed since these tests never submit)
 */
public class TuitionFeePaymentAcceptanceEdgeTest extends WebBaseTest {

    private static final Logger log = LoggerFactory.getLogger(TuitionFeePaymentAcceptanceEdgeTest.class);

    @Test(description = "Verify OTP button stays disabled while fewer than 6 digits are entered",
          groups = {"tuition-web"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-TUITION-WEB-EDGE-PARTIAL-OTP")
    public void edge_partialOtp_verifyButtonStaysDisabled() {
        String inviteUrl = requireProperty("tuitionInviteUrlEdge");
        log.info("Running test: edge_partialOtp_verifyButtonStaysDisabled");

        TuitionInviteOtpPage otpPage = new TuitionInviteOtpPage(page);
        otpPage.navigate(inviteUrl);
        Assert.assertTrue(otpPage.isPageLoaded(), "OTP input boxes should be visible");

        otpPage.fillOtp("12345"); // only 5 of 6 digits
        Assert.assertFalse(otpPage.isOtpFullyFilled(), "Only 5 of 6 OTP boxes should be filled");
        Assert.assertFalse(otpPage.isVerifyButtonEnabled(), "Verify OTP button should stay disabled with an incomplete code");
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
