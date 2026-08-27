package com.popclub.android.actions;

import com.popclub.api.auth.TokenExtractor;
import com.popclub.core.TestContext;
import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/** loginIfNeeded — logs in via OTP if the login screen is visible; no-op if already logged in. */
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

        TestContext.setLoginPhone(phone);
        TestContext.setLoginOtp(otp);

        boolean loginScreenVisible = isVisible(driver, PHONE_INPUT_TAG, QUICK_TIMEOUT_MS);

        if (loginScreenVisible) {
            System.out.println("[loginIfNeeded] Login screen detected — performing login…");
            doLogin(driver, phone, otp);
            captureTokenFromLogcat();   // reads jwt_access_token from OkHttp logcat
        } else {
            System.out.println("[loginIfNeeded] No login required — already on home screen.");
            // App is already logged in — token won't be in logcat (no recent login response).
            // Try DataStore / SharedPrefs fallbacks so the token is available for API calls.
            captureTokenFromDevice();
        }

        System.out.println("[loginIfNeeded] ✅ Login step complete");
    }

    private void doLogin(AppiumDriver driver, String phone, String otp) throws Exception {
        WebElement phoneInput = waitFor(driver, PHONE_INPUT_TAG, LOGIN_TIMEOUT_SEC);
        phoneInput.click();
        typeIntoFocusedField(driver, phone);

        WebElement continueBtn = waitFor(driver, CONTINUE_BTN_TAG, LOGIN_TIMEOUT_SEC);
        continueBtn.click();

        WebElement otpInput = waitFor(driver, OTP_INPUT_TAG, LOGIN_TIMEOUT_SEC);
        otpInput.click();
        typeIntoFocusedField(driver, otp);
        new WebDriverWait(driver, Duration.ofSeconds(LOGIN_TIMEOUT_SEC))
                .until(ExpectedConditions.presenceOfElementLocated(
                        AppiumBy.accessibilityId(HOME_TAB_TAG)));

        System.out.println("[loginIfNeeded] ✅ Login succeeded — home screen visible");
    }

    /** Types into the focused field; falls back to mobile: type for Compose TextFields. */
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

    /** Returns true if the accessibilityId element appears within timeoutMs. */
    private boolean isVisible(AppiumDriver driver, String tag, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                List<WebElement> found = driver.findElements(AppiumBy.accessibilityId(tag));
                if (!found.isEmpty() && found.get(0).isDisplayed()) return true;
            } catch (Exception ignored) {}
            sleep(100);
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

    private void captureTokenFromLogcat() {
        storeToken(getDeviceSerial(), "logcat/DataStore/SharedPrefs");
    }

    private void captureTokenFromDevice() {
        storeToken(getDeviceSerial(), "DataStore/SharedPrefs (login skipped)");
    }

    private String getDeviceSerial() {
        try {
            var info = AppiumDriverManager.getDeviceInfo();
            return (info != null) ? info.udid : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private void storeToken(String deviceSerial, String source) {
        try {
            TokenExtractor.clearCache();
            TestContext.setUserToken(null);
            TestContext.setLegacyToken(null);
            String token = TokenExtractor.get(deviceSerial);
            if (token != null && !token.isBlank()) {
                TestContext.setUserToken(token);
                TestContext.setScalarData("auth_token", token);
                String legacy = TokenExtractor.getLegacyToken(deviceSerial);
                if (legacy != null && !legacy.isBlank()) {
                    TestContext.setLegacyToken(legacy);
                    TestContext.setScalarData("legacy_token", legacy);
                    System.out.println("  ✅ [loginIfNeeded] Legacy DRF token captured");
                }
                String preview = token.length() > 20
                        ? token.substring(0, 10) + "…" + token.substring(token.length() - 6)
                        : token;
                System.out.println("  ✅ [loginIfNeeded] Token captured via " + source + ": " + preview);
            } else {
                System.out.println("  ⚠️  [loginIfNeeded] Token not found via " + source);
            }
        } catch (Exception e) {
            System.out.println("  ⚠️  [loginIfNeeded] Token capture failed (" + source + "): " + e.getMessage());
        }
    }

}
