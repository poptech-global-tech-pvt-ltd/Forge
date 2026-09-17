package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final long DEFAULT_TIMEOUT_MS = 5_000;
    private static final long POLL_MS            = 50;   // tight poll for transient elements (toasts/snackbars)

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

        String storeKey = step.variable != null ? step.variable : step.value;
        if (storeKey == null || storeKey.isBlank())
            throw new RuntimeException(
                "captureText: 'variable' (storage key) is required");

        // optional: step.timeout (in ms) overrides default — useful for transient elements like snackbars
        long timeoutMs = (step.timeout > 0) ? step.timeout : DEFAULT_TIMEOUT_MS;
        // optional: step.optional=true — store empty string instead of failing when not found
        boolean optional = step.optional;

        AppiumDriver driver = DriverManager.getDriver();

        // ── Maestro-style fast path: poll page source in-memory ──────────────
        // One getPageSource() call per poll instead of findElement + getText.
        // This is significantly faster for transient elements (toasts/snackbars)
        // because the XML contains both the element AND its text — no second HTTP call.
        if (step.locators != null && !step.locators.isEmpty()) {
            String captured = pollPageSourceForText(driver, step, timeoutMs);
            if (captured != null) {
                TestContext.setScalarData(storeKey, captured);
                System.out.printf("  ✅ captureText [%s] = \"%s\" (page-source fast path)%n", storeKey, captured);
                return;
            }
            if (optional) {
                TestContext.setScalarData(storeKey, "");
                System.out.printf("  ⚠️  captureText [%s] — element not found (optional, stored empty)%n", storeKey);
                return;
            }
            throw new RuntimeException(
                "captureText: element not found for key='" + storeKey + "'"
                + describeLocator(step));
        }

        // ── Fallback: text/locator field path (no pre-resolved locators) ─────
        WebElement el = resolveElement(driver, step, timeoutMs);

        if (el == null) {
            if (optional) {
                TestContext.setScalarData(storeKey, "");
                System.out.printf("  ⚠️  captureText [%s] — element not found (optional, stored empty)%n", storeKey);
                return;
            }
            throw new RuntimeException(
                "captureText: element not found for key='" + storeKey + "'"
                + describeLocator(step));
        }

        String captured = extractText(el);
        TestContext.setScalarData(storeKey, captured);
        System.out.printf("  ✅ captureText [%s] = \"%s\"%n", storeKey, captured);
    }

    // ── Maestro-style page-source poll ───────────────────────────────────────

    /**
     * Polls getPageSource() at POLL_MS intervals, searches the XML in-memory for
     * the element AND extracts its text attribute in one shot — no second HTTP call.
     *
     * This mirrors how Maestro captures text: maintain/dump the UI tree once per
     * poll tick, then read the text attribute directly from the XML node.
     *
     * Handles both:
     *   content-desc="..." (accessibilityId elements)
     *   text="..."         (standard text views)
     */
    private String pollPageSourceForText(AppiumDriver driver, Step step, long timeoutMs)
            throws InterruptedException {

        long deadline = System.currentTimeMillis() + timeoutMs;

        boolean firstPoll = true;
        while (System.currentTimeMillis() < deadline) {
            try {
                String xml = driver.getPageSource();
                if (xml != null) {
                    // On first poll, log a snippet around our locator for diagnostics
                    if (firstPoll) {
                        firstPoll = false;
                        for (com.popclub.core.Locator loc : step.locators) {
                            int idx = xml.indexOf(loc.value);
                            if (idx >= 0) {
                                int s = Math.max(0, idx - 50);
                                int e = Math.min(xml.length(), idx + 300);
                                System.out.printf("  [captureText] XML snippet around '%s':%n%s%n",
                                    loc.value, xml.substring(s, e));
                            } else {
                                System.out.printf("  [captureText] '%s' NOT found in XML on first poll%n", loc.value);
                            }
                        }
                    }
                    for (com.popclub.core.Locator loc : step.locators) {
                        String text = extractTextFromXml(xml, loc);
                        if (text != null) return text;
                    }
                }
            } catch (Exception ignored) {}
            Thread.sleep(POLL_MS);
        }
        return null;
    }

    /**
     * Given the full XML page source and a locator, find the matching node and
     * return its text (including from child nodes).
     *
     * Compose qaTestTag elements are containers — the visible text lives in a
     * child TextView node, not on the container itself. This method:
     *   1. Finds the position in XML where our locator attribute appears
     *   2. Scans forward through the subtree to find the first non-empty text= value
     *   3. Stops at the end of the subtree (tracks open/close tag depth)
     *
     * Example:
     *   <FrameLayout content-desc="product_details_add_to_cart_toast" ...>
     *     <TextView text="Added to Basket Successfully" ... />   ← captured
     *   </FrameLayout>
     */
    private String extractTextFromXml(String xml, com.popclub.core.Locator loc) {
        if (loc == null || loc.value == null) return null;

        // Find the index where our locator attribute appears in the XML
        String attrSnippet;
        switch (loc.type) {
            case "accessibilityId":
                attrSnippet = "content-desc=\"" + loc.value + "\"";
                break;
            case "id":
                attrSnippet = "resource-id=\"" + loc.value + "\"";
                break;
            case "text":
                attrSnippet = "text=\"" + loc.value + "\"";
                break;
            default:
                return null;
        }

        int attrPos = xml.indexOf(attrSnippet);
        if (attrPos < 0) return null;

        // Walk back to the start of the opening tag that contains this attribute
        int tagStart = xml.lastIndexOf('<', attrPos);
        if (tagStart < 0) return null;

        // Check if the element itself has text= on its opening tag
        int tagEnd = xml.indexOf('>', tagStart);
        if (tagEnd < 0) return null;
        String openingTag = xml.substring(tagStart, tagEnd + 1);

        String directText = extractAttr(openingTag, "text");
        if (directText != null && !directText.isBlank()) {
            return directText.trim();
        }

        // Self-closing tag → no children, element found but empty text
        if (openingTag.endsWith("/>")) {
            return "";
        }

        // Scan forward through the subtree for any child with non-empty text=
        // Extract a reasonable window (3000 chars) past the opening tag
        int windowEnd = Math.min(tagEnd + 1 + 3000, xml.length());
        String subtree = xml.substring(tagEnd + 1, windowEnd);

        Pattern childTextPattern = Pattern.compile("text=\"([^\"]+)\"");
        Matcher m = childTextPattern.matcher(subtree);
        while (m.find()) {
            String t = m.group(1).trim();
            if (!t.isBlank()) return t;
        }

        return ""; // element found but no text anywhere in subtree
    }

    /** Extract a named XML attribute value from a single XML node string. */
    private String extractAttr(String node, String attr) {
        Pattern p = Pattern.compile(attr + "=\"([^\"]*)\"");
        Matcher m = p.matcher(node);
        return m.find() ? m.group(1) : null;
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
    private WebElement resolveElement(AppiumDriver driver, Step step, long timeoutMs)
            throws InterruptedException {

        // 1. TestExecutor has already resolved element/locator into step.locators
        if (step.locators != null && !step.locators.isEmpty()) {
            return waitForLocators(driver, step, timeoutMs);
        }

        // 2. Direct accessibilityId via locator field (inline, no ElementRepository)
        if (step.locator != null && !step.locator.isBlank()) {
            return waitFor(driver, "accessibilityId", step.locator, timeoutMs);
        }

        // 3. Text pattern — find first element whose text starts with the given prefix
        if (step.text != null && !step.text.isBlank()) {
            return waitFor(driver, "textStartsWith", step.text, timeoutMs);
        }

        return null;
    }

    private WebElement waitForLocators(AppiumDriver driver, Step step, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
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

    private WebElement waitFor(AppiumDriver driver, String type, String value, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
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
