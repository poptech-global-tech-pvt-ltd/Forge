package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TermsConditionsPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(TermsConditionsPage.class);

    private static final String HEADING           = "role=heading[name='Terms & Conditions']";
    private static final String TANDC_CHECKBOX    = "input[name='TandC']";
    private static final String CALL_SMS_CHECKBOX = "input[name='call-sms-email']";
    private static final String PAN_CHECKBOX      = "input[name='PAN']";
    private static final String CONSENT_TOGGLE    = "form svg, form img[alt=''], form button[aria-expanded], .consent-toggle, [class*='toggle'], [class*='expand']";
    private static final String CONTINUE_BUTTON   = "button:has-text('Continue')";

    public TermsConditionsPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(HEADING).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(HEADING).isVisible();
        log.debug("TermsConditionsPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public boolean isTandCVisible() {
        boolean visible = page.locator(TANDC_CHECKBOX).isVisible();
        log.debug("TermsConditionsPage isTandCVisible: {}", visible);
        return visible;
    }

    public boolean isCallSmsConsentVisible() {
        boolean visible = page.locator(CALL_SMS_CHECKBOX).isVisible();
        log.debug("TermsConditionsPage isCallSmsConsentVisible: {}", visible);
        return visible;
    }

    public boolean isPanConsentVisible() {
        boolean visible = page.locator(PAN_CHECKBOX).isVisible();
        log.debug("TermsConditionsPage isPanConsentVisible: {}", visible);
        return visible;
    }

    public TermsConditionsPage expandConsentSection() {
        log.info("Expanding consent section on TermsConditionsPage");
        page.locator(CONSENT_TOGGLE).first().click();
        return this;
    }

    public TermsConditionsPage checkTandC() {
        log.info("Checking Terms & Conditions checkbox");
        Locator checkbox = page.locator(TANDC_CHECKBOX);
        checkbox.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        if (!checkbox.isChecked()) checkbox.check();
        return this;
    }

    public void clickContinue() {
        log.info("Clicking Continue on TermsConditionsPage");
        page.locator(CONTINUE_BUTTON).waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(CONTINUE_BUTTON).click();
    }
}
