package com.popclub.webTests;

import com.popclub.api.util.WebOnboardingSetup;
import com.popclub.web.base.WebBaseTest;
import com.popclub.web.constants.TestCaseId;
import com.popclub.web.listeners.RetryAnalyzer;
import com.popclub.web.pages.*;
import com.popclub.web.util.AadhaarOtpReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class FullApplicationFlowTest extends WebBaseTest {

    private static final Logger log = LoggerFactory.getLogger(FullApplicationFlowTest.class);

    private static final String OTP_CODE       = "560102";
    private static final String PAN_NUMBER     = "BVXPN1005K";
    private static final String PIN_CODE       = "560102";
    private static final String FIRST_NAME     = "Deepa";
    private static final String MIDDLE_NAME    = "D";
    private static final String LAST_NAME      = "Hegde";
    private static final String DOB_YEAR       = "1997";
    private static final String DOB_MONTH      = "May";
    private static final String DOB_OPTION     = "Choose Tuesday, May 6th,";
    private static final String EMAIL          = "dhegdetech@gmail.com";
    private static final String NOMINEE_NAME   = "Diwakar Hegde";
    private static final String COMPANY_NAME   = "Popclub";
    private static final String COMPANY_TYPE   = "Public Ltd Company";
    private static final String DESIGNATION    = "SDE";
    private static final String JOB_TITLE      = "ACCOUNTANT";
    private static final String ANNUAL_INCOME  = "1,30,0000";
    private static final String MAP_QUERY      = "Poptech";
    private static final String MAP_SUGGESTION = "Poptech Growth Private Limited, Haralur,";
    private static final String AADHAAR_NUMBER = "314510407414";
    private static final String AADHAAR_OTP    = "608495";

    @Override
    protected boolean isEkycTest(org.testng.ITestResult result) {
        return "fullEligibilityApplicationFlow".equals(result.getMethod().getMethodName());
    }

    // ── Smoke tests ──────────────────────────────────────────────────────────

    @Test(description = "Verify landing page loads and APPLY NOW button is visible",
          groups = {"smoke", "regression"}, retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-XXXX")
    public void verifyLandingPageLoads() {
        log.info("Running test: {}", "verifyLandingPageLoads");
        LandingPage landingPage = new LandingPage(page).navigate(baseUrl);
        Assert.assertTrue(landingPage.isApplyNowVisible(),
                "APPLY NOW button should be visible on the landing page");
        log.info("[WEB] Landing page loaded — APPLY NOW visible");
    }

    @Test(description = "Verify OTP page loads with all required elements",
          groups = {"smoke", "regression"}, retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-XXXX")
    public void verifyOtpPageElements() {
        log.info("Running test: {}", "verifyOtpPageElements");
        page.navigate(baseUrl + "/otp");
        OtpPage otpPage = new OtpPage(page);
        Assert.assertTrue(otpPage.isPageLoaded(),             "Phone input should be visible");
        Assert.assertTrue(otpPage.isLogoVisible(),            "Logo in banner should be visible");
        Assert.assertTrue(otpPage.isHeadingVisible(),         "Heading 'Check your eligibility' should be visible");
        Assert.assertTrue(otpPage.isContinueButtonVisible(),  "Continue button should be visible");
        log.info("[WEB] OTP page elements verified");
    }

    @Test(description = "Verify Terms & Conditions page loads after entering phone and clicking Continue",
          groups = {"smoke", "regression"}, retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-XXXX")
    public void verifyTermsPageLoadsAfterPhoneEntry() {
        log.info("Running test: {}", "verifyTermsPageLoadsAfterPhoneEntry");
        page.navigate(baseUrl + "/otp");
        new OtpPage(page).isPageLoaded();
        new OtpPage(page).fillPhone(testPhone).clickContinue();

        TermsConditionsPage termsPage = new TermsConditionsPage(page);
        Assert.assertTrue(termsPage.isPageLoaded(),  "Terms & Conditions heading should appear");
        Assert.assertTrue(termsPage.isTandCVisible(), "T&C checkbox should be visible");
        log.info("[WEB] Terms & Conditions page loaded after phone entry");
    }

    @Test(description = "Verify T&C consent items become visible after expanding the section",
          groups = {"smoke", "regression"}, retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-XXXX")
    public void verifyConsentItemsExpandOnToggle() {
        log.info("Running test: {}", "verifyConsentItemsExpandOnToggle");
        page.navigate(baseUrl + "/otp");
        new OtpPage(page).isPageLoaded();
        new OtpPage(page).fillPhone(testPhone).clickContinue();

        TermsConditionsPage termsPage = new TermsConditionsPage(page);
        termsPage.isPageLoaded();
        termsPage.expandConsentSection();

        Assert.assertTrue(page.getByText("I accept the important terms").isVisible(),
                "First consent line should appear after expanding");
        Assert.assertTrue(page.getByText("I authorise POP to call/SMS/e").isVisible(),
                "Second consent line should appear after expanding");
        Assert.assertTrue(page.getByText("I authorise POP to verify my").isVisible(),
                "Third consent line should appear after expanding");
        log.info("[WEB] Consent items visible after toggle expanded");
    }

    @Test(description = "Verify PAN details page shows PAN and pin-code inputs",
          groups = {"smoke", "regression"}, retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-XXXX")
    public void verifyPanDetailsPageElements() {
        log.info("Running test: {}", "verifyPanDetailsPageElements");
        page.navigate(baseUrl + "/pan-details");
        PanDetailsPage panPage = new PanDetailsPage(page);
        Assert.assertTrue(panPage.isPageLoaded(),          "PAN input should be visible");
        Assert.assertTrue(panPage.isPinCodeInputVisible(), "Pin code input should be visible");
        log.info("[WEB] PAN details page elements verified");
    }

    @Test(description = "Verify personal details form renders all required fields",
          groups = {"smoke", "regression"}, retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-XXXX")
    public void verifyPersonalDetailsPageElements() {
        log.info("Running test: {}", "verifyPersonalDetailsPageElements");
        page.navigate(baseUrl + "/level-0");
        PersonalDetailsPage detailsPage = new PersonalDetailsPage(page);
        Assert.assertTrue(detailsPage.isPageLoaded(),         "First-name input should be visible");
        Assert.assertTrue(detailsPage.isMiddleNameVisible(),  "Middle-name input should be visible");
        Assert.assertTrue(detailsPage.isLastNameVisible(),    "Last-name input should be visible");
        Assert.assertTrue(detailsPage.isEmailVisible(),       "Email input should be visible");
        Assert.assertTrue(detailsPage.isSalariedVisible(),    "Salaried option should be visible");
        Assert.assertTrue(detailsPage.isSelfEmployedVisible(), "Self Employed option should be visible");
        Assert.assertTrue(detailsPage.isMarriedVisible(),     "Married option should be visible");
        Assert.assertTrue(detailsPage.isFemaleVisible(),      "FEMALE option should be visible");
        Assert.assertTrue(detailsPage.isMaleVisible(),        "MALE option should be visible");
        log.info("[WEB] Personal details page elements verified");
    }

    // ── E2E regression test ───────────────────────────────────────────────────

    @Test(description = "Full POPCard eligibility E2E: Landing → OTP → T&C → OTP verify → " +
                        "PAN → Personal → PEP → Address → Additional → Office → Aadhaar → Video KYC",
          groups = {"e2e", "regression"}, retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-XXXX")
    public void fullEligibilityApplicationFlow() {
        log.info("Running test: {}", "fullEligibilityApplicationFlow");
        runWithYblCheck(this::runFullEligibilityFlow);
    }

    private void runFullEligibilityFlow() {
        // Find a first-time-user phone number
        WebOnboardingSetup setup = new WebOnboardingSetup();
        testPhone = setup.prepare();
        log.info("[WEB] Using prepared phone: {}", testPhone);

        log.info("[WEB] Step 1: Landing page — verify Apply Now and click");
        LandingPage landingPage = new LandingPage(page).navigate(baseUrl);
        Assert.assertTrue(landingPage.isApplyNowVisible(), "APPLY NOW must be visible");
        landingPage.clickApplyNow();
        Assert.assertTrue(landingPage.isEligibilityTextVisible(), "Eligibility text must appear");

        log.info("[WEB] Step 2: OTP page — enter phone {}", testPhone);
        page.navigate(baseUrl + "/otp");
        OtpPage otpPage = new OtpPage(page);
        Assert.assertTrue(otpPage.isPageLoaded(), "OTP page must load");
        otpPage.fillPhone(testPhone).clickContinue();
        assertNotOnErrorPage();

        log.info("[WEB] Step 3: Terms & Conditions — accept and continue");
        TermsConditionsPage termsPage = new TermsConditionsPage(page);
        Assert.assertTrue(termsPage.isPageLoaded(), "T&C page must load");
        termsPage.checkTandC().clickContinue();
        assertNotOnErrorPage();

        log.info("[WEB] Step 4: OTP verification — enter code {}", OTP_CODE);
        OtpVerificationPage otpVerPage = new OtpVerificationPage(page);
        Assert.assertTrue(otpVerPage.isOtpInputVisible(),      "OTP input must be visible");
        Assert.assertTrue(otpVerPage.isResendOtpLinkVisible(), "Resend OTP link must be visible");
        otpVerPage.fillOtp(OTP_CODE);
        assertNotOnErrorPage();

        log.info("[WEB] Post-login API: signup + mark hybrid journey for {}", testPhone);
        setup.setupAfterWebLogin(testPhone);

        log.info("[WEB] Step 5: PAN details — fill PAN and pin code");
        PanDetailsPage panPage = new PanDetailsPage(page);
        Assert.assertTrue(panPage.isPageLoaded(), "PAN details page must load");
        panPage.fillPan(PAN_NUMBER).fillPinCode(PIN_CODE).clickContinue();
        assertNotOnErrorPage();

        log.info("[WEB] Step 6: Personal details — fill all fields");
        PersonalDetailsPage personalPage = new PersonalDetailsPage(page);
        Assert.assertTrue(personalPage.isPageLoaded(), "Personal details page must load");
        personalPage.fillFirstName(FIRST_NAME)
                    .fillMiddleName(MIDDLE_NAME)
                    .fillLastName(LAST_NAME)
                    .selectDateOfBirth(DOB_YEAR, DOB_MONTH, DOB_OPTION)
                    .fillEmail(EMAIL)
                    .selectSalaried()
                    .selectMarried()
                    .selectFemale()
                    .clickContinue();
        assertNotOnErrorPage();

        log.info("[WEB] Step 7: PEP questions — select NO, accept YesBank consent");
        EligibilityPage pepPage = new EligibilityPage(page);
        Assert.assertTrue(pepPage.isYesBankConsentVisible(), "YesBank consent checkbox must be visible");
        pepPage.selectPepNo().selectRelationshipNo().checkYesBankConsent().clickContinue();
        assertNotOnErrorPage();

        log.info("[WEB] Step 8: Residential address — fill and continue");
        page.navigate(baseUrl + "/level-1");
        AddressPage addressPage = new AddressPage(page);
        Assert.assertTrue(addressPage.isPageLoaded(), "Address page must load");
        addressPage.fillAddressLine1("317,3rd floor, RR residency")
                   .fillAddressLine2("Hsr layout sector 2")
                   .fillPostalCode(PIN_CODE)
                   .clickContinue();
        assertNotOnErrorPage();

        log.info("[WEB] Step 9: Additional details — enter nominee name");
        AdditionalDetailsPage additionalPage = new AdditionalDetailsPage(page);
        Assert.assertTrue(additionalPage.isPageLoaded(), "Additional details page must load");
        additionalPage.fillNomineeName(NOMINEE_NAME).clickContinue();
        assertNotOnErrorPage();

        log.info("[WEB] Step 10: Office details — fill company and designation");
        OfficeDetailsPage officePage = new OfficeDetailsPage(page);
        Assert.assertTrue(officePage.isPageLoaded(), "Office details page must load");
        officePage.fillCompanyName(COMPANY_NAME)
                  .selectCompanyType(COMPANY_TYPE)
                  .fillDesignation(DESIGNATION)
                  .selectJobTitle(JOB_TITLE)
                  .fillAnnualIncome(ANNUAL_INCOME)
                  .clickContinue();
        assertNotOnErrorPage();

        log.info("[WEB] Step 11: Office address — pick location from map");
        OfficeAddressPage officeAddressPage = new OfficeAddressPage(page);
        Assert.assertTrue(officeAddressPage.isPageLoaded(), "Office address page must load");
        officeAddressPage.selectLocationFromMap(MAP_QUERY, MAP_SUGGESTION);
        Assert.assertTrue(officeAddressPage.isAddedFromMapsHeadingVisible(),
                "'Added from maps' heading should appear");
        officeAddressPage.clickContinue();
        assertNotOnErrorPage();

        log.info("[WEB] Step 12: Aadhaar verification page");
        page.navigate(baseUrl + "/level-5");
        AadhaarPage aadhaarPage = new AadhaarPage(page);
        Assert.assertTrue(aadhaarPage.isPageLoaded(),        "Aadhaar page must load");
        Assert.assertTrue(aadhaarPage.isYesBankInfoVisible(), "YES Bank info text must be visible");
        aadhaarPage.clickContinue();
        assertNotOnErrorPage();

        log.info("[WEB] Step 13: YES Bank consent — Aadhaar entry and OTP");
        YesBankConsentPage yesBankPage = new YesBankConsentPage(page);
        yesBankPage.fillAadhaar(AADHAAR_NUMBER)
                   .acceptConsent()
                   .clickSubmit()
                   .fillOtp(AADHAAR_OTP)
                   .clickSubmitOtp();
        assertNotOnErrorPage();

        log.info("[WEB] Step 14: Video KYC — verify and start");
        page.navigate(baseUrl + "/level-6");
        VideoKycPage vkycPage = new VideoKycPage(page);
        Assert.assertTrue(vkycPage.isPageLoaded(), "Video KYC page must load");
        vkycPage.expandDetails();
        Assert.assertTrue(vkycPage.isStartVideoKycButtonVisible(), "START VIDEO KYC button must be visible");
        vkycPage.clickStartVideoKyc();
        Assert.assertTrue(vkycPage.isProceedToVideoKycVisible(), "Proceed to Video KYC must be visible");
        vkycPage.clickProceedToVideoKyc();
        vkycPage.clickAllowAccess();

        log.info("[WEB] Full eligibility application flow completed successfully");
    }
}
