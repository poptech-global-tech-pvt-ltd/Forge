package com.popclub.android.actions;

import com.popclub.api.auth.TokenExtractor;
import com.popclub.core.TestContext;
import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.android.driver.DriverManager;
import com.popclub.driver.ForgeDriverContext;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
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
    // Onboarding story screen shown before the phone-number screen when login hasn't happened yet.
    private static final String ONBOARDING_SKIP_BTN_TAG = "story_onboarding_skip_button";

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
        if (!TestContext.isLoginRequired()) {
            System.out.println("[loginIfNeeded] loginRequired=false — skipping login");
            return;
        }

        if (ForgeDriverContext.isActive()) {
            forgeLogin(step);
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

        // Runtime permission prompts (e.g. notifications) can cover the home screen
        // on either path above — dismiss so the next waitFor/tap isn't blocked.
        dismissPermissionDialog(driver);

        System.out.println("[loginIfNeeded] ✅ Login step complete");
    }

    private void forgeLogin(Step step) throws Exception {
        com.popclub.driver.ForgeDriverClient forge = ForgeDriverContext.get();
        String phone = step.value != null ? step.value.trim() : "1234561122";
        String otp   = step.text  != null ? step.text.trim()  : "560102";
        TestContext.setLoginPhone(phone);
        TestContext.setLoginOtp(otp);

        // Wait up to 15s for either the login screen or the home/tab bar to appear
        System.out.println("[loginIfNeeded/forge] Waiting for app to reach login or home screen…");
        boolean loginVisible = false;
        boolean homeVisible  = false;
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            // Onboarding story screen can cover the phone-input screen before login — skip it.
            if (forge.isPresent(ONBOARDING_SKIP_BTN_TAG)) {
                System.out.println("[loginIfNeeded/forge] Onboarding story screen detected — tapping skip button");
                forge.tap(ONBOARDING_SKIP_BTN_TAG);
                Thread.sleep(500);
                continue;
            }
            if (forge.isPresent(PHONE_INPUT_TAG)) { loginVisible = true; break; }
            if (forge.isPresent(HOME_TAB_TAG))    { homeVisible  = true; break; }
            if (forge.isPresent("common_shop_tab")) { homeVisible = true; break; }
            Thread.sleep(500);
        }

        if (homeVisible) {
            System.out.println("[loginIfNeeded/forge] Already logged in — skipping login");
            storeToken(ForgeDriverContext.getDeviceSerial(), "cache/DataStore/SharedPrefs (forge, login skipped)", false);
            return;
        }

        if (!loginVisible) {
            // Dismiss any system permission dialogs ("Allow" / "While using the app" / "Don't allow")
            dismissSystemDialogs(forge);

            // Re-check after dismissal
            if (forge.isPresent(PHONE_INPUT_TAG)) { loginVisible = true; }
            else if (forge.isPresent(HOME_TAB_TAG) || forge.isPresent("common_shop_tab")) { homeVisible = true; }

            if (!loginVisible && !homeVisible) {
                // App might be on a deep screen — press back until login or home appears
                System.out.println("[loginIfNeeded/forge] Neither login nor home visible — pressing back to navigate…");
                for (int i = 0; i < 6; i++) {
                    forge.pressBack();
                    Thread.sleep(600);
                    dismissSystemDialogs(forge);
                    if (forge.isPresent(PHONE_INPUT_TAG)) { loginVisible = true; break; }
                    if (forge.isPresent(HOME_TAB_TAG) || forge.isPresent("common_shop_tab")) { homeVisible = true; break; }
                }
            }

            if (homeVisible) {
                System.out.println("[loginIfNeeded/forge] Reached home screen after back-presses — already logged in");
                storeToken(ForgeDriverContext.getDeviceSerial(), "cache/DataStore/SharedPrefs (forge)", false);
                return;
            }
            if (!loginVisible) {
                throw new RuntimeException("[loginIfNeeded/forge] Could not reach login or home screen");
            }
        }

        System.out.println("[loginIfNeeded/forge] Login screen detected — performing login…");
        forge.tap(PHONE_INPUT_TAG);
        forge.clearAndType(phone);
        forge.tap(CONTINUE_BTN_TAG);

        int[] otpField = forge.waitUntilPresent(OTP_INPUT_TAG, LOGIN_TIMEOUT_SEC * 1000);
        if (otpField == null) {
            otpField = forge.waitUntilPresent("login_merged_otp_entry_content_button", 10_000);
        }
        if (otpField == null) throw new RuntimeException("[loginIfNeeded/forge] OTP screen did not appear");

        forge.tap(OTP_INPUT_TAG);
        forge.type(otp);

        int[] homeTab = forge.waitUntilPresent(HOME_TAB_TAG, LOGIN_TIMEOUT_SEC * 1000);
        if (homeTab == null) throw new RuntimeException("[loginIfNeeded/forge] Home screen not visible after OTP");
        System.out.println("[loginIfNeeded/forge] ✅ Login succeeded");
        storeToken(ForgeDriverContext.getDeviceSerial(), "logcat/DataStore (forge)", true);
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

    /** Dismisses a runtime permission dialog if one appears; package name varies by device/OS build. */
    private void dismissPermissionDialog(AppiumDriver driver) {
        for (String pkg : new String[]{"com.android.permissioncontroller", "com.google.android.permissioncontroller"}) {
            try {
                List<WebElement> found = driver.findElements(AppiumBy.androidUIAutomator(
                        "new UiSelector().resourceId(\"" + pkg + ":id/permission_deny_button\")"));
                if (!found.isEmpty() && found.get(0).isDisplayed()) {
                    found.get(0).click();
                    System.out.println("[loginIfNeeded] Dismissed runtime permission dialog (" + pkg + ")");
                    return;
                }
            } catch (Exception ignored) {}
        }
        // resourceId naming varies by OS build — fall back to the visible button text.
        try {
            List<WebElement> found = driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textMatches(\"(?i)don.t allow\")"));
            if (!found.isEmpty() && found.get(0).isDisplayed()) {
                found.get(0).click();
                System.out.println("[loginIfNeeded] Dismissed runtime permission dialog (text match)");
            }
        } catch (Exception ignored) {}
    }

    /** Taps the first visible system permission dialog button via ForgeDriver text search. */
    private void dismissSystemDialogs(com.popclub.driver.ForgeDriverClient forge) {
        autoHandleDialogs(forge);
    }

    /**
     * Auto-grant permissions and/or dismiss dialogs.
     * Called before each forge step when autoGrantPermissions/autoDismissDialogs are set in YAML.
     * Also callable from forgeLogin() for in-flow dialogs.
     */
    public static void autoHandleDialogs(com.popclub.driver.ForgeDriverClient forge) {
        // Permission dialogs — tap the affirmative button
        if (com.popclub.core.TestContext.isAutoGrantPermissions()) {
            for (String label : new String[]{"While using the app", "Only this time", "Allow"}) {
                try {
                    if (!forge.searchByText(label).isEmpty()) {
                        forge.tapByText(label);
                        System.out.println("[forge/dialog] Granted permission: " + label);
                        Thread.sleep(300);
                        return;
                    }
                } catch (Exception ignored) {}
            }
        } else {
            // Legacy behaviour: dismiss (deny) permission dialogs
            for (String label : new String[]{"While using the app", "Only this time", "Allow", "Don’t allow"}) {
                try {
                    if (!forge.searchByText(label).isEmpty()) {
                        forge.tapByText(label);
                        System.out.println("[forge/dialog] Dismissed permission dialog: " + label);
                        Thread.sleep(300);
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }

        // Generic dialogs — OK, Got it, Close, Skip, Not now, etc.
        if (com.popclub.core.TestContext.isAutoDismissDialogs()) {
            for (String label : new String[]{"OK", "Got it", "Close", "Dismiss", "Skip", "Not now", "No thanks", "Later"}) {
                try {
                    if (!forge.searchByText(label).isEmpty()) {
                        forge.tapByText(label);
                        System.out.println("[forge/dialog] Dismissed dialog: " + label);
                        Thread.sleep(300);
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }
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
            // mobile: hideKeyboard falls back to a raw KEYCODE_BACK press when no
            // keyboard is actually showing (e.g. it already auto-dismissed after a
            // 6-digit OTP auto-submits) — that stray back-press can land on the
            // now-loaded Home screen and trigger the app's "Exit App?" dialog.
            if (((AndroidDriver) driver).isKeyboardShown()) {
                driver.executeScript("mobile: hideKeyboard");
            }
        } catch (Exception ignored) {}
    }

    /** Returns true if the accessibilityId element appears within timeoutMs. */
    private boolean isVisible(AppiumDriver driver, String tag, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                // Google's phone-number-hint bottom sheet ("Choose a phone number") can pop up
                // over the login screen while it's still loading, covering login_phone_input and
                // making it undetectable — dismiss it so the real login field becomes visible.
                List<WebElement> gmsCancel = driver.findElements(By.id("com.google.android.gms:id/cancel"));
                if (!gmsCancel.isEmpty() && gmsCancel.get(0).isDisplayed()) {
                    gmsCancel.get(0).click();
                    System.out.println("[loginIfNeeded] Dismissed phone-number-hint bottom sheet");
                }

                // Onboarding story screen can cover the phone-input screen before login — skip it.
                List<WebElement> onboardingSkip = driver.findElements(AppiumBy.accessibilityId(ONBOARDING_SKIP_BTN_TAG));
                if (!onboardingSkip.isEmpty() && onboardingSkip.get(0).isDisplayed()) {
                    onboardingSkip.get(0).click();
                    System.out.println("[loginIfNeeded] Onboarding story screen detected — tapped skip button");
                }

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
        storeToken(getDeviceSerial(), "logcat/DataStore/SharedPrefs", true);
    }

    private void captureTokenFromDevice() {
        storeToken(getDeviceSerial(), "cache/DataStore/SharedPrefs (login skipped)", false);
    }

    private String getDeviceSerial() {
        try {
            var info = AppiumDriverManager.getDeviceInfo();
            if (info != null && info.udid != null && !info.udid.isBlank()) return info.udid;
        } catch (Exception ignored) {}
        return System.getProperty("deviceSerial", "");
    }

    private void storeToken(String deviceSerial, String source, boolean clearCacheFirst) {
        try {
            if (clearCacheFirst) {
                TokenExtractor.clearCache();
            }
            TestContext.setUserToken(null);
            TestContext.setLegacyToken(null);
            String token = TokenExtractor.get(deviceSerial);
            if (token != null && !token.isBlank()) {
                TestContext.setUserToken(token);
                TestContext.setScalarData("auth_token", token);
                String legacy = TokenExtractor.getLegacyToken(deviceSerial);
                if (legacy == null || legacy.isBlank()) {
                    legacy = TokenExtractor.getLegacyFromCache();
                }
                if (legacy != null && !legacy.isBlank()) {
                    TestContext.setLegacyToken(legacy);
                    TestContext.setScalarData("legacy_token", legacy);
                    TokenExtractor.saveLegacyToCache(legacy);
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
