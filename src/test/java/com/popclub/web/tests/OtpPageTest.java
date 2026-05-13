package com.popclub.web.tests;

import com.popclub.web.base.WebBaseTest;
import com.popclub.web.constants.AppConstants;
import com.popclub.web.constants.TestCaseId;
import com.popclub.web.listeners.RetryAnalyzer;
import com.popclub.web.pages.OtpPage;
import com.popclub.web.pages.TermsConditionsPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class OtpPageTest extends WebBaseTest {

    private static final Logger log = LoggerFactory.getLogger(OtpPageTest.class);

    @Test(description = "Verify OTP page loads with all required elements",
          groups = {"smoke", "regression"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-5629")
    public void verifyOtpPageLoads() {
        log.info("Running test: {}", "verifyOtpPageLoads");
        OtpPage otpPage = new OtpPage(page);
        otpPage.navigate(baseUrl);

        Assert.assertTrue(otpPage.isPageLoaded(),             "Phone input should be visible");
        Assert.assertTrue(otpPage.isLogoVisible(),            "Logo should be visible");
        Assert.assertTrue(otpPage.isHeadingVisible(),         "Heading 'Check your eligibility' should be visible");
        Assert.assertTrue(otpPage.isContinueButtonVisible(),  "Continue button should be visible");
    }

    @Test(description = "Enter valid phone number and proceed to Terms & Conditions",
          groups = {"smoke", "regression"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-5629")
    public void enterPhoneAndProceedToTerms() {
        log.info("Running test: {}", "enterPhoneAndProceedToTerms");
        OtpPage otpPage = new OtpPage(page);
        otpPage.navigate(baseUrl)
               .fillPhone(AppConstants.TEST_PHONE)
               .clickContinue();

        TermsConditionsPage termsPage = new TermsConditionsPage(page);
        Assert.assertTrue(termsPage.isPageLoaded(), "Terms & Conditions page should load after entering phone");
    }
}
