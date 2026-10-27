package com.popclub.android.actions;

import com.popclub.core.GestureUtil;
import com.popclub.core.TestContext;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * FindCartItemIndexAction — scans cart_item_title_0, _1, _2 … until it finds
 * the item whose text matches {@code step.value}, then stores the index in
 * {@code step.variable}.
 *
 * Use this after landing on the cart screen when the cart may contain multiple
 * items (from a previous session) and the item you just added may not be at
 * index 0.
 *
 * Scroll behaviour: if an index is not found in the current view the action
 * scrolls down and retries that index up to MAX_SCROLL_ATTEMPTS times before
 * giving up.  This handles RecyclerView carts where off-screen items are not
 * in the accessibility tree.
 *
 * YAML usage:
 *   - action: findCartItemIndex
 *     value:    "${product_title}"    # text to match (already interpolated)
 *     variable: cart_item_index       # variable to store the found index (0-based)
 *
 * On success:  TestContext variable {@code cart_item_index} = "0", "1", etc.
 * On failure:  throws RuntimeException listing all titles scanned.
 */
public class FindCartItemIndexAction implements Action {

    private static final int    MAX_ITEMS           = 10;
    private static final int    MAX_SCROLL_ATTEMPTS = 8;
    private static final int    SWIPE_DURATION_MS   = 400;
    private static final String TAG_PREFIX          = "cart_item_title_";

    @Override
    public void perform(Step step) {

        String expectedTitle = step.value;
        String storeKey      = step.variable;

        if (expectedTitle == null || expectedTitle.isBlank())
            throw new RuntimeException("[findCartItemIndex] 'value' (title to match) is required");
        if (storeKey == null || storeKey.isBlank())
            throw new RuntimeException("[findCartItemIndex] 'variable' (storage key) is required");

        AppiumDriver driver = DriverManager.getDriver();
        StringBuilder scanned = new StringBuilder();

        for (int i = 0; i < MAX_ITEMS; i++) {
            String accessibilityId = TAG_PREFIX + i;

            // Scroll down until this index appears in the view hierarchy
            String text = getTextScrollingIfNeeded(driver, accessibilityId);

            if (text == null) {
                // No element at this index even after scrolling — past the last cart item
                System.out.println("[findCartItemIndex] No element at index " + i
                        + " after scrolling — stopping scan");
                break;
            }

            scanned.append("  [").append(i).append("] ").append(text).append("\n");
            System.out.println("[findCartItemIndex] index=" + i + " title=\"" + text + "\"");

            if (text.trim().equalsIgnoreCase(expectedTitle.trim())) {
                TestContext.setScalarData(storeKey, String.valueOf(i));
                System.out.println("[findCartItemIndex] ✅ Found at index " + i
                        + " → stored in '" + storeKey + "'");
                return;
            }
        }

        throw new RuntimeException(
            "[findCartItemIndex] Item not found in cart.\n"
            + "  Looking for: \"" + expectedTitle + "\"\n"
            + "  Scanned:\n" + scanned
        );
    }

    /**
     * Returns the text of {@code accessibilityId}, scrolling down up to
     * MAX_SCROLL_ATTEMPTS times if the element is not yet in the view hierarchy.
     * Returns null only when the element is still absent after all scroll attempts.
     */
    private String getTextScrollingIfNeeded(AppiumDriver driver, String accessibilityId) {
        String text = getTextByAccessibilityId(driver, accessibilityId);
        if (text != null) return text;

        for (int attempt = 1; attempt <= MAX_SCROLL_ATTEMPTS; attempt++) {
            System.out.println("[findCartItemIndex] '" + accessibilityId
                    + "' not visible — scrolling down (attempt " + attempt + "/" + MAX_SCROLL_ATTEMPTS + ")");
            try {
                GestureUtil.swipe(driver, "up", SWIPE_DURATION_MS);
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.out.println("[findCartItemIndex] Swipe failed: " + e.getMessage());
            }

            text = getTextByAccessibilityId(driver, accessibilityId);
            if (text != null) return text;
        }

        return null;
    }

    /**
     * Returns the visible text of the element with the given accessibilityId,
     * or null if the element is not present on screen.
     */
    private String getTextByAccessibilityId(AppiumDriver driver, String accessibilityId) {
        try {
            List<WebElement> elements =
                driver.findElements(AppiumBy.accessibilityId(accessibilityId));

            if (elements.isEmpty()) return null;

            WebElement el = elements.get(0);

            // 1. Direct text
            String text = el.getText();
            if (text != null && !text.isBlank()) return text.trim();

            // 2. content-desc (Compose nodes sometimes store display text here)
            String desc = el.getAttribute("content-desc");
            if (desc != null && !desc.isBlank()) return desc.trim();

            // 3. Child text nodes
            List<WebElement> children =
                el.findElements(org.openqa.selenium.By.xpath(".//*[@text!='']"));
            for (WebElement child : children) {
                String ct = child.getText();
                if (ct != null && !ct.isBlank()) return ct.trim();
            }

            return ""; // element exists but text is empty
        } catch (Exception e) {
            return null;
        }
    }
}
