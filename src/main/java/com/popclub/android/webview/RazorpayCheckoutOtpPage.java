package com.popclub.android.webview;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

/**
 * Page object for the Razorpay-hosted 3DS/OTP checkout screen that opens in
 * Chrome during tuition-fee card payment. This is an external Razorpay-hosted
 * page (not a webview inside the POP app), so it drives its own standalone
 * Appium Chrome session rather than switching context on the app's driver.
 *
 * Usage:
 *   try (RazorpayCheckoutOtpPage checkout = new RazorpayCheckoutOtpPage(deviceSerial)) {
 *       checkout.open(paymentLink).enterOtp(otp).submit();
 *   }
 */
public class RazorpayCheckoutOtpPage implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RazorpayCheckoutOtpPage.class);
    private static final String CHROME_PACKAGE  = "com.android.chrome";
    private static final String CHROME_ACTIVITY = "com.google.android.apps.chrome.Main";
    private static final String APPIUM_SERVER_URL = "http://localhost:4723";

    private final AndroidDriver driver;

    public RazorpayCheckoutOtpPage(String deviceSerial) throws MalformedURLException {
        String effectiveSerial = (deviceSerial == null || deviceSerial.isBlank()) ? "*" : deviceSerial;
        log.info("Using device: {}", effectiveSerial);

        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setDeviceName(effectiveSerial);
        options.setAutomationName("UiAutomator2");
        options.setAutoGrantPermissions(true);
        options.setNoReset(true);
        options.setAppPackage(CHROME_PACKAGE);
        options.setAppActivity(CHROME_ACTIVITY);

        this.driver = new AndroidDriver(new URL(APPIUM_SERVER_URL), options);
        log.info("Appium session created for Razorpay checkout OTP page");
    }

    public RazorpayCheckoutOtpPage open(String paymentLink) {
        driver.get(paymentLink);
        log.info("Navigated to payment link");
        sleep(2500); // allow the checkout page to finish loading
        return this;
    }

    /**
     * Interacts via the default NATIVE_APP context rather than switching into the
     * Chrome WebView context. Switching contexts requires Appium to spin up a
     * Chromedriver matching the device's Chrome build, which this environment
     * doesn't have available — but UiAutomator2 (via the accessibility tree) can
     * already see the rendered OTP field and Submit button as native-looking
     * elements, so no WebView context switch is needed.
     */
    public RazorpayCheckoutOtpPage enterOtp(String otp) {
        WebElement otpInput = findOtpInput();
        otpInput.clear();
        otpInput.sendKeys(otp);
        log.info("Entered OTP");
        return this;
    }

    public void submit() {
        sleep(500);
        findSubmitButton().click();
        log.info("Clicked submit button");
        sleep(3000); // allow payment processing to settle before the session is torn down
    }

    private WebElement findOtpInput() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        try {
            return wait.until(ExpectedConditions.presenceOfElementLocated(By.className("android.widget.EditText")));
        } catch (Exception byClass) {
            return driver.findElement(By.xpath(
                    "//*[contains(translate(@text, 'OTP', 'otp'), 'otp') or contains(translate(@hint, 'OTP', 'otp'), 'otp')]"));
        }
    }

    private WebElement findSubmitButton() {
        try {
            return driver.findElement(By.xpath("//*[@text='Submit' or @text='Verify']"));
        } catch (Exception e) {
            return driver.findElement(By.xpath(
                    "//*[contains(translate(@text, 'SUBMITVERIFY', 'submitverify'), 'submit') or contains(translate(@text, 'SUBMITVERIFY', 'submitverify'), 'verify')]"));
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        try {
            driver.quit();
            log.info("Appium session closed");
        } catch (Exception e) {
            log.warn("Error closing driver: {}", e.getMessage());
        }
    }
}
