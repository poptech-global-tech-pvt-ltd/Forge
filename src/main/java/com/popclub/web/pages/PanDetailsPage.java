package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PAN details step — /pan-details.
 * Collects a 10-character PAN number and 6-digit pin code.
 */
public class PanDetailsPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(PanDetailsPage.class);

    private static final String HEADING        = "[role='heading']:has-text('Check your eligibility')";
    private static final String PAN_INPUT      = "input[name='pan_num']";
    private static final String PIN_CODE_INPUT = "input[name='pin_code']";
    private static final String CONTINUE_BTN   = "button:has-text('Continue')";

    public PanDetailsPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(PAN_INPUT)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(PAN_INPUT).isVisible();
        log.debug("PanDetailsPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public boolean isPanInputVisible() {
        boolean visible = page.locator(PAN_INPUT).isVisible();
        log.debug("PanDetailsPage isPanInputVisible: {}", visible);
        return visible;
    }

    public boolean isPinCodeInputVisible() {
        boolean visible = page.locator(PIN_CODE_INPUT).isVisible();
        log.debug("PanDetailsPage isPinCodeInputVisible: {}", visible);
        return visible;
    }

    public PanDetailsPage fillPan(String pan) {
        log.info("Filling PAN number");
        page.locator(PAN_INPUT).fill(pan);
        return this;
    }

    public PanDetailsPage fillPinCode(String pinCode) {
        log.info("Filling pin code: {}", pinCode);
        page.locator(PIN_CODE_INPUT).fill(pinCode);
        return this;
    }

    public void clickContinue() {
        log.info("Clicking Continue on PanDetailsPage");
        page.locator(CONTINUE_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(CONTINUE_BTN).click();
    }
}