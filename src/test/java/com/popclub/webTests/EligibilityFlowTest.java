package com.popclub.webTests;

import com.popclub.web.base.WebBaseTest;
import com.popclub.web.constants.AppConstants;
import com.popclub.web.constants.TestCaseId;
import com.popclub.web.listeners.RetryAnalyzer;
import com.popclub.web.pages.EligibilityPage;
import com.popclub.web.pages.OtpPage;
import com.popclub.web.pages.TermsConditionsPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class EligibilityFlowTest extends WebBaseTest {

    private static final Logger log = LoggerFactory.getLogger(EligibilityFlowTest.class);

    @Test(description = "Enter phone → accept T&C → enter OTP → verify eligibility page loads",
          groups = {"smoke", "regression"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-5629")
    public void fullEligibilityFlow() {
        log.info("Running test: {}", "fullEligibilityFlow");
        // Step 1: Enter phone number
        OtpPage otpPage = new OtpPage(page);
        otpPage.navigate(baseUrl)
               .fillPhone(AppConstants.TEST_PHONE)
               .clickContinue();

        // Step 2: Accept Terms & Conditions
        TermsConditionsPage termsPage = new TermsConditionsPage(page);
        Assert.assertTrue(termsPage.isPageLoaded(), "Terms & Conditions page should appear");
        termsPage.clickContinue();

        // Step 3: Enter OTP
        EligibilityPage eligibilityPage = new EligibilityPage(page);
        Assert.assertTrue(eligibilityPage.isPageLoaded(), "OTP input should be visible");
        eligibilityPage.fillOtp(AppConstants.TEST_OTP);

        // Step 4: Verify eligibility questions appear
        Assert.assertTrue(eligibilityPage.isYesBankConsentVisible(), "YesBank consent should be visible after OTP");
    }

    @Test(description = "Enter phone → accept T&C → enter OTP → answer PEP No → submit",
          groups = {"regression"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-5629")
    public void eligibilityFlowPepNo() {
        log.info("Running test: {}", "eligibilityFlowPepNo");
        // Step 1: Enter phone number
        OtpPage otpPage = new OtpPage(page);
        otpPage.navigate(baseUrl)
               .fillPhone(AppConstants.TEST_PHONE)
               .clickContinue();

        // Step 2: Accept Terms & Conditions
        TermsConditionsPage termsPage = new TermsConditionsPage(page);
        termsPage.isPageLoaded();
        termsPage.clickContinue();

        // Step 3: Enter OTP
        EligibilityPage eligibilityPage = new EligibilityPage(page);
        eligibilityPage.isPageLoaded();
        eligibilityPage.fillOtp(AppConstants.TEST_OTP);

        // Step 4: Answer PEP = No, Relationship = No, accept YesBank consent
        eligibilityPage.selectPepNo()
                       .selectRelationshipNo()
                       .checkYesBankConsent()
                       .clickContinue();
    }

    @Test(description = "Verify T&C consent checkboxes expand on toggle",
          groups = {"regression"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-5629")
    public void verifyConsentCheckboxesExpandOnToggle() {
        log.info("Running test: {}", "verifyConsentCheckboxesExpandOnToggle");
        OtpPage otpPage = new OtpPage(page);
        otpPage.navigate(baseUrl)
               .fillPhone(AppConstants.TEST_PHONE)
               .clickContinue();

        TermsConditionsPage termsPage = new TermsConditionsPage(page);
        termsPage.isPageLoaded();
        termsPage.expandConsentSection();

        Assert.assertTrue(termsPage.isCallSmsConsentVisible(), "Call/SMS/Email consent checkbox should be visible");
        Assert.assertTrue(termsPage.isPanConsentVisible(),     "PAN consent checkbox should be visible");
    }
}
