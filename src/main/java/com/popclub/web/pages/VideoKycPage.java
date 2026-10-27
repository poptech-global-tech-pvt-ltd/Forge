package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Video KYC page — /level-6.
 *
 * <p>Displays a summary of the applicant's data before initiating a live video call.
 * Camera/microphone permissions must be granted at the BrowserContext level before this page loads:
 * {@code context.grantPermissions(List.of("camera", "microphone"))}.
 */
public class VideoKycPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(VideoKycPage.class);

    private static final String VKYC_HEADING        = "text=v-KYC Details";
    private static final String EXPAND_ICON         = "img";
    private static final String PROCEED_INFO        = "text=You can now proceed with";
    private static final String START_VKYC_BTN      = "button:has-text('START VIDEO KYC')";
    private static final String PROCEED_VKYC_BTN    = "button:has-text('Proceed to Video KYC')";
    private static final String ALLOW_ACCESS_BTN    = "button:has-text('Allow Access')";

    public VideoKycPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(VKYC_HEADING)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(VKYC_HEADING).isVisible();
        log.debug("VideoKycPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public VideoKycPage expandDetails() {
        log.info("Expanding details on VideoKycPage");
        page.locator(EXPAND_ICON).first().click();
        page.locator(PROCEED_INFO)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return this;
    }

    public boolean isStartVideoKycButtonVisible() {
        boolean visible = page.locator(START_VKYC_BTN).isVisible();
        log.debug("VideoKycPage isStartVideoKycButtonVisible: {}", visible);
        return visible;
    }

    public VideoKycPage clickStartVideoKyc() {
        log.info("Clicking Start Video KYC on VideoKycPage");
        page.locator(START_VKYC_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(START_VKYC_BTN).click();
        return this;
    }

    public boolean isProceedToVideoKycVisible() {
        boolean visible = page.locator(PROCEED_VKYC_BTN).isVisible();
        log.debug("VideoKycPage isProceedToVideoKycVisible: {}", visible);
        return visible;
    }

    public VideoKycPage clickProceedToVideoKyc() {
        log.info("Clicking Proceed to Video KYC on VideoKycPage");
        page.locator(PROCEED_VKYC_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(PROCEED_VKYC_BTN).click();
        return this;
    }

    public void clickAllowAccess() {
        log.info("Clicking Allow Access on VideoKycPage");
        page.locator(ALLOW_ACCESS_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(ALLOW_ACCESS_BTN).click();
    }
}
