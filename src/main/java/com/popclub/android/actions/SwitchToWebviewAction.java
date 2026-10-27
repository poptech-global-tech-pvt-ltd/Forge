package com.popclub.android.actions;

import com.popclub.android.driver.DriverManager;
import com.popclub.core.TestContext;
import com.popclub.model.Step;
import io.appium.java_client.android.AndroidDriver;

import java.util.Set;

/**
 * switchToWebview — switches the current Appium session from the native app
 * context into whichever WEBVIEW_* context the Razorpay/Chrome redirect has
 * opened (tutor-onboarding flow). Polls getContextHandles() since the
 * WEBVIEW_* context only registers once the page has started loading.
 *
 * YAML usage:
 *   - action: switchToWebview
 */
public class SwitchToWebviewAction implements Action {

    @Override
    public void perform(Step step) {
        AndroidDriver driver = (AndroidDriver) DriverManager.getDriver();

        int timeoutMs = (step.timeout > 0 ? step.timeout : TestContext.getDefaultTimeout()) * 1000;
        long deadline = System.currentTimeMillis() + timeoutMs;
        String target = null;
        Set<String> lastSeen = Set.of();

        while (System.currentTimeMillis() < deadline) {
            lastSeen = driver.getContextHandles();
            target = lastSeen.stream()
                    .filter(c -> c.startsWith("WEBVIEW_"))
                    .findFirst()
                    .orElse(null);
            if (target != null) {
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        if (target == null) {
            throw new RuntimeException("switchToWebview: no WEBVIEW_* context appeared within "
                    + (timeoutMs / 1000) + "s — contexts seen: " + lastSeen);
        }

        driver.context(target);
        System.out.println("[SwitchToWebview] Switched to context: " + target);
    }
}
