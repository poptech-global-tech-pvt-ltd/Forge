package com.popclub.ios.actions;

import com.popclub.android.actions.Action;
import com.popclub.android.driver.DriverManager;
import com.popclub.api.auth.TokenExtractor;
import com.popclub.core.TestContext;
import com.popclub.ios.driver.IOSDriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/** loginIfNeeded for iOS — logs in via OTP if the login screen is visible; no-op if already logged in. */
public class IOSLoginIfNeededAction implements Action {

    private static final String PHONE_INPUT_TAG  = "login_phone_input";
    private static final String CONTINUE_BTN_TAG = "login_continue_button";
    private static final String OTP_INPUT_TAG    = "login_otp_input";
    private static final String HOME_TAB_TAG     = "Home";

    private static final long QUICK_TIMEOUT_MS  = 4_000;
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
        if (!TestContext.isLoginRequired()) {
            System.out.println("[IOSLoginIfNeeded] loginRequired=false — skipping login");
            return;
        }

        AppiumDriver driver = DriverManager.getDriver();

        String phone = step.value != null ? step.value.trim() : "1234561122";
        String otp   = step.text  != null ? step.text.trim()  : "560102";

        TestContext.setLoginPhone(phone);
        TestContext.setLoginOtp(otp);

        boolean loginScreenVisible = isVisible(driver, PHONE_INPUT_TAG, QUICK_TIMEOUT_MS);

        if (loginScreenVisible) {
            System.out.println("[IOSLoginIfNeeded] Login screen detected — performing login…");
            doLogin(driver, phone, otp);
            captureToken("iOS/DataStore");
        } else {
            System.out.println("[IOSLoginIfNeeded] No login required — already on home screen.");
            captureToken("DataStore/Keychain (login skipped)");
        }

        // iOS system alerts (notifications, contacts, etc.) can appear after login — dismiss them.
        dismissSystemAlerts(driver);

        System.out.println("[IOSLoginIfNeeded] ✅ Login step complete");
    }

    private void doLogin(AppiumDriver driver, String phone, String otp) throws Exception {
        WebElement phoneInput = waitFor(driver, PHONE_INPUT_TAG, LOGIN_TIMEOUT_SEC);
        phoneInput.click();
        typeText(driver, phone);

        WebElement continueBtn = waitFor(driver, CONTINUE_BTN_TAG, LOGIN_TIMEOUT_SEC);
        continueBtn.click();

        WebElement otpInput = waitFor(driver, OTP_INPUT_TAG, LOGIN_TIMEOUT_SEC);
        otpInput.click();
        typeText(driver, otp);

        new WebDriverWait(driver, Duration.ofSeconds(LOGIN_TIMEOUT_SEC))
                .until(ExpectedConditions.presenceOfElementLocated(
                        AppiumBy.accessibilityId(HOME_TAB_TAG)));

        System.out.println("[IOSLoginIfNeeded] ✅ Login succeeded — home screen visible");
    }

    /**
     * Types text using the XCUITest-compatible mobile: type script.
     * On iOS, sendKeys on a focused XCUIElementTypeTextField works for most cases,
     * but Compose-style text fields respond more reliably to mobile: type.
     */
    private void typeText(AppiumDriver driver, String text) {
        try {
            driver.executeScript("mobile: type", java.util.Map.of("text", text));
        } catch (Exception ex) {
            // Fallback: find the focused text field and send keys directly
            try {
                WebElement focused = driver.findElement(
                        By.xpath("//XCUIElementTypeTextField[@focused='true'] | //XCUIElementTypeSecureTextField[@focused='true']"));
                focused.clear();
                focused.sendKeys(text);
            } catch (Exception ignored) {}
        }
        // Dismiss the keyboard if it is still showing after text entry
        try {
            driver.executeScript("mobile: hideKeyboard");
        } catch (Exception ignored) {}
    }

    /**
     * Dismisses iOS system permission alerts that may appear during or after login.
     * Runs multiple rounds since iOS can stack alerts.
     */
    private void dismissSystemAlerts(AppiumDriver driver) {
        int rounds = 4;
        for (int round = 1; round <= rounds; round++) {
            boolean dismissed = false;

            dismissed |= tapIfPresent(driver,
                    By.xpath("//XCUIElementTypeButton[@name='Allow While Using App']"),
                    "Location — Allow While Using App");

            dismissed |= tapIfPresent(driver,
                    By.xpath("//XCUIElementTypeButton[@name='Allow']"),
                    "System alert — Allow");

            dismissed |= tapIfPresent(driver,
                    By.xpath("//XCUIElementTypeButton[@name='OK']"),
                    "System alert — OK");

            dismissed |= tapIfPresent(driver,
                    AppiumBy.accessibilityId("app_update_later_button"),
                    "App update — Later");

            if (!dismissed) {
                System.out.println("[IOSLoginIfNeeded] No alerts detected (round " + round + ")");
                break;
            }
            System.out.println("[IOSLoginIfNeeded] Dismissed an alert (round " + round + "), checking again...");
            sleep(300);
        }
    }

    private boolean tapIfPresent(AppiumDriver driver, By locator, String description) {
        try {
            List<WebElement> elements = driver.findElements(locator);
            if (!elements.isEmpty()) {
                elements.get(0).click();
                System.out.println("[IOSLoginIfNeeded] Dismissed: " + description);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
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

    private WebElement waitFor(AppiumDriver driver, String tag, long timeoutSec) throws Exception {
        return new WebDriverWait(driver, Duration.ofSeconds(timeoutSec))
                .until(ExpectedConditions.visibilityOfElementLocated(
                        AppiumBy.accessibilityId(tag)));
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private void captureToken(String source) {
        try {
            String udid = "";
            try {
                var info = IOSDriverManager.getDeviceInfo();
                if (info != null) udid = info.udid;
            } catch (Exception ignored) {}

            TokenExtractor.clearCache();
            TestContext.setUserToken(null);
            TestContext.setLegacyToken(null);
            String token = TokenExtractor.get(udid);
            if (token != null && !token.isBlank()) {
                TestContext.setUserToken(token);
                TestContext.setScalarData("auth_token", token);
                String legacy = TokenExtractor.getLegacyToken(udid);
                if (legacy != null && !legacy.isBlank()) {
                    TestContext.setLegacyToken(legacy);
                    TestContext.setScalarData("legacy_token", legacy);
                    System.out.println("  ✅ [IOSLoginIfNeeded] Legacy DRF token captured");
                }
                String preview = token.length() > 20
                        ? token.substring(0, 10) + "…" + token.substring(token.length() - 6)
                        : token;
                System.out.println("  ✅ [IOSLoginIfNeeded] Token captured via " + source + ": " + preview);
            } else {
                System.out.println("  ⚠️  [IOSLoginIfNeeded] Token not found via " + source);
            }
        } catch (Exception e) {
            System.out.println("  ⚠️  [IOSLoginIfNeeded] Token capture failed (" + source + "): " + e.getMessage());
        }
    }
}
