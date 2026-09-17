package com.popclub.android.actions;

import com.popclub.core.Locator;
import com.popclub.core.LocatorUtil;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * AssertTextAction — verifies that a specific on-screen element's text equals
 * an expected value.
 *
 * The expected value in the {@code value} field may use {@code ${varName}}
 * syntax — the executor interpolates it before this action runs.
 *
 * YAML examples:
 *
 *   # Assert a specific element shows a literal string
 *   - action: assertText
 *     element: cart_product_name
 *     value: "Nike Air Max 270"
 *
 *   # Assert an element matches a previously captured variable
 *   - action: assertText
 *     element: cart_product_name
 *     value: ${pdpProductName}
 *
 *   # Assert using a direct locator (qaTestTag)
 *   - action: assertText
 *     locator: product_details_title
 *     value: ${capturedTitle}
 *
 * Fails with a clear diff message if the actual text does not match.
 */
public class AssertTextAction implements Action {

    private static final int WAIT_SECONDS = 5;

    @Override
    public void perform(Step step) {
        String expected = step.value;
        if (expected == null)
            throw new RuntimeException("assertText: 'value' (expected text) is required");

        AppiumDriver driver = DriverManager.getDriver();
        WebElement el = findElement(driver, step);

        if (el == null)
            throw new RuntimeException("assertText: element not found — " + describeLocator(step));

        String actual = el.getText();
        if (actual == null) actual = "";
        actual = actual.trim();

        String expectedNorm = normalizePrice(expected.trim());
        String actualNorm   = normalizePrice(actual);

        if (!actualNorm.equals(expectedNorm)) {
            throw new RuntimeException(
                "assertText FAIL — " + describeLocator(step) + "\n" +
                "  expected: \"" + expected.trim() + "\"\n" +
                "  actual:   \"" + actual + "\""
            );
        }

        System.out.printf("  ✅ assertText PASS: \"%s\"%n", actual);
    }

    private WebElement findElement(AppiumDriver driver, Step step) {
        if (step.locators != null && !step.locators.isEmpty()) {
            for (Locator loc : step.locators) {
                try {
                    return new WebDriverWait(driver, Duration.ofSeconds(WAIT_SECONDS))
                            .until(ExpectedConditions.visibilityOfElementLocated(
                                    LocatorUtil.getLocator(loc)));
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Normalise price strings so formatting differences don't cause false failures.
     * Strips thousands-separator commas from numeric price values only.
     * e.g. "₹1,099" → "₹1099",  "₹1,099.00" → "₹1099.00"
     * Non-price strings (titles, labels) are returned unchanged.
     */
    private String normalizePrice(String text) {
        if (text == null) return "";
        // Only strip commas when the string looks like a price (currency symbol + digits)
        // Pattern: optional currency symbol, then digits/commas/dots
        if (text.matches("[₹$€£¥]?[\\d,]+(\\.\\d+)?")) {
            return text.replace(",", "");
        }
        return text;
    }

    private String describeLocator(Step step) {
        if (step.element != null) return "element=" + step.element;
        if (step.locator != null) return "locator=" + step.locator;
        return "(unknown locator)";
    }
}
