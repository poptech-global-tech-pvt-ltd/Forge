package com.popclub.web.actions;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.popclub.core.ElementRepository;
import com.popclub.core.Locator;
import com.popclub.android.actions.Action;
import com.popclub.model.Step;
import com.popclub.web.driver.PlaywrightContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebAssertTextAction implements Action {

    private static final Logger log = LoggerFactory.getLogger(WebAssertTextAction.class);

    @Override
    public void perform(Step step) {
        Page page = PlaywrightContext.getPage();
        String selector = resolveSelector(step);
        ElementHandle el = page.waitForSelector(selector,
                new Page.WaitForSelectorOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
        String actual = el.textContent().trim();
        String expected = step.value != null ? step.value.trim() : "";
        if (!actual.contains(expected)) {
            throw new RuntimeException("[WEB] Text mismatch on " + selector
                    + " — expected: [" + expected + "] actual: [" + actual + "]");
        }
        log.info("[WEB] Asserted text on {}: {}", selector, actual);
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
