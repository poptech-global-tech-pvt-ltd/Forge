package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Additional details step — appears after residential address.
 * Captures the nominee / next-of-kin name.
 */
public class AdditionalDetailsPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(AdditionalDetailsPage.class);

    private static final String HEADING      = "[role='heading']:has-text('Additional details')";
    private static final String CONTINUE_BTN = "button:has-text('Continue')";

    public AdditionalDetailsPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(HEADING)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(HEADING).isVisible();
        log.debug("AdditionalDetailsPage isPageLoaded: {}", loaded);
        return loaded;
    }

    /**
     * Fills the nominee / additional person name field.
     * This page has a single textbox; we target it by role to avoid fragile nth-child selectors.
     */
    public AdditionalDetailsPage fillNomineeName(String name) {
        log.info("Filling nominee name");
        Locator textbox = page.getByRole(AriaRole.TEXTBOX);
        textbox.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        textbox.fill(name);
        return this;
    }

    public void clickContinue() {
        log.info("Clicking Continue on AdditionalDetailsPage");
        page.locator(CONTINUE_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(CONTINUE_BTN).click();
    }
}
