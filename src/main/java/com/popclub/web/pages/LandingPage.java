package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LandingPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(LandingPage.class);

    private static final String APPLY_NOW_BUTTON = "button:has-text('APPLY NOW')";
    private static final String ELIGIBILITY_TEXT  = "text=Eligibility";

    public LandingPage(Page page) {
        this.page = page;
    }

    public LandingPage navigate(String baseUrl) {
        log.info("Navigating to Landing page: {}", baseUrl);
        page.navigate(baseUrl);
        page.waitForLoadState();
        return this;
    }

    public boolean isApplyNowVisible() {
        page.locator(APPLY_NOW_BUTTON).first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean visible = page.locator(APPLY_NOW_BUTTON).first().isVisible();
        log.debug("LandingPage isApplyNowVisible: {}", visible);
        return visible;
    }

    public LandingPage clickApplyNow() {
        log.info("Clicking Apply Now on LandingPage");
        page.locator(APPLY_NOW_BUTTON).first().click();
        return this;
    }

    public boolean isEligibilityTextVisible() {
        page.locator(ELIGIBILITY_TEXT).first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean visible = page.locator(ELIGIBILITY_TEXT).first().isVisible();
        log.debug("LandingPage isEligibilityTextVisible: {}", visible);
        return visible;
    }
}
