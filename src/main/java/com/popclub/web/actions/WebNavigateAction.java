package com.popclub.web.actions;

import com.microsoft.playwright.Page;
import com.popclub.android.actions.Action;
import com.popclub.model.Step;
import com.popclub.web.driver.PlaywrightContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebNavigateAction implements Action {

    private static final Logger log = LoggerFactory.getLogger(WebNavigateAction.class);

    @Override
    public void perform(Step step) {
        Page page = PlaywrightContext.getPage();
        String url = step.value != null ? step.value : step.locator;
        log.info("[WEB] Navigating to: {}", url);
        page.navigate(url);
        page.waitForLoadState();
        log.info("[WEB] Navigated to: {}", url);
    }
}
