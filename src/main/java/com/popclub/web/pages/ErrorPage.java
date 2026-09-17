package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.popclub.web.constants.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(ErrorPage.class);

    private static final String ERROR_HEADING = "role=heading[name='Something went wrong']";
    private static final String RETRY_BUTTON  = "role=button[name='Retry']";

    public ErrorPage(Page page) {
        this.page = page;
    }

    public ErrorPage navigate(String baseUrl) {
        log.info("Navigating to Error page: {}{}", baseUrl, AppConstants.ERRORS_PAGE);
        page.navigate(baseUrl + AppConstants.ERRORS_PAGE);
        page.waitForLoadState();
        return this;
    }

    public boolean isPageLoaded() {
        page.locator(ERROR_HEADING).waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(ERROR_HEADING).isVisible();
        log.debug("ErrorPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public boolean isRetryButtonVisible() {
        boolean visible = page.locator(RETRY_BUTTON).isVisible();
        log.debug("ErrorPage isRetryButtonVisible: {}", visible);
        return visible;
    }

    public void clickRetry() {
        log.info("Clicking Retry on ErrorPage");
        page.locator(RETRY_BUTTON).waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(RETRY_BUTTON).click();
    }
}
