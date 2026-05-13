package com.popclub.web.actions;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.popclub.core.ElementRepository;
import com.popclub.core.Locator;
import com.popclub.mobile.actions.Action;
import com.popclub.model.Step;
import com.popclub.web.driver.PlaywrightContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WebClickAction implements Action {

    private static final Logger log = LoggerFactory.getLogger(WebClickAction.class);

    @Override
    public void perform(Step step) {
        Page page = PlaywrightContext.getPage();
        String selector = resolveSelector(step);
        ElementHandle el = page.waitForSelector(selector,
                new Page.WaitForSelectorOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
        try {
            el.scrollIntoViewIfNeeded();
            el.click();
        } catch (PlaywrightException e) {
            log.error("[WEB] Native click failed for '{}', falling back to JS click: {}", selector, e.getMessage());
            page.evaluate("el => el.click()", el);
        }
        log.info("[WEB] Clicked: {}", selector);
    }

    private String resolveSelector(Step step) {
        if (step.locators != null && !step.locators.isEmpty()) return toPlaywrightSelector(step.locators.get(0));
        if (step.element != null) return toPlaywrightSelector(ElementRepository.getLocators(step.element, "web").get(0));
        return step.locator;
    }

    private String toPlaywrightSelector(Locator locator) {
        return switch (locator.type.toLowerCase()) {
            case "css"   -> locator.value;
            case "xpath" -> "xpath=" + locator.value;
            case "text"  -> "text=" + locator.value;
            case "id"    -> "#" + locator.value;
            default      -> locator.value;
        };
    }
}
