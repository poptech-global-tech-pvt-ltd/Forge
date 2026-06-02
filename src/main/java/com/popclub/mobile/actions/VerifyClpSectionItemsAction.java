package com.popclub.mobile.actions;

import com.popclub.clp.ClpSection;
import com.popclub.core.TestContext;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VerifyClpSectionItemsAction — finds every grid-type section in the CLP page
 * (from TestContext, populated by a preceding {@code verifyCLP} step) and
 * asserts that the item tiles inside each grid are visible on screen.
 *
 * Works across HOME, SHOP, and CARD — no section name required.
 * When {@code element} is provided it verifies only that named section.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * YAML — auto-detect ALL grids on the page:
 *
 *   - action: verifyCLP
 *     value: SHOP
 *
 *   - action: verifyClpSectionItems
 *     value: SHOP          # page: HOME | SHOP | CARD
 *                          # element omitted → verifies every grid section
 *
 * YAML — verify one specific section by name:
 *
 *   - action: verifyClpSectionItems
 *     value: SHOP
 *     element: Everything UPI    # partial, case-insensitive match
 *     text: "4"                  # (optional) max items to check per section
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Output example:
 *
 *   [Grid 0] Everything UPI  (grid)
 *     ✅ ITEM[0]  "PhonePe"
 *     ✅ ITEM[1]  "Google Pay"
 *     ⚠️  ITEM[2]  "Amazon Pay"  — not visible (may be in horizontal scroll)
 *
 *   [Grid 1] Recharges and bills  (grid)
 *     ✅ ITEM[0]  "Electricity"
 *     ✅ ITEM[1]  "Mobile Recharge"
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Pass/fail:
 *   • Warns  (⚠️) when an item is not in the current viewport — horizontal
 *     carousels only show a subset of items without scrolling sideways.
 *   • Fails  (RuntimeException) only when ZERO items in a grid are found at all,
 *     which indicates the section itself is not rendered or the app is on the
 *     wrong screen.
 */
public class VerifyClpSectionItemsAction implements Action {

    private static final int  MAX_SCROLLS = 12;
    private static final long SCROLL_WAIT = 500;

    /** cardTypes that represent a grid-style widget (rows × columns of tiles). */
    private static final Set<String> GRID_TYPES = new HashSet<>(Arrays.asList(
            "grid", "horizontal_grid", "vertical_grid",
            "icon_grid", "category_grid", "shortcut_grid",
            "horizontal_list", "vertical_list"
    ));

    @Override
    public void perform(Step step) {
        String page          = step.value   != null ? step.value.trim().toUpperCase() : "SHOP";
        String sectionFilter = step.element != null ? step.element.trim()             : null;
        int    maxItems      = parseMax(step.text);

        List<ClpSection> allSections = TestContext.getClpData(page);
        if (allSections.isEmpty()) {
            throw new RuntimeException(
                    "verifyClpSectionItems: no CLP data for page '" + page
                    + "'. Add a verifyCLP step before this one.");
        }

        // Collect which sections to verify
        List<ClpSection> targets = new ArrayList<>();

        if (sectionFilter != null && !sectionFilter.isEmpty()) {
            // Named section — find it regardless of cardType
            for (ClpSection s : allSections) {
                if (s.sectionTitle.toLowerCase().contains(sectionFilter.toLowerCase())) {
                    targets.add(s);
                    break;
                }
            }
            if (targets.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (ClpSection s : allSections) names.add("\"" + s.sectionTitle + "\"");
                throw new RuntimeException(
                        "verifyClpSectionItems: no section matching \"" + sectionFilter
                        + "\" in " + page + " CLP.\n  Available: " + names);
            }
        } else {
            // Auto-mode — collect every grid-type section
            for (ClpSection s : allSections) {
                if (GRID_TYPES.contains(s.cardType)) {
                    targets.add(s);
                }
            }
            if (targets.isEmpty()) {
                System.out.println("[verifyClpSectionItems] No grid sections found in "
                        + page + " CLP — nothing to verify.");
                return;
            }
        }

        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.printf( "║  verifyClpSectionItems  page=%-24s║%n", page);
        System.out.printf( "║  %d grid section(s) to verify%-25s║%n", targets.size(), "");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        AppiumDriver driver = DriverManager.getDriver();

        int gridsFailed  = 0;
        int gridsChecked = 0;

        for (int gi = 0; gi < targets.size(); gi++) {
            ClpSection section = targets.get(gi);
            gridsChecked++;

            System.out.printf("%n  [Grid %d] \"%s\"  (%s)%n", gi, section.sectionTitle, section.cardType);

            if (section.itemTitles.isEmpty()) {
                System.out.println("    ⚠️  No items in API response — skipping.");
                continue;
            }

            int limit = maxItems < 0
                    ? section.itemTitles.size()
                    : Math.min(maxItems, section.itemTitles.size());

            List<String> passed  = new ArrayList<>();
            List<String> missing = new ArrayList<>();

            for (int i = 0; i < limit; i++) {
                String title = section.itemTitles.get(i);
                if (title == null || title.isBlank()) continue;

                boolean found = findOnScreen(driver, title);
                if (found) {
                    System.out.printf("    ✅ ITEM[%d]  \"%s\"%n", i, title);
                    passed.add(title);
                } else {
                    System.out.printf("    ⚠️  ITEM[%d]  \"%s\"  — not visible%n", i, title);
                    missing.add(title);
                }
            }

            System.out.printf("    Visible: %d / %d%n", passed.size(), limit);

            if (passed.isEmpty()) {
                System.out.printf("    ❌ FAIL — 0 of %d items visible in \"%s\"%n",
                        limit, section.sectionTitle);
                gridsFailed++;
            }
        }

        System.out.println("\n── verifyClpSectionItems Summary ─────────────────────");
        System.out.printf("  Page         : %s%n", page);
        System.out.printf("  Grids checked: %d%n", gridsChecked);
        System.out.printf("  Grids failed : %d%n", gridsFailed);
        System.out.println("──────────────────────────────────────────────────────\n");

        if (gridsFailed > 0) {
            throw new RuntimeException(
                    "verifyClpSectionItems: " + gridsFailed + " of " + gridsChecked
                    + " grid section(s) had 0 visible items on " + page + " CLP.");
        }
    }

    // ── Scroll + search ────────────────────────────────────────────────────────

    private boolean findOnScreen(AppiumDriver driver, String text) {
        for (int s = 0; s <= MAX_SCROLLS; s++) {
            if (isVisible(driver, text)) return true;
            if (s < MAX_SCROLLS) {
                scrollDown(driver);
                try { Thread.sleep(SCROLL_WAIT); } catch (InterruptedException ignored) {}
            }
        }
        return false;
    }

    private boolean isVisible(AppiumDriver driver, String text) {
        try {
            String escaped = text.replace("\"", "\\\"");
            return !driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"" + escaped + "\")")).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void scrollDown(AppiumDriver driver) {
        try {
            Dimension    size    = driver.manage().window().getSize();
            int          centerX = size.width  / 2;
            int          startY  = (int) (size.height * 0.75);
            int          endY    = (int) (size.height * 0.25);
            PointerInput finger  = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence     seq     = new Sequence(finger, 1);
            seq.addAction(finger.createPointerMove(Duration.ZERO,
                    PointerInput.Origin.viewport(), centerX, startY));
            seq.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            seq.addAction(finger.createPointerMove(Duration.ofMillis(400),
                    PointerInput.Origin.viewport(), centerX, endY));
            seq.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(List.of(seq));
        } catch (Exception e) {
            System.out.println("  ⚠️  Scroll failed: " + e.getMessage());
        }
    }

    private int parseMax(String text) {
        if (text == null || text.isBlank()) return -1; // -1 = all items
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) { return -1; }
    }
}
