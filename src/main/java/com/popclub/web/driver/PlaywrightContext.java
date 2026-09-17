package com.popclub.web.driver;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaywrightContext {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightContext.class);

    private static final ThreadLocal<Playwright> playwright = new ThreadLocal<>();
    private static final ThreadLocal<Browser> browser       = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> context = new ThreadLocal<>();
    private static final ThreadLocal<Page> page             = new ThreadLocal<>();

    public static Page getPage() {
        if (page.get() == null) launch();
        return page.get();
    }

    private static void launch() {
        log.info("[PlaywrightContext] Initializing browser (chromium, headless=false)");
        Playwright pw = Playwright.create();
        playwright.set(pw);

        Browser b = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        browser.set(b);

        BrowserContext ctx = b.newContext();
        context.set(ctx);

        page.set(ctx.newPage());
        log.info("[PlaywrightContext] Browser initialized successfully");
    }

    public static void close() {
        log.info("[PlaywrightContext] Closing browser");
        try {
            if (page.get() != null)       page.get().close();
            if (context.get() != null)    context.get().close();
            if (browser.get() != null)    browser.get().close();
            if (playwright.get() != null) playwright.get().close();
        } finally {
            page.remove();
            context.remove();
            browser.remove();
            playwright.remove();
        }
    }
}
