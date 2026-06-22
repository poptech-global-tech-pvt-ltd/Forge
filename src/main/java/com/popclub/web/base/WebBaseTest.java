package com.popclub.web.base;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.popclub.core.TestContext;
import com.popclub.api.util.WebOnboardingSetup;
import com.popclub.web.constants.AppConstants;
import com.popclub.web.constants.TestCaseId;
import com.popclub.web.factory.PlaywrightFactory;
import com.popclub.web.heal.WebSelfHealingEngine;
import com.popclub.web.utils.ConfigReader;
import com.popclub.web.utils.ScreenshotUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;
import java.util.List;

public class WebBaseTest {

    private static final Logger log = LoggerFactory.getLogger(WebBaseTest.class);

    protected Page page;
    protected String baseUrl;
    protected String testPhone;

    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        page = PlaywrightFactory.initBrowser();
        baseUrl = ConfigReader.get("base.url");
        testPhone = AppConstants.TEST_PHONE;
        page.navigate(baseUrl);
        TestCaseId annotation = method.getAnnotation(TestCaseId.class);
        if (annotation != null) {
            TestContext.setTestCaseIds(List.of(annotation.value()));
        }
        log.info("[WEB] Starting: {}", method.getName());
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        try {
            if (result.getStatus() == ITestResult.FAILURE && PlaywrightFactory.getPage() != null) {
                String path = ScreenshotUtil.capture(result.getName());
                log.info("[WEB] Screenshot saved: {}", path);
            }
            if (result.getStatus() == ITestResult.SUCCESS && isEkycTest(result) && testPhone != null) {
                WebOnboardingSetup.markEkycDone(testPhone);
            }
        } finally {
            PlaywrightFactory.closeBrowser();
        }
        log.info("[WEB] Finished: {} — {}", result.getName(), result.isSuccess() ? "PASSED" : "FAILED");
    }

    /** Override in subclass to return true for tests that complete ekyc. */
    protected boolean isEkycTest(ITestResult result) {
        return false;
    }

    /**
     * Self-healing locator for page-object tests.
     *
     * <p>Tries the selector as-is first. If Playwright throws a timeout/not-found error,
     * {@link WebSelfHealingEngine} scans the live DOM for the closest matching element
     * and retries once with the healed selector.
     *
     * <p>Usage in page objects:
     * <pre>
     *   safeLocate("input[name='first_name']").fill("Deepa");
     *   safeLocate("button:has-text('Continue')").click();
     * </pre>
     *
     * @param selector original CSS / attribute selector
     * @return a Playwright {@link Locator} — either the original or a healed one
     */
    protected Locator safeLocate(String selector) {
        try {
            Locator loc = page.locator(selector);
            loc.waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
            return loc;
        } catch (PlaywrightException e) {
            String healed = WebSelfHealingEngine.tryHeal(page, selector, e);
            if (healed != null) return page.locator(healed);
            throw e;
        }
    }

    /**
     * Returns true if the current page is the YBL "Something went wrong" error page.
     * Detects both forms:
     *   1. Redirect to /errors URL
     *   2. Inline "Something went wrong / We're checking this on our end." text
     */
    protected boolean isYblErrorPage() {
        String url = page.url();
        if (url != null && url.contains("/errors")) return true;
        return page.getByText("Something went wrong").isVisible()
                && page.getByText("We're checking this on our end. Please try again shortly.").isVisible();
    }

    /**
     * Asserts the current page is NOT the YBL error page. Call this after each step.
     */
    protected void assertNotOnErrorPage() {
        if (isYblErrorPage()) {
            String url = page.url();
            String msg = "[WEB] YBL stage env is likely down — " +
                (url != null && url.contains("/errors") ? "redirected to /errors page" :
                    "inline error: 'Something went wrong / We're checking this on our end. Please try again shortly.'") +
                ". Check https://cardstack-sit.popclub.co.in health.";
            log.error(msg);
            throw new AssertionError(msg);
        }
    }

    /**
     * Wraps a test body Runnable. If any exception is thrown and the page is currently
     * showing the YBL error, re-throws with a clear "YBL is down" message.
     * Use this in E2E tests where YBL can go down mid-flow at any step.
     *
     * Usage:
     *   runWithYblCheck(() -> {
     *       // all your test steps here
     *   });
     */
    protected void runWithYblCheck(Runnable testBody) {
        try {
            testBody.run();
        } catch (Throwable t) {
            if (isYblErrorPage()) {
                String msg = "[WEB] YBL stage env is down — test failed mid-flow. " +
                    "Check https://cardstack-sit.popclub.co.in health. Original error: " + t.getMessage();
                log.error(msg);
                throw new AssertionError(msg, t);
            }
            throw t;
        }
    }
}
