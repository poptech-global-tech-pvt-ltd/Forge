package com.popclub.android.actions;

import com.popclub.core.Locator;
import com.popclub.core.LocatorUtil;
import com.popclub.core.TestContext;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * CaptureToastAction — captures Android toast / snackbar text.
 *
 * Supports two modes depending on the toast type:
 *
 * 1. COMPOSE CUSTOM SNACKBAR (element: provided)
 *    Rapid findElements polling at 30ms — catches the element in the
 *    accessibility tree while it's visible. Text is extracted from the
 *    element itself or its child Text composable.
 *
 * 2. SYSTEM TOAST (no element: provided)
 *    Uses UIAutomator2's "mobile: getToast" which captures text from
 *    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED — works even
 *    after the toast disappears from the view hierarchy.
 *
 * YAML examples:
 *
 *   # Compose snackbar with qaTestTag — use element:
 *   - action: captureToast
 *     element: add_to_cart_snackbar
 *     variable: toast_text
 *     timeout: 4000
 *     optional: true
 *
 *   # System android.widget.Toast — no element needed
 *   - action: captureToast
 *     variable: toast_text
 *     timeout: 3000
 *     optional: true
 */
public class CaptureToastAction implements Action {

    private static final int DEFAULT_TIMEOUT_MS = 3_000;
    private static final int POLL_MS            = 30;   // very tight — toasts are short-lived

    @Override
    public void perform(Step step) {
        String storeKey = step.variable != null ? step.variable : step.value;
        if (storeKey == null || storeKey.isBlank())
            throw new RuntimeException("captureToast: 'variable' is required");

        int timeoutMs = (step.timeout > 0) ? step.timeout : DEFAULT_TIMEOUT_MS;
        boolean optional = step.optional;

        AppiumDriver driver = DriverManager.getDriver();
        String toastText;

        if (step.locators != null && !step.locators.isEmpty()) {
            // Mode 1: Compose / custom snackbar — rapid element polling
            toastText = pollElementForText(driver, step.locators, timeoutMs);
        } else {
            // Mode 2: System toast — UIAutomator2 accessibility event cache
            toastText = pollSystemToast(driver, timeoutMs);
        }

        if (toastText == null || toastText.isBlank()) {
            if (optional) {
                TestContext.setScalarData(storeKey, "");
                System.out.printf("  ⚠️  captureToast [%s] — no toast appeared (optional, stored empty)%n", storeKey);
                return;
            }
            throw new RuntimeException(
                "captureToast: no toast/snackbar appeared within " + timeoutMs + "ms");
        }

        TestContext.setScalarData(storeKey, toastText.trim());
        System.out.printf("  ✅ captureToast [%s] = \"%s\"%n", storeKey, toastText.trim());
    }

    // ── Mode 1: rapid element polling ────────────────────────────────────────

    /**
     * Poll findElements() at POLL_MS intervals with implicit wait disabled.
     * When found, immediately extract text from element or child Text nodes.
     *
     * Why findElements over getPageSource():
     *   findElements() returns the WebElement reference needed to call getText()
     *   and find children. getPageSource() requires XML parsing + a second
     *   findElement call to get the actual WebElement. For short-lived elements
     *   the extra call often misses the window.
     */
    private String pollElementForText(AppiumDriver driver, List<Locator> locators, int timeoutMs) {
        try { driver.manage().timeouts().implicitlyWait(Duration.ofMillis(0)); }
        catch (Exception ignored) {}

        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            for (Locator loc : locators) {
                WebElement el = findOne(driver, loc);
                if (el != null) {
                    System.out.println("  [captureToast] element found in accessibility tree");
                    String text = extractText(el);
                    if (text != null && !text.isBlank()) return text;
                    // element found but text not ready yet — keep polling
                }
            }
            try { Thread.sleep(POLL_MS); } catch (InterruptedException e) { break; }
        }
        return null;
    }

    private WebElement findOne(AppiumDriver driver, Locator loc) {
        try {
            List<WebElement> found = driver.findElements(LocatorUtil.getLocator(loc));
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) { return null; }
    }

    /**
     * Extract text with three fallbacks:
     *   1. el.getText()       — direct text
     *   2. Child xpath search — Compose Text composables nest text in children
     *   3. content-desc       — last resort
     */
    private String extractText(WebElement el) {
        try {
            String t = el.getText();
            if (t != null && !t.isBlank()) return t.trim();
        } catch (Exception ignored) {}

        try {
            List<WebElement> children = el.findElements(
                org.openqa.selenium.By.xpath(".//*[@text!='']"));
            for (WebElement child : children) {
                String ct = child.getText();
                if (ct != null && !ct.isBlank()) return ct.trim();
            }
        } catch (Exception ignored) {}

        try {
            String desc = el.getAttribute("content-desc");
            if (desc != null && !desc.isBlank()) return desc.trim();
        } catch (Exception ignored) {}

        return null;
    }

    // ── Mode 2: system toast via UIAutomator2 accessibility cache ────────────

    /**
     * UIAutomator2 listens for AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
     * and caches the toast text. mobile: getToast reads that cache — works for
     * android.widget.Toast even after it's gone from the view hierarchy.
     * Does NOT work for Compose custom toasts (use element: + Mode 1 for those).
     */
    private String pollSystemToast(AppiumDriver driver, int timeoutMs) {
        try {
            Object result = driver.executeScript("mobile: getToast",
                Map.of("timeout", timeoutMs));
            if (result != null) {
                String text = result.toString().trim();
                return text.isBlank() ? null : text;
            }
        } catch (Exception e) {
            System.out.printf("  ⚠️  captureToast: mobile: getToast failed: %s%n", e.getMessage());
        }
        return null;
    }
}
