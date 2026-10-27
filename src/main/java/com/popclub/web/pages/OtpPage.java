package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.popclub.web.constants.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OtpPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(OtpPage.class);

    private static final String PHONE_INPUT     = "input[name='phone']";
    private static final String CONTINUE_BUTTON = "button:has-text('Continue')";
    private static final String LOGO            = "role=banner >> role=img";
    private static final String HEADING         = "role=heading[name='Check your eligibility']";

    public OtpPage(Page page) {
        this.page = page;
    }

    public OtpPage navigate(String baseUrl) {
        log.info("Navigating to OTP page: {}", baseUrl);
        page.navigate(baseUrl );
        page.waitForLoadState();
        return this;
    }

    public boolean isPageLoaded() {
        page.locator(PHONE_INPUT).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(PHONE_INPUT).isVisible();
        log.debug("OtpPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public boolean isLogoVisible() {
        boolean visible = page.locator(LOGO).isVisible();
        log.debug("OtpPage isLogoVisible: {}", visible);
        return visible;
    }

    public boolean isHeadingVisible() {
        boolean visible = page.locator(HEADING).isVisible();
        log.debug("OtpPage isHeadingVisible: {}", visible);
        return visible;
    }

    public boolean isContinueButtonVisible() {
        boolean visible = page.locator(CONTINUE_BUTTON).first().isVisible();
        log.debug("OtpPage isContinueButtonVisible: {}", visible);
        return visible;
    }

    public OtpPage fillPhone(String phone) {
        // Strip +91 or 91 prefix — the input expects 10-digit number only
        String digits = phone.replaceAll("^\\+?91", "");
        log.info("Filling phone number: {}", digits);
        page.locator(PHONE_INPUT).fill(digits);
        return this;
    }

    public void clickContinue() {
        log.info("Clicking Continue on OtpPage");
        page.locator(CONTINUE_BUTTON).first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(CONTINUE_BUTTON).first().click();
    }
}
