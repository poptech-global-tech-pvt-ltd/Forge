package com.popclub.web.actions;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.popclub.core.ElementRepository;
import com.popclub.core.Locator;
import com.popclub.mobile.actions.Action;
import com.popclub.model.Step;
import com.popclub.web.driver.PlaywrightContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebFillAction implements Action {

    private static final Logger log = LoggerFactory.getLogger(WebFillAction.class);

    @Override
    public void perform(Step step) {
        Page page = PlaywrightContext.getPage();
        String selector = resolveSelector(step);
        ElementHandle el = page.waitForSelector(selector,
                new Page.WaitForSelectorOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
        el.fill(step.value != null ? step.value : "");
        log.info("[WEB] Filled: {} = {}", selector, step.value);
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
