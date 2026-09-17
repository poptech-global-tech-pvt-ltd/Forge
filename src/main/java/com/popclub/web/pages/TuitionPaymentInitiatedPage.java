package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The /acc-{sellerId} success screen shown after a tuition fee payment is accepted.
 */
public class TuitionPaymentInitiatedPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(TuitionPaymentInitiatedPage.class);

    private static final String SUCCESS_HEADING = "text=Education fee";
    private static final String CREDIT_NOTICE   = "text=Money will be credited within 24 hours";
    private static final String DOWNLOAD_BUTTON = "button:has-text('Download POP')";

    public TuitionPaymentInitiatedPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(SUCCESS_HEADING).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(SUCCESS_HEADING).isVisible() && page.locator(CREDIT_NOTICE).isVisible();
        log.debug("TuitionPaymentInitiatedPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public boolean isDownloadButtonVisible() {
        boolean visible = page.locator(DOWNLOAD_BUTTON).isVisible();
        log.debug("TuitionPaymentInitiatedPage isDownloadButtonVisible: {}", visible);
        return visible;
    }
}
