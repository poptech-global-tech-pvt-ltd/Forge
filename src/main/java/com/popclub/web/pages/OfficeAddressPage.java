package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Office address step — uses an embedded Google Maps picker.
 * Flow: open map modal → search → pick suggestion → confirm → add address → continue.
 */
public class OfficeAddressPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(OfficeAddressPage.class);

    private static final String HEADING             = "[role='heading']:has-text('Office address')";
    private static final String SELECT_ON_MAP_BTN   = "button:has-text('Select location on maps')";
    private static final String MAP_LOADED          = ".gm-style";
    private static final String MAP_SEARCH_BOX      = "[placeholder*='Search for area']";
    private static final String CONFIRM_LOCATION    = "button:has-text('Yes, this is my location')";
    private static final String ADD_ADDRESS_BTN     = "button:has-text('Add Address')";
    private static final String ADDED_FROM_MAPS     = "[role='heading']:has-text('Added from maps')";
    private static final String CONTINUE_BTN        = "button:has-text('Continue')";

    public OfficeAddressPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(HEADING)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(HEADING).isVisible();
        log.debug("OfficeAddressPage isPageLoaded: {}", loaded);
        return loaded;
    }

    /**
     * Opens the map modal, searches for the given query, selects the first suggestion
     * that contains the given suggestion text, confirms the pin and adds the address.
     */
    public OfficeAddressPage selectLocationFromMap(String searchQuery, String suggestionText) {
        log.info("Selecting office location from map: query='{}', suggestion='{}'", searchQuery, suggestionText);
        page.locator(SELECT_ON_MAP_BTN).click();

        page.locator(MAP_LOADED)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

        Locator searchBox = page.locator(MAP_SEARCH_BOX);
        searchBox.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        searchBox.click();
        searchBox.fill(searchQuery);

        page.getByText(suggestionText).first().click();

        page.locator(CONFIRM_LOCATION)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(CONFIRM_LOCATION).click();

        page.locator(ADD_ADDRESS_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(ADD_ADDRESS_BTN).click();

        page.locator(ADDED_FROM_MAPS)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return this;
    }

    public boolean isAddedFromMapsHeadingVisible() {
        boolean visible = page.locator(ADDED_FROM_MAPS).isVisible();
        log.debug("OfficeAddressPage isAddedFromMapsHeadingVisible: {}", visible);
        return visible;
    }

    public void clickContinue() {
        log.info("Clicking Continue on OfficeAddressPage");
        page.locator(CONTINUE_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(CONTINUE_BTN).click();
    }
}
