package com.popclub.web.base;

import com.microsoft.playwright.Page;
import com.popclub.core.TestContext;
import com.popclub.api.util.WebOnboardingSetup;
import com.popclub.web.constants.AppConstants;
import com.popclub.web.constants.TestCaseId;
import com.popclub.web.factory.PlaywrightFactory;
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
}
