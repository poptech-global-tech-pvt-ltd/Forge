package com.popclub.web.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Personal details form — /level-0.
 * Captures name, date of birth, email, occupation type, marital status and gender.
 */
public class PersonalDetailsPage {

    private final Page page;
    private static final Logger log = LoggerFactory.getLogger(PersonalDetailsPage.class);

    private static final String FIRST_NAME     = "input[name='first_name']";
    private static final String MIDDLE_NAME    = "input[name='middle_name']";
    private static final String LAST_NAME      = "input[name='last_name']";
    private static final String EMAIL          = "input[name='email']";
    private static final String CONTINUE_BTN   = "button:has-text('Continue')";

    // Option labels rendered as plain text nodes
    private static final String OPT_SALARIED      = "text=Salaried";
    private static final String OPT_SELF_EMPLOYED = "text=Self Employed";
    private static final String OPT_MARRIED       = "text=married";
    private static final String OPT_SINGLE        = "text=single";
    private static final String OPT_DIVORCED      = "text=divorced";
    private static final String OPT_WIDOWED       = "text=widowed";
    private static final String OPT_FEMALE        = "text=FEMALE";
    private static final String OPT_MALE          = "text=MALE";
    private static final String OPT_THIRD_GENDER  = "text=THIRDGENDER";

    public PersonalDetailsPage(Page page) {
        this.page = page;
    }

    public boolean isPageLoaded() {
        page.locator(FIRST_NAME)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        boolean loaded = page.locator(FIRST_NAME).isVisible();
        log.debug("PersonalDetailsPage isPageLoaded: {}", loaded);
        return loaded;
    }

    public boolean isFirstNameVisible()   { boolean v = page.locator(FIRST_NAME).isVisible(); log.debug("PersonalDetailsPage isFirstNameVisible: {}", v); return v; }
    public boolean isMiddleNameVisible()  { boolean v = page.locator(MIDDLE_NAME).isVisible(); log.debug("PersonalDetailsPage isMiddleNameVisible: {}", v); return v; }
    public boolean isLastNameVisible()    { boolean v = page.locator(LAST_NAME).isVisible(); log.debug("PersonalDetailsPage isLastNameVisible: {}", v); return v; }
    public boolean isEmailVisible()       { boolean v = page.locator(EMAIL).isVisible(); log.debug("PersonalDetailsPage isEmailVisible: {}", v); return v; }
    public boolean isSalariedVisible()    { boolean v = page.getByText("Salaried").isVisible(); log.debug("PersonalDetailsPage isSalariedVisible: {}", v); return v; }
    public boolean isSelfEmployedVisible(){ boolean v = page.getByText("Self Employed").isVisible(); log.debug("PersonalDetailsPage isSelfEmployedVisible: {}", v); return v; }
    public boolean isMarriedVisible()     { boolean v = page.getByText("married").isVisible(); log.debug("PersonalDetailsPage isMarriedVisible: {}", v); return v; }
    public boolean isFemaleVisible()      { boolean v = page.getByText("FEMALE").isVisible(); log.debug("PersonalDetailsPage isFemaleVisible: {}", v); return v; }
    public boolean isMaleVisible()        { boolean v = page.getByText("MALE", new Page.GetByTextOptions().setExact(true)).isVisible(); log.debug("PersonalDetailsPage isMaleVisible: {}", v); return v; }
    public boolean isThirdGenderVisible() { boolean v = page.getByText("THIRDGENDER").isVisible(); log.debug("PersonalDetailsPage isThirdGenderVisible: {}", v); return v; }

    public PersonalDetailsPage fillFirstName(String firstName) {
        log.info("Filling first name");
        page.locator(FIRST_NAME).fill(firstName);
        return this;
    }

    public PersonalDetailsPage fillMiddleName(String middleName) {
        log.info("Filling middle name");
        page.locator(MIDDLE_NAME).fill(middleName);
        return this;
    }

    public PersonalDetailsPage fillLastName(String lastName) {
        log.info("Filling last name");
        page.locator(LAST_NAME).fill(lastName);
        return this;
    }

    /**
     * Opens the date-picker (4th textbox on the page), selects the year
     * from the year combobox, then clicks the specific day option.
     *
     * @param year           e.g. "1997"
     * @param dateOptionText partial label shown on the calendar day cell,
     *                       e.g. "Choose Tuesday, May 6th,"
     */
    public PersonalDetailsPage selectDateOfBirth(String year, String dateOptionText) {
        log.info("Selecting date of birth: year={}, option='{}'", year, dateOptionText);
        page.getByRole(AriaRole.TEXTBOX).nth(3).click();
        page.getByRole(AriaRole.COMBOBOX).nth(1).selectOption(year);
        page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(dateOptionText)).click();
        return this;
    }

    public PersonalDetailsPage fillEmail(String email) {
        log.info("Filling email");
        page.locator(EMAIL).fill(email);
        return this;
    }

    public PersonalDetailsPage selectSalaried() {
        log.info("Selecting occupation: Salaried");
        page.locator(OPT_SALARIED).click();
        return this;
    }

    public PersonalDetailsPage selectSelfEmployed() {
        log.info("Selecting occupation: Self Employed");
        page.locator(OPT_SELF_EMPLOYED).click();
        return this;
    }

    public PersonalDetailsPage selectMarried() {
        log.info("Selecting marital status: married");
        page.locator(OPT_MARRIED).click();
        return this;
    }

    public PersonalDetailsPage selectSingle() {
        log.info("Selecting marital status: single");
        page.locator(OPT_SINGLE).click();
        return this;
    }

    public PersonalDetailsPage selectFemale() {
        log.info("Selecting gender: FEMALE");
        page.locator(OPT_FEMALE).click();
        return this;
    }

    public PersonalDetailsPage selectMale() {
        log.info("Selecting gender: MALE");
        page.locator(OPT_MALE).click();
        return this;
    }

    public void clickContinue() {
        log.info("Clicking Continue on PersonalDetailsPage");
        page.locator(CONTINUE_BTN)
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(CONTINUE_BTN).click();
    }
}
