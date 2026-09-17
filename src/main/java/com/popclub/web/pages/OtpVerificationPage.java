package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the OTP verification step that appears after accepting Terms & Conditions.
 * The user enters the 6-digit OTP received on their phone.
 */
public class OtpVerificationPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(OtpVerificationPage.class);

    private static final String OTP_INPUT    = "input[name='otp']";
    private static final String RESEND_LINK  = "a:has-text('Resend OTP')";

    public OtpVerificationPage(Page page) {
        this.page = page;
    }

    public boolean isOtpInputVisible() {
        page.locator(OTP_INPUT)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean visible = page.locator(OTP_INPUT).isVisible();
        log.debug("OtpVerificationPage isOtpInputVisible: {}", visible);
        return visible;
    }

    public boolean isResendOtpLinkVisible() {
        boolean visible = page.locator(RESEND_LINK).isVisible();
        log.debug("OtpVerificationPage isResendOtpLinkVisible: {}", visible);
        return visible;
    }

    public OtpVerificationPage fillOtp(String otp) {
        log.info("Filling OTP on OtpVerificationPage");
        page.locator(OTP_INPUT).click();
        page.locator(OTP_INPUT).fill(otp);
        return this;
    }
}