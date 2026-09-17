package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The /receive-payment confirmation screen — shown after OTP verification.
 * Tutor reviews payer/payee details and accepts the Seller T&C to receive the fee.
 */
public class TuitionReceivePaymentPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(TuitionReceivePaymentPage.class);

    private static final String RECEIVING_TEXT  = "text=Receiving from your student";
    private static final String SELLER_ID_LABEL = "text=Seller ID";
    private static final String TERMS_CHECKBOX  = "input[type='checkbox']";
    private static final String CONFIRM_BUTTON  = "button:has-text('Confirm & receive')";
    private static final String REJECT_LINK     = "text=Reject this payment";

    public TuitionReceivePaymentPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(RECEIVING_TEXT).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(RECEIVING_TEXT).isVisible();
        log.debug("TuitionReceivePaymentPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public boolean isSellerIdVisible() {
        boolean visible = page.locator(SELLER_ID_LABEL).isVisible();
        log.debug("TuitionReceivePaymentPage isSellerIdVisible: {}", visible);
        return visible;
    }

    public TuitionReceivePaymentPage acceptTerms() {
        log.info("Checking Seller T&C consent");
        Locator checkbox = page.locator(TERMS_CHECKBOX);
        checkbox.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        if (!checkbox.isChecked()) checkbox.check();
        return this;
    }

    public boolean isConfirmButtonEnabled() {
        boolean enabled = page.locator(CONFIRM_BUTTON).isEnabled();
        log.debug("TuitionReceivePaymentPage isConfirmButtonEnabled: {}", enabled);
        return enabled;
    }

    public void clickConfirmAndReceive() {
        log.info("Clicking Confirm & receive");
        Locator button = page.locator(CONFIRM_BUTTON);
        button.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        button.click();
    }

    public void clickRejectPayment() {
        log.info("Clicking Reject this payment");
        page.locator(REJECT_LINK).click();
    }
}
