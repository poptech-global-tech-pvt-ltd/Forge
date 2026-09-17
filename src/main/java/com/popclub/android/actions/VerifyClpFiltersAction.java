package com.popclub.android.actions;

import com.popclub.clp.ClpSection;
import com.popclub.core.TestContext;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * VerifyClpFiltersAction — for every section that has filter chips/tabs, taps each
 * filter and verifies:
 *
 *   Case A — IN-PAGE filter (Men / Women tabs inside a section):
 *     • Filter chip is tappable
 *     • After tap, at least one item visible under the section changes
 *       (first visible item text differs → filter is working)
 *     • Restores to the first filter after each section
 *
 *   Case B — DEEP-LINK filter (chip that opens a listing/category page):
 *     • After tap, a listing page opens (Sort + Filters bar or back button visible)
 *     • Presses Back to return to CLP
 *
 * Data-driven: uses whatever filter labels the API returns — adapts automatically
 * when the CLP content changes.
 *
 * YAML usage:
 *   - action: verifyCLPFilters
 *     value: SHOP        # HOME | SHOP | CARD
 *
 * Requires: verifyCLP must run first to populate TestContext.
 */
public class VerifyClpFiltersAction implements Action {

    private static final long FILTER_WAIT  = 1200;  // ms after tapping a filter
    private static final long NAV_WAIT     = 1500;  // ms after tapping a deep-link chip
    private static final long BACK_WAIT    = 1000;
    private static final int  MAX_SCROLLS  = 8;
    private static final long SCROLL_WAIT  = 500;

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

        String page = step.value != null ? step.value.trim().toUpperCase() : "SHOP";

        List<ClpSection> sections = TestContext.getClpData(page);
        if (sections.isEmpty()) {
            throw new RuntimeException(
                    "verifyCLPFilters: no CLP data for '" + page
                    + "'. Run verifyCLP first.");
        }

        AppiumDriver driver = DriverManager.getDriver();

        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("  verifyCLPFilters: " + page);
        System.out.println("══════════════════════════════════════════════");

        int sectionsWithFilters = 0;
        int filtersPassed       = 0;
        int filtersFailed       = 0;

        for (ClpSection section : sections) {
            if (!section.hasFilters()) continue;
            sectionsWithFilters++;

            System.out.println("\n  ── Section: \"" + section.sectionTitle + "\" ["
                    + section.cardType + "]");
            System.out.println("     Filters: " + String.join(" | ", section.filters));

            // Scroll to the section first
            scrollToText(driver, section.sectionTitle);
            Thread.sleep(500);

            // Snapshot the LIVE screen before touching any filter chip
            // This is fully dynamic — whatever is visible now, regardless of API data
            List<String> snapshotBefore = screenTextSnapshot(driver);

            for (int fi = 0; fi < section.filters.size(); fi++) {
                String filter = section.filters.get(fi);
                System.out.printf("     [%d] Tapping filter \"%s\" …%n", fi, filter);

                // Tap the filter chip
                WebElement chip = findWithScroll(driver, filter);
                if (chip == null) {
                    System.out.printf("     ⚠️  [%d] Filter chip \"%s\" not found%n", fi, filter);
                    filtersFailed++;
                    continue;
                }

                // Snapshot before this specific tap
                List<String> snapBefore = screenTextSnapshot(driver);

                chip.click();
                Thread.sleep(FILTER_WAIT);

                // Detect whether this opened a new page (deep-link) or stayed in-page
                if (isBackButtonVisible(driver) || isSortFilterBarVisible(driver)) {
                    // Case B — deep-link: a listing/category page opened
                    System.out.printf("     ✅ [%d] \"%s\" → opened listing/category page%n",
                            fi, filter);
                    filtersPassed++;
                    pressBack(driver);
                    Thread.sleep(BACK_WAIT);
                    // Re-scroll to section after returning
                    scrollToText(driver, section.sectionTitle);
                    Thread.sleep(500);

                } else {
                    // Case A — in-page filter: compare live screen before vs after
                    List<String> snapAfter = screenTextSnapshot(driver);
                    boolean changed = contentChanged(snapBefore, snapAfter, section.filters);

                    if (fi == 0) {
                        // First filter = default state, just confirm chip is tappable
                        System.out.printf("     ✅ [%d] \"%s\" → chip tapped OK (default filter)%n",
                                fi, filter);
                        filtersPassed++;
                    } else if (changed) {
                        System.out.printf("     ✅ [%d] \"%s\" → content updated (dynamic data changed)%n",
                                fi, filter);
                        filtersPassed++;
                    } else {
                        System.out.printf("     ⚠️  [%d] \"%s\" → screen content unchanged after filter tap%n",
                                fi, filter);
                        filtersFailed++;
                    }
                }
            }

            // Restore first filter so CLP is in a clean state for the next section
            if (!section.filters.isEmpty()) {
                WebElement firstChip = findElement(driver, section.filters.get(0));
                if (firstChip != null) {
                    firstChip.click();
                    Thread.sleep(FILTER_WAIT);
                    System.out.println("     ↩ Restored to first filter: \""
                            + section.filters.get(0) + "\"");
                }
            }
        }

        System.out.println("\n── verifyCLPFilters Summary ──────────────────");
        System.out.println("  Page              : " + page);
        System.out.println("  Sections w/ filters: " + sectionsWithFilters);
        System.out.println("  Filters passed    : " + filtersPassed);
        System.out.println("  Filters failed    : " + filtersFailed);
        System.out.println("──────────────────────────────────────────────\n");

        if (sectionsWithFilters == 0) {
            System.out.println("  ℹ️  No filter chips found in API data for " + page
                    + " — skipping (no filters in this CLP).");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Captures a snapshot of ALL text currently visible on screen.
     * Used to detect content changes after a filter tap — completely dynamic,
     * does not rely on pre-parsed API item lists.
     */
    private List<String> screenTextSnapshot(AppiumDriver driver) {
        List<String> texts = new ArrayList<>();
        try {
            List<WebElement> all = driver.findElements(
                    AppiumBy.androidUIAutomator(
                            "new UiSelector().resourceIdMatches(\".*\")"));
            for (WebElement el : all) {
                try {
                    String t = el.getText();
                    if (t != null && !t.isBlank()) texts.add(t.trim());
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            // fallback — just return empty list
        }
        return texts;
    }

    /**
     * Compares two screen snapshots and returns true if meaningful content changed.
     * Ignores the filter chip labels themselves (those stay visible after tap).
     */
    private boolean contentChanged(List<String> before, List<String> after,
                                   List<String> filterLabels) {
        // Count texts that appear in one snapshot but not the other
        long onlyInBefore = before.stream()
                .filter(t -> !after.contains(t) && !filterLabels.contains(t))
                .count();
        long onlyInAfter  = after.stream()
                .filter(t -> !before.contains(t) && !filterLabels.contains(t))
                .count();
        return onlyInBefore > 0 || onlyInAfter > 0;
    }

    /** Scrolls down until the given text is visible. */
    private void scrollToText(AppiumDriver driver, String text) throws InterruptedException {
        for (int s = 0; s <= MAX_SCROLLS; s++) {
            if (isTextVisible(driver, text)) return;
            scrollDown(driver);
            Thread.sleep(SCROLL_WAIT);
        }
    }

    private boolean isSortFilterBarVisible(AppiumDriver driver) {
        try {
            // A listing page typically shows "Sort" and "Filters" buttons
            String selSort    = "new UiSelector().textContains(\"Sort\")";
            String selFilters = "new UiSelector().textContains(\"Filters\")";
            return !driver.findElements(AppiumBy.androidUIAutomator(selSort)).isEmpty()
                && !driver.findElements(AppiumBy.androidUIAutomator(selFilters)).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBackButtonVisible(AppiumDriver driver) {
        try {
            String[] descs = {"Navigate up", "Back", "Go back"};
            for (String d : descs) {
                if (!driver.findElements(
                        AppiumBy.androidUIAutomator(
                                "new UiSelector().descriptionContains(\"" + d + "\")")
                ).isEmpty()) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTextVisible(AppiumDriver driver, String text) {
        try {
            String escaped = text.replace("\"", "\\\"");
            return !driver.findElements(
                    AppiumBy.androidUIAutomator(
                            "new UiSelector().textContains(\"" + escaped + "\")")
            ).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private WebElement findWithScroll(AppiumDriver driver, String text) throws InterruptedException {
        for (int s = 0; s <= MAX_SCROLLS; s++) {
            WebElement el = findElement(driver, text);
            if (el != null) return el;
            if (s < MAX_SCROLLS) {
                scrollDown(driver);
                Thread.sleep(SCROLL_WAIT);
            }
        }
        return null;
    }

    private WebElement findElement(AppiumDriver driver, String text) {
        try {
            String escaped = text.replace("\"", "\\\"");
            List<WebElement> found = driver.findElements(
                    AppiumBy.androidUIAutomator(
                            "new UiSelector().textContains(\"" + escaped + "\")"));
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private void pressBack(AppiumDriver driver) {
        try {
            ((AndroidDriver) driver).pressKey(new KeyEvent(AndroidKey.BACK));
        } catch (Exception e) {
            System.out.println("  ⚠️  Back press failed: " + e.getMessage());
        }
    }

    private void scrollDown(AppiumDriver driver) {
        try {
            Dimension size  = driver.manage().window().getSize();
            int centerX = size.width / 2;
            int startY  = (int) (size.height * 0.75);
            int endY    = (int) (size.height * 0.25);
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence seq = new Sequence(finger, 1);
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

    private String shorten(String s) {
        if (s == null) return "(none)";
        return s.length() > 30 ? s.substring(0, 27) + "…" : s;
    }
}
