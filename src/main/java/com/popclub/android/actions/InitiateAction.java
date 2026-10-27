package com.popclub.android.actions;

import com.popclub.android.driver.DriverManager;
import com.popclub.core.TestContext;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;

/**
 * initiate — taps the given trigger element (e.g. the "Accept payment" /
 * "Open now" link in the tutor-onboarding WhatsApp/notification flow) and
 * waits for the OS to bring a browser to the foreground. Use {@code value}
 * to override the expected package (defaults to Chrome).
 *
 * YAML usage:
 *   - action: initiate
 *     element: onboarding_accept_payment_link
 *     value: com.android.chrome   # optional, this is the default
 */
public class InitiateAction implements Action {

    private static final String DEFAULT_BROWSER_PACKAGE = "com.android.chrome";

    @Override
    public void perform(Step step) {
        AppiumDriver driver = DriverManager.getDriver();

        // Reuse TapAction's locator resolution to tap the trigger element.
        new TapAction().perform(step);

        String expectedPackage = step.value != null && !step.value.isBlank()
                ? step.value
                : DEFAULT_BROWSER_PACKAGE;

        int timeoutMs = (step.timeout > 0 ? step.timeout : TestContext.getDefaultTimeout()) * 1000;
        long deadline = System.currentTimeMillis() + timeoutMs;
        String currentPackage = null;

        while (System.currentTimeMillis() < deadline) {
            currentPackage = (String) driver.executeScript("mobile: getCurrentPackage");
            if (expectedPackage.equals(currentPackage)) {
                System.out.println("[Initiate] Browser opened: " + currentPackage);
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        throw new RuntimeException("initiate: expected foreground package '" + expectedPackage
                + "' but was '" + currentPackage + "' after " + (timeoutMs / 1000) + "s");
    }
}
