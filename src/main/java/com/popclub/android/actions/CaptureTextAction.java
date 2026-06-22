package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * CaptureTextAction — reads the visible text of an on-screen element and stores
 * it in TestContext under a named key.  The stored value can later be asserted
 * with assertStoredText, or referenced by any subsequent step.
 *
 * Locator resolution order (first match wins):
 *   1. element  — ElementRepository key (accessibilityId from elements/*.yaml)
 *   2. locator  — direct qaTestTag accessibilityId (from popdroid TestTags.kt)
 *   3. text     — UIAutomator textStartsWith pattern  e.g. "₹" captures first price text
 *
 * Storage key:
 *   value  — required; the key name to store the captured text under
 *             e.g.  value: pdp_price   →  TestContext.setScalarData("pdp_price", "₹1,299")
 *
 * YAML examples:
 *
 *   # Capture the PDP product title by its qaTestTag
 *   - action: captureText
 *     locator: product_details_title
 *     value: item0_title
 *
 *   # Capture the first ₹-prefixed price visible on screen
 *   - action: captureText
 *     text: "₹"
 *     value: item0_pdp_price
 *
 *   # Capture using an ElementRepository element key
 *   - action: captureText
 *     element: product_details_title
 *     value: item0_title
 */
public class CaptureTextAction implements Action {

    private static final long TIMEOUT_MS = 5_000;
    private static final long POLL_MS    = 300;

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

        String storeKey = step.value;
        if (storeKey == null || storeKey.isBlank())
            throw new RuntimeException(
                "captureText: 'value' (storage key) is required");

        AppiumDriver driver = DriverManager.getDriver();
        WebElement el       = resolveElement(driver, step);

        if (el == null)
            throw new RuntimeException(
                "captureText: element not found for key='" + storeKey + "'"
                + describeLocator(step));

        String captured = extractText(el);

        TestContext.setScalarData(storeKey, captured);

        System.out.printf("  ✅ captureText [%s] = \"%s\"%n", storeKey, captured);
    }

    // ── Text extraction ───────────────────────────────────────────────────────

    /**
     * Extract visible text from an element with three fallback layers:
     *
     *  1. el.getText()           — works for XML views and simple Compose Text nodes
     *  2. child text nodes       — Compose Buttons wrap text in a child Text composable
     *                              whose .getText() holds the label
     *  3. content-desc attribute — last resort; some elements expose text via contentDescription
     *
     * We log which strategy succeeded so it's easy to trace in the output.
     */
    private String extractText(WebElement el) {
        // 1. Direct text
        String text = el.getText();
        if (text != null && !text.isBlank()) {
            System.out.println("  [captureText] strategy=direct");
            return text.trim();
        }

        // 2. Child text nodes (Compose Button wraps its label in a child Text composable)
        try {
            List<WebElement> children = el.findElements(By.xpath(".//*[@text!='']"));
            for (WebElement child : children) {
                String ct = child.getText();
                if (ct != null && !ct.isBlank()) {
                    System.out.println("  [captureText] strategy=child-text");
                    return ct.trim();
                }
            }
        } catch (Exception ignored) {}

        // 3. content-desc attribute
        try {
            String desc = el.getAttribute("content-desc");
            if (desc != null && !desc.isBlank()) {
                System.out.println("  [captureText] strategy=content-desc");
                return desc.trim();
            }
        } catch (Exception ignored) {}

        System.out.println("  [captureText] strategy=none — element found but all text sources empty");
        return "";
    }

    // ── Element resolution ────────────────────────────────────────────────────

    /**
     * Resolve which element to read text from:
     *   • locators already resolved by TestExecutor (element/locator fields) → use those
     *   • text field → UIAutomator textStartsWith search
     */
    private WebElement resolveElement(AppiumDriver driver, Step step)
            throws InterruptedException {

        // 1. TestExecutor has already resolved element/locator into step.locators
        if (step.locators != null && !step.locators.isEmpty()) {
            return waitForLocators(driver, step);
        }

        // 2. Direct accessibilityId via locator field (inline, no ElementRepository)
        if (step.locator != null && !step.locator.isBlank()) {
            return waitFor(driver, "accessibilityId", step.locator);
        }

        // 3. Text pattern — find first element whose text starts with the given prefix
        if (step.text != null && !step.text.isBlank()) {
            return waitFor(driver, "textStartsWith", step.text);
        }

        return null;
    }

    private WebElement waitForLocators(AppiumDriver driver, Step step)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            for (com.popclub.core.Locator loc : step.locators) {
                WebElement el = findByLocator(driver, loc);
                if (el != null) return el;
            }
            Thread.sleep(POLL_MS);
        }
        return null;
    }

    private WebElement findByLocator(AppiumDriver driver, com.popclub.core.Locator loc) {
        try {
            List<WebElement> found = switch (loc.type) {
                case "accessibilityId" ->
                    driver.findElements(AppiumBy.accessibilityId(loc.value));
                case "id" ->
                    driver.findElements(AppiumBy.id(loc.value));
                case "text" ->
                    driver.findElements(AppiumBy.androidUIAutomator(
                        "new UiSelector().textContains(\"" + esc(loc.value) + "\")"));
                default -> List.of();
            };
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) { return null; }
    }

    private WebElement waitFor(AppiumDriver driver, String type, String value)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            WebElement el = find(driver, type, value);
            if (el != null) return el;
            Thread.sleep(POLL_MS);
        }
        return null;
    }

    private WebElement find(AppiumDriver driver, String type, String value) {
        try {
            List<WebElement> found = switch (type) {
                case "accessibilityId" ->
                    driver.findElements(AppiumBy.accessibilityId(value));
                case "textStartsWith" ->
                    driver.findElements(AppiumBy.androidUIAutomator(
                        "new UiSelector().textStartsWith(\"" + esc(value) + "\")"));
                case "textContains" ->
                    driver.findElements(AppiumBy.androidUIAutomator(
                        "new UiSelector().textContains(\"" + esc(value) + "\")"));
                default -> List.of();
            };
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) { return null; }
    }

    private String esc(String s) { return s.replace("\"", "\\\""); }

    private String describeLocator(Step step) {
        if (step.element  != null) return " (element=" + step.element + ")";
        if (step.locator  != null) return " (locator=" + step.locator + ")";
        if (step.text     != null) return " (text="    + step.text    + ")";
        return "";
    }
}
