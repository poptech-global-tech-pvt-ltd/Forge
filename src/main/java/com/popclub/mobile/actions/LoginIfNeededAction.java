package com.popclub.mobile.actions;

import com.popclub.clp.TokenExtractor;
import com.popclub.core.TestContext;
import com.popclub.mobile.driver.AppiumDriverManager;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 * LoginIfNeededAction — performs login only if the login screen is visible.
 *
 * If the app is already logged in (home screen showing), this action is a no-op.
 *
 * If the login screen IS visible, it completes the full OTP login flow:
 *   enter phone → tap continue → enter OTP → wait for home_tab
 *
 * This makes tests work reliably with noReset: true regardless of whether
 * a previous test run left the app logged in or logged out.
 *
 * YAML usage (replaces the 6 individual login steps):
 *
 *   - action: loginIfNeeded
 *     value: "1234561122"   ← phone number
 *     text:  "560102"       ← OTP
 */
public class LoginIfNeededAction implements Action {

    private static final String PHONE_INPUT_TAG  = "login_phone_input";
    private static final String CONTINUE_BTN_TAG = "login_continue_button";
    private static final String OTP_INPUT_TAG    = "login_otp_input";
    private static final String HOME_TAB_TAG     = "Home";

    private static final long QUICK_TIMEOUT_MS = 4_000;
    private static final long LOGIN_TIMEOUT_SEC = 20;

    @Override
    public void perform(Step step) {
        try {
            performInternal(step);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void performInternal(Step step) throws Exception {
        // If the test declares loginRequired: false, skip the entire login flow.
        if (!TestContext.isLoginRequired()) {
            System.out.println("[loginIfNeeded] loginRequired=false — skipping login");
            return;
        }

        AppiumDriver driver = DriverManager.getDriver();

        String phone = step.value != null ? step.value.trim() : "1234561122";
        String otp   = step.text  != null ? step.text.trim()  : "560102";

        boolean loginScreenVisible = isVisible(driver, PHONE_INPUT_TAG, QUICK_TIMEOUT_MS);

        if (loginScreenVisible) {
            System.out.println("[loginIfNeeded] Login screen detected — performing login…");
            doLogin(driver, phone, otp);
        } else {
            System.out.println("[loginIfNeeded] No login required — already on home screen.");
        }

        // Capture the token the app stored after login (SharedPreferences)
        captureTokenFromDevice();

        System.out.println("[loginIfNeeded] ✅ Login step complete");
    }

    // ── UI login flow ─────────────────────────────────────────────────────────

    private void doLogin(AppiumDriver driver, String phone, String otp) throws Exception {
        // Enter phone number
        WebElement phoneInput = waitFor(driver, PHONE_INPUT_TAG, LOGIN_TIMEOUT_SEC);
        phoneInput.click();
        sleep(800);
        typeIntoFocusedField(driver, phone);

        // Tap continue
        WebElement continueBtn = waitFor(driver, CONTINUE_BTN_TAG, LOGIN_TIMEOUT_SEC);
        continueBtn.click();
        sleep(1200);

        // Enter OTP
        WebElement otpInput = waitFor(driver, OTP_INPUT_TAG, LOGIN_TIMEOUT_SEC);
        otpInput.click();
        sleep(800);
        typeIntoFocusedField(driver, otp);

        // Wait for home_tab to confirm login succeeded
        System.out.println("[loginIfNeeded] OTP entered — waiting for home screen…");
        new WebDriverWait(driver, Duration.ofSeconds(LOGIN_TIMEOUT_SEC))
                .until(ExpectedConditions.presenceOfElementLocated(
                        AppiumBy.accessibilityId(HOME_TAB_TAG)));

        System.out.println("[loginIfNeeded] ✅ Login succeeded — home screen visible");
    }

    /**
     * Types text into the currently-focused field.
     * Mirrors EnterTextAction: find focused android.widget.EditText,
     * or fall back to mobile: type for Compose TextFields.
     */
    private void typeIntoFocusedField(AppiumDriver driver, String text) {
        try {
            WebElement editText = driver.findElement(
                    AppiumBy.xpath("//android.widget.EditText[@focused='true']"));
            editText.clear();
            editText.sendKeys(text);
        } catch (Exception ex) {
            // Compose TextField — dispatch key events to whatever is focused
            driver.executeScript("mobile: type", java.util.Map.of("text", text));
        }
        try {
            driver.executeScript("mobile: hideKeyboard");
        } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns true if the accessibilityId element appears within timeoutMs. */
    private boolean isVisible(AppiumDriver driver, String tag, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                List<WebElement> found = driver.findElements(AppiumBy.accessibilityId(tag));
                if (!found.isEmpty() && found.get(0).isDisplayed()) return true;
            } catch (Exception ignored) {}
            sleep(300);
        }
        return false;
    }

    private WebElement waitFor(AppiumDriver driver, String tag, long timeoutSec)
            throws Exception {
        return new WebDriverWait(driver, Duration.ofSeconds(timeoutSec))
                .until(ExpectedConditions.visibilityOfElementLocated(
                        AppiumBy.accessibilityId(tag)));
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    /** Read the token the app stored in SharedPreferences after login and save to TestContext. */
    private void captureTokenFromDevice() {
        if (TestContext.getUserToken() != null && !TestContext.getUserToken().isBlank()) {
            return; // already captured
        }
        try {
            String deviceSerial = "";
            try {
                var info = AppiumDriverManager.getDeviceInfo();
                if (info != null) deviceSerial = info.udid;
            } catch (Exception ignored) {}

            String token = TokenExtractor.get(deviceSerial);
            if (token != null && !token.isBlank()) {
                TestContext.setUserToken(token);
                TestContext.setScalarData("auth_token", token);
                String preview = token.length() > 20
                        ? token.substring(0, 10) + "…" + token.substring(token.length() - 6)
                        : token;
                System.out.println("  🔑 Token captured: " + preview);
            }
        } catch (Exception e) {
            System.out.println("  ⚠️  [loginIfNeeded] Could not capture token: " + e.getMessage());
        }
    }
}
