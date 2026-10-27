package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Current residential address form — /level-1.
 */
public class AddressPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(AddressPage.class);

    private static final String HEADING       = "[role='heading']:has-text('Current residential address')";
    private static final String ADDR_LINE_1   = "input[name='address_line_1']";
    private static final String ADDR_LINE_2   = "input[name='address_line_2']";
    private static final String ADDR_LINE_3   = "input[name='address_line_3']";
    private static final String POSTAL_CODE   = "input[name='postal_code']";
    private static final String CITY          = "input[name='city']";
    private static final String SAVE_EXIT     = "text=Save & exit";
    private static final String CONTINUE_BTN  = "button:has-text('Continue')";

    public AddressPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(HEADING)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(HEADING).isVisible();
        log.debug("AddressPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public boolean isAddressLine1Visible() { boolean v = page.locator(ADDR_LINE_1).isVisible(); log.debug("AddressPage isAddressLine1Visible: {}", v); return v; }
    public boolean isAddressLine2Visible() { boolean v = page.locator(ADDR_LINE_2).isVisible(); log.debug("AddressPage isAddressLine2Visible: {}", v); return v; }
    public boolean isAddressLine3Visible() { boolean v = page.locator(ADDR_LINE_3).isVisible(); log.debug("AddressPage isAddressLine3Visible: {}", v); return v; }
    public boolean isPostalCodeVisible()   { boolean v = page.locator(POSTAL_CODE).isVisible(); log.debug("AddressPage isPostalCodeVisible: {}", v); return v; }
    public boolean isCityVisible()         { boolean v = page.locator(CITY).isVisible(); log.debug("AddressPage isCityVisible: {}", v); return v; }
    public boolean isSaveExitVisible()     { boolean v = page.locator(SAVE_EXIT).isVisible(); log.debug("AddressPage isSaveExitVisible: {}", v); return v; }

    public AddressPage fillAddressLine1(String value) {
        log.info("Filling address line 1");
        page.locator(ADDR_LINE_1).fill(value);
        return this;
    }

    public AddressPage fillAddressLine2(String value) {
        log.info("Filling address line 2");
        page.locator(ADDR_LINE_2).fill(value);
        return this;
    }

    public AddressPage fillAddressLine3(String value) {
        log.info("Filling address line 3");
        page.locator(ADDR_LINE_3).fill(value);
        return this;
    }

    public AddressPage fillPostalCode(String postalCode) {
        log.info("Filling postal code: {}", postalCode);
        page.locator(POSTAL_CODE).fill(postalCode);
        return this;
    }

    public void clickContinue() {
        log.info("Clicking Continue on AddressPage");
        page.locator(CONTINUE_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(CONTINUE_BTN).click();
    }
}
