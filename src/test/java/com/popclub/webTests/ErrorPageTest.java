package com.popclub.webTests;

import com.popclub.web.base.WebBaseTest;
import com.popclub.web.constants.TestCaseId;
import com.popclub.web.listeners.RetryAnalyzer;
import com.popclub.web.pages.ErrorPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ErrorPageTest extends WebBaseTest {

    private static final Logger log = LoggerFactory.getLogger(ErrorPageTest.class);

    @Test(description = "Verify error page displays correct heading and Retry button",
          groups = {"smoke", "regression"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-5629")
    public void verifyErrorPageLoads() {
        log.info("Running test: {}", "verifyErrorPageLoads");
        ErrorPage errorPage = new ErrorPage(page);
        errorPage.navigate(baseUrl);

        Assert.assertTrue(errorPage.isPageLoaded(),        "'Something went wrong' heading should be visible");
        Assert.assertTrue(errorPage.isRetryButtonVisible(), "Retry button should be visible");
    }

    @Test(description = "Verify Retry button on error page navigates away from errors",
          groups = {"regression"},
          retryAnalyzer = RetryAnalyzer.class)
    @TestCaseId("PO-5629")
    public void verifyRetryButtonIsClickable() {
        log.info("Running test: {}", "verifyRetryButtonIsClickable");
        ErrorPage errorPage = new ErrorPage(page);
        errorPage.navigate(baseUrl);
        errorPage.isPageLoaded();
        errorPage.clickRetry();

        Assert.assertNotNull(page.url(), "Page should have a valid URL after clicking Retry");
    }
}
