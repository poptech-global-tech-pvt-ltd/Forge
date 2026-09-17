package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Office / employment details step.
 * Captures company name, company type (dropdown), designation, job-title category (dropdown)
 * and annual income.
 */
public class OfficeDetailsPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(OfficeDetailsPage.class);

    private static final String HEADING            = "[role='heading']:has-text('Office details')";
    private static final String COMPANY_NAME       = "input[name='company_name']";
    private static final String COMPANY_TYPE_DDL   = ".selected-option";
    private static final String DESIGNATION        = "input[name='designation']";
    private static final String JOB_TITLE_DDL      =
            "div:nth-child(5) > .dropdown-wrapper > .dropdown-container > .selected-option";
    private static final String ANNUAL_INCOME      = "input[name='annual_income']";
    private static final String CONTINUE_BTN       = "button:has-text('Continue')";

    public OfficeDetailsPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(HEADING)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(HEADING).isVisible();
        log.debug("OfficeDetailsPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public boolean isCompanyNameVisible() { boolean v = page.locator(COMPANY_NAME).isVisible(); log.debug("OfficeDetailsPage isCompanyNameVisible: {}", v); return v; }

    public OfficeDetailsPage fillCompanyName(String companyName) {
        log.info("Filling company name");
        page.locator(COMPANY_NAME).fill(companyName);
        return this;
    }

    /**
     * Opens the company-type dropdown and selects the given option text.
     */
    public OfficeDetailsPage selectCompanyType(String optionText) {
        log.info("Selecting company type: {}", optionText);
        page.locator(COMPANY_TYPE_DDL).first().click();
        page.getByText(optionText).click();
        return this;
    }

    public OfficeDetailsPage fillDesignation(String designation) {
        log.info("Filling designation");
        page.locator(DESIGNATION).fill(designation);
        return this;
    }

    /**
     * Opens the job-title (5th dropdown) and selects the given option text.
     */
    public OfficeDetailsPage selectJobTitle(String optionText) {
        log.info("Selecting job title: {}", optionText);
        page.locator(JOB_TITLE_DDL).click();
        page.getByText(optionText).click();
        return this;
    }

    public OfficeDetailsPage fillAnnualIncome(String income) {
        log.info("Filling annual income");
        page.locator(ANNUAL_INCOME).fill(income);
        return this;
    }

    public void clickContinue() {
        log.info("Clicking Continue on OfficeDetailsPage");
        page.locator(CONTINUE_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(CONTINUE_BTN).click();
    }
}
