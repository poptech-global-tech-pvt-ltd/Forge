package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aadhaar verification step — /level-5.
 * Clicking Continue redirects to the external YES Bank consent URL.
 */
public class AadhaarPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(AadhaarPage.class);

    private static final String HEADING          = "[role='heading']:has-text('Complete your KYC')";
    private static final String YES_BANK_INFO    = "text=Keep your Aadhaar details handy";
    private static final String CONTINUE_BTN     = "button:has-text('Start'), button:has-text('Continue')";

    public AadhaarPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(HEADING)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(HEADING).isVisible();
        log.debug("AadhaarPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public boolean isYesBankInfoVisible() {
        boolean visible = page.locator(YES_BANK_INFO).isVisible();
        log.debug("AadhaarPage isYesBankInfoVisible: {}", visible);
        return visible;
    }

    public void clickContinue() {
        log.info("Clicking Continue on AadhaarPage");
        page.locator(CONTINUE_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(CONTINUE_BTN).click();
    }
}
