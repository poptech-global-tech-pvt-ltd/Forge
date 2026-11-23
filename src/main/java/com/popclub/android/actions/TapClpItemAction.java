package com.popclub.android.actions;

import com.popclub.clp.ClpSection;
import com.popclub.core.TestContext;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.List;

/**
 * TapClpItemAction — taps a specific carousel/list item by its index within a section.
 *
 * Requires a preceding `verifyCLP` step to have stored the CLP data in TestContext.
 *
 * YAML usage:
 *   - action: tapClpItem
 *     value: SHOP            # page: HOME | SHOP | CARD
 *     element: Trending Now  # section title (partial match OK)
 *     text: "0"              # item index (0-based)
 *
 * Example — tap the second item in the "Brand Picks" section:
 *   - action: tapClpItem
 *     value: SHOP
 *     element: Brand Picks
 *     text: "1"
 *
 * If `element` is omitted, the index is treated as a global index across ALL items
 * on the page.
 */
public class TapClpItemAction implements Action {

    private static final int  MAX_SCROLLS = 10;
    private static final long SCROLL_WAIT = 500;

    @Override
    public void perform(Step step) {
        String page    = step.value   != null ? step.value.trim().toUpperCase() : "SHOP";
        String section = step.element != null ? step.element.trim()             : null;
        String indexStr = step.text   != null ? step.text.trim()                : "0";

        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            throw new RuntimeException("tapClpItem: text must be a numeric index, got: " + indexStr);
        }

        List<ClpSection> sections = TestContext.getClpData(page);
        if (sections.isEmpty()) {
            throw new RuntimeException(
                    "tapClpItem: no CLP data for page '" + page
                    + "'. Run verifyCLP first.");
        }

        String targetTitle = resolveItemTitle(sections, section, index, page);

        System.out.println("  tapClpItem → page=" + page
                + "  section=" + (section != null ? "\"" + section + "\"" : "(any)")
                + "  index=" + index
                + "  → \"" + targetTitle + "\"");

        AppiumDriver driver = DriverManager.getDriver();
        WebElement el = findWithScroll(driver, targetTitle);
        if (el == null) {
            throw new RuntimeException(
                    "tapClpItem FAILED: item [" + index + "] \"" + targetTitle
                    + "\" not found on screen after " + MAX_SCROLLS + " scrolls.");
        }
        el.click();
        System.out.println("  ✅ Tapped item [" + index + "]: \"" + targetTitle + "\"");
    }

    // ── Resolve which title to tap ─────────────────────────────────────────────

    private String resolveItemTitle(List<ClpSection> sections,
                                    String sectionFilter,
                                    int index,
                                    String page) {
        if (sectionFilter != null) {
            // Find the named section
            for (ClpSection s : sections) {
                if (s.sectionTitle.toLowerCase().contains(sectionFilter.toLowerCase())) {
                    if (index >= s.itemTitles.size()) {
                        throw new RuntimeException(
                                "tapClpItem: section \"" + s.sectionTitle
                                + "\" has only " + s.itemTitles.size()
                                + " items; index " + index + " is out of range.");
                    }
                    return s.itemTitles.get(index);
                }
            }
            throw new RuntimeException(
                    "tapClpItem: no section matching \"" + sectionFilter
                    + "\" found in " + page + " CLP data. "
                    + "Available sections: " + sectionTitles(sections));
        }

        // No section filter — global flat index across all items
        int cursor = 0;
        for (ClpSection s : sections) {
            for (String title : s.itemTitles) {
                if (cursor == index) return title;
                cursor++;
            }
        }

        int total = sections.stream().mapToInt(s -> s.itemTitles.size()).sum();
        throw new RuntimeException(
                "tapClpItem: global index " + index + " is out of range. "
                + "Total items on " + page + " CLP: " + total);
    }

    private String sectionTitles(List<ClpSection> sections) {
        StringBuilder sb = new StringBuilder("[");
        for (ClpSection s : sections) sb.append('"').append(s.sectionTitle).append("\", ");
        if (sb.length() > 1) sb.setLength(sb.length() - 2);
        sb.append("]");
        return sb.toString();
    }

    // ── UIAutomator scroll + tap ───────────────────────────────────────────────

    private WebElement findWithScroll(AppiumDriver driver, String text) {
        for (int scroll = 0; scroll <= MAX_SCROLLS; scroll++) {
            WebElement el = findVisible(driver, text);
            if (el != null) return el;
            if (scroll < MAX_SCROLLS) {
                scrollDown(driver);
                try { Thread.sleep(SCROLL_WAIT); } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }

    private WebElement findVisible(AppiumDriver driver, String text) {
        try {
            String escaped  = text.replace("\"", "\\\"");
            String selector = "new UiSelector().textContains(\"" + escaped + "\")";
            List<WebElement> found = driver.findElements(
                    AppiumBy.androidUIAutomator(selector));
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private void scrollDown(AppiumDriver driver) {
        try {
            Dimension size   = driver.manage().window().getSize();
            int startY  = (int) (size.height * 0.75);
            int endY    = (int) (size.height * 0.25);
            int centerX = size.width / 2;

            PointerInput finger   = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence     sequence = new Sequence(finger, 1);
            sequence.addAction(finger.createPointerMove(
                    Duration.ZERO, PointerInput.Origin.viewport(), centerX, startY));
            sequence.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            sequence.addAction(finger.createPointerMove(
                    Duration.ofMillis(400), PointerInput.Origin.viewport(), centerX, endY));
            sequence.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(List.of(sequence));
        } catch (Exception e) {
            System.out.println("  ⚠️  Scroll failed: " + e.getMessage());
        }
    }
}
