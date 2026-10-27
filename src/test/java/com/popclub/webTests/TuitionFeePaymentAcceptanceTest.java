package com.popclub.webTests;

import com.popclub.web.base.WebBaseTest;
import com.popclub.web.constants.TestCaseId;
import com.popclub.web.listeners.RetryAnalyzer;
import com.popclub.web.pages.TuitionInviteOtpPage;
import com.popclub.web.pages.TuitionPaymentInitiatedPage;
import com.popclub.web.pages.TuitionReceivePaymentPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Happy-path web acceptance of a tuition/education-fee payment link: verify OTP,
 * accept Seller T&C, receive payment, land on the success page.
 *
 * The invite link and OTP are single-use per invite, so they're supplied at runtime:
 *   mvn test -Dsurefire.suiteXmlFiles=src/test/resources/suites/testng-web.xml \
 *       -DtuitionInviteUrl="https://onboarding.popclub-web.in/verify/INV-XXXX" -DtuitionOtp="123456"
 *
 * In the full mobile→web chain, tuitionInviteUrl is the payment link captured by
 * Forge's network interceptor from the Android "send link" step.
 */
public class TuitionFeePaymentAcceptanceTest extends WebBaseTest {

    private static final Logger log = LoggerFactory.getLogger(TuitionFeePaymentAcceptanceTest.class);

    @Test(description = "Verify OTP → accept Seller T&C → confirm & receive tuition fee → success page",
          groups = {"e2e", "regression"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-TUITION-C2C-WEB")
    public void tutorAcceptsTuitionFeePayment() {
        String inviteUrl = requireProperty("tuitionInviteUrl");
        String otp       = requireProperty("tuitionOtp");

        log.info("Running test: tutorAcceptsTuitionFeePayment");

        TuitionInviteOtpPage otpPage = new TuitionInviteOtpPage(page);
        otpPage.navigate(inviteUrl);
        Assert.assertTrue(otpPage.isPageLoaded(), "OTP input boxes should be visible");
        Assert.assertTrue(otpPage.isFeeHeadingVisible(), "Education fee heading should be visible");
        otpPage.fillOtp(otp);
        Assert.assertTrue(otpPage.isVerifyButtonEnabled(), "Verify OTP button should enable once all digits are filled");
        otpPage.clickVerifyOtp();

        TuitionReceivePaymentPage receivePage = new TuitionReceivePaymentPage(page);
        Assert.assertTrue(receivePage.isPageLoaded(), "Receive-payment confirmation page should load after OTP verification");
        Assert.assertTrue(receivePage.isSellerIdVisible(), "Seller ID should be visible on confirm details");
        receivePage.acceptTerms();
        Assert.assertTrue(receivePage.isConfirmButtonEnabled(), "Confirm & receive button should enable once T&C is accepted");
        receivePage.clickConfirmAndReceive();

        TuitionPaymentInitiatedPage successPage = new TuitionPaymentInitiatedPage(page);
        Assert.assertTrue(successPage.isPageLoaded(), "Payment-initiated success page should load");
        Assert.assertTrue(successPage.isDownloadButtonVisible(), "Download POP CTA should be visible on success page");
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
