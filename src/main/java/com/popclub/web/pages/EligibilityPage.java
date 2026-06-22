package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EligibilityPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(EligibilityPage.class);

    private static final String OTP_INPUT          = "input[name='otp']";
    private static final String PEP_YES            = "text=YES";
    private static final String PEP_NO             = "text=NO";
    private static final String RELATIONSHIP_YES   = "text=yes";
    private static final String RELATIONSHIP_NO    = "text=no";
    private static final String YESBANK_CONSENT    = "input[name='yesbank_authorize_consent']";
    // All required YES Bank consent checkboxes on /level-0.1
    private static final String[] ALL_REQUIRED_CONSENTS = {
        "yesbank_authorize_consent",
        "promo_consent",
        "cibil_consent",
        "term_condition_consent",
        "user_comm_consent",
        "kfs_consent",
        "yesbank_gogreen_consent",
        "digit_app_consent"
    };
    private static final String BANK_OFFICER_INPUT = "input[name='bank_officer_name']";
    private static final String SELECTED_OPTION    = ".selected-option";
    private static final String CONTINUE_BUTTON    = "form button:has-text('Continue')";

    public EligibilityPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(OTP_INPUT).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(OTP_INPUT).isVisible();
        log.debug("EligibilityPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public EligibilityPage fillOtp(String otp) {
        log.info("Filling OTP on EligibilityPage");
        page.locator(OTP_INPUT).fill(otp);
        return this;
    }

    public EligibilityPage selectPepNo() {
        log.info("Selecting PEP: NO");
        page.locator(PEP_NO).click();
        return this;
    }

    public EligibilityPage selectPepYes() {
        log.info("Selecting PEP: YES");
        page.locator(PEP_YES).click();
        return this;
    }

    public EligibilityPage selectRelationshipYes() {
        log.info("Selecting relationship with PEP: yes");
        page.locator(RELATIONSHIP_YES).click();
        return this;
    }

    public EligibilityPage selectRelationshipNo() {
        log.info("Selecting relationship with PEP: no");
        page.locator(RELATIONSHIP_NO).click();
        return this;
    }

    public EligibilityPage checkYesBankConsent() {
        log.info("Checking all required YES Bank consent checkboxes");
        for (String name : ALL_REQUIRED_CONSENTS) {
            Locator cb = page.locator("input[name='" + name + "']");
            cb.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            if (!cb.isChecked()) cb.check();
        }
        return this;
    }

    public boolean isYesBankConsentVisible() {
        boolean visible = page.locator(YESBANK_CONSENT).isVisible();
        log.debug("EligibilityPage isYesBankConsentVisible: {}", visible);
        return visible;
    }

    public boolean isBankOfficerInputVisible() {
        boolean visible = page.locator(BANK_OFFICER_INPUT).isVisible();
        log.debug("EligibilityPage isBankOfficerInputVisible: {}", visible);
        return visible;
    }

    public void clickContinue() {
        log.info("Clicking Continue on EligibilityPage");
        page.locator(CONTINUE_BUTTON).waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(CONTINUE_BUTTON).click();
    }
}
