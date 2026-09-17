package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The tuition/education-fee payment invite link's OTP verification screen
 * (e.g. https://onboarding.popclub-web.in/verify/INV-XXXX). Six single-digit
 * inputs with no name/id attributes — indexed by position.
 */
public class TuitionInviteOtpPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(TuitionInviteOtpPage.class);

    private static final String OTP_DIGIT_INPUTS = "input[type='tel']";
    private static final String VERIFY_BUTTON    = "button:has-text('Verify OTP')";
    private static final String FEE_HEADING      = "text=education fee of";

    public TuitionInviteOtpPage(Page page) {
        this.page = page;
    }

    public TuitionInviteOtpPage navigate(String inviteUrl) {
        log.info("Navigating to tuition invite link: {}", inviteUrl);
        page.navigate(inviteUrl);
        page.waitForLoadState();
        return this;
    }

    public boolean isPageLoaded() {
        page.locator(OTP_DIGIT_INPUTS).first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(OTP_DIGIT_INPUTS).count() == 6;
        log.debug("TuitionInviteOtpPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public boolean isFeeHeadingVisible() {
        boolean visible = page.locator(FEE_HEADING).isVisible();
        log.debug("TuitionInviteOtpPage isFeeHeadingVisible: {}", visible);
        return visible;
    }

    public TuitionInviteOtpPage fillOtp(String otp) {
        log.info("Filling tuition invite OTP");
        Locator digits = page.locator(OTP_DIGIT_INPUTS);
        for (int i = 0; i < otp.length(); i++) {
            digits.nth(i).fill(String.valueOf(otp.charAt(i)));
        }
        return this;
    }

    public boolean isVerifyButtonEnabled() {
        boolean enabled = page.locator(VERIFY_BUTTON).isEnabled();
        log.debug("TuitionInviteOtpPage isVerifyButtonEnabled: {}", enabled);
        return enabled;
    }

    public void clickVerifyOtp() {
        log.info("Clicking Verify OTP on TuitionInviteOtpPage");
        Locator button = page.locator(VERIFY_BUTTON);
        button.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        button.click();
    }
}
