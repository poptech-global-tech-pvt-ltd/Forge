package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * External YES Bank Aadhaar consent page (Angular Material UI).
 *
 * <p>This page lives on a separate domain (consent-sandbox.yesuat.bank.in).
 * The session URL is generated dynamically; navigate to it before instantiating this page.
 *
 * <p>Happy-path flow:
 * <ol>
 *   <li>Fill Aadhaar number</li>
 *   <li>Accept the consent checkbox</li>
 *   <li>Click Submit → OTP is sent</li>
 *   <li>Fill OTP</li>
 *   <li>Click Submit OTP</li>
 * </ol>
 */
public class YesBankConsentPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(YesBankConsentPage.class);

    private static final String AADHAAR_INPUT    = "[placeholder='Aadhaar number']";
    private static final String CONSENT_CHECKBOX = ".mat-checkbox-inner-container";
    private static final String SUBMIT_BTN       = "button:has-text('Submit')";
    private static final String OTP_INPUT        = "[placeholder='One Time Password']";
    private static final String SUBMIT_OTP_BTN   = "button:has-text('Submit OTP')";

    public YesBankConsentPage(Page page) {
        this.page = page;
    }

    public boolean isAadhaarInputVisible() {
        page.locator(AADHAAR_INPUT)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean visible = page.locator(AADHAAR_INPUT).isVisible();
        log.debug("YesBankConsentPage isAadhaarInputVisible: {}", visible);
        return visible;
    }

    public YesBankConsentPage fillAadhaar(String aadhaarNumber) {
        log.info("Filling Aadhaar number on YesBankConsentPage");
        page.locator(AADHAAR_INPUT).click();
        page.locator(AADHAAR_INPUT).fill(aadhaarNumber);
        return this;
    }

    public YesBankConsentPage acceptConsent() {
        log.info("Accepting consent on YesBankConsentPage");
        page.locator(CONSENT_CHECKBOX).click();
        return this;
    }

    public YesBankConsentPage clickSubmit() {
        log.info("Clicking Submit on YesBankConsentPage");
        page.locator(SUBMIT_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(SUBMIT_BTN).click();
        return this;
    }

    public YesBankConsentPage fillOtp(String otp) {
        log.info("Filling OTP on YesBankConsentPage");
        page.locator(OTP_INPUT)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(OTP_INPUT).fill(otp);
        return this;
    }

    public void clickSubmitOtp() {
        log.info("Clicking Submit OTP on YesBankConsentPage");
        page.locator(SUBMIT_OTP_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(SUBMIT_OTP_BTN).click();
    }
}
