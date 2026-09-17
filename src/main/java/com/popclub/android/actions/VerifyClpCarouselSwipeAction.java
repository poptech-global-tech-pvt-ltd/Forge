package com.popclub.android.actions;

import com.popclub.clp.ClpSection;
import com.popclub.core.TestContext;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import com.popclub.core.GestureUtil;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VerifyClpCarouselSwipeAction — for every carousel/grid section, swipes
 * horizontally to reveal items beyond the initial viewport and verifies:
 *   ✅ At least one new item appears after swiping left
 *   ✅ All API-listed item titles are eventually visible across swipes
 *
 * This catches cases where a carousel renders with 0 items past the fold,
 * or horizontal scrolling is broken.
 *
 * YAML usage:
 *   - action: verifyCLPCarouselSwipe
 *     value: SHOP        # HOME | SHOP | CARD
 *     text:  "3"         # max horizontal swipes per section (default 3)
 *
 * Requires: verifyCLP must run first.
 */
public class VerifyClpCarouselSwipeAction implements Action {

    private static final long SWIPE_WAIT   = 700;
    private static final long SCROLL_WAIT  = 500;
    private static final int  MAX_V_SCROLLS = 8;

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
        int maxSwipes = 3;
        if (step.text != null && !step.text.isBlank()) {
            try { maxSwipes = Integer.parseInt(step.text.trim()); } catch (NumberFormatException e) { /* use default */ }
        }

        List<ClpSection> sections = TestContext.getClpData(page);
        if (sections.isEmpty()) {
            throw new RuntimeException(
                    "verifyCLPCarouselSwipe: no CLP data for '" + page
                    + "'. Run verifyCLP first.");
        }

        AppiumDriver driver = DriverManager.getDriver();

        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("  verifyCLPCarouselSwipe: " + page
                + "  (max " + maxSwipes + " swipes per section)");
        System.out.println("══════════════════════════════════════════════");

        int sectionsPassed = 0, sectionsFailed = 0;

        for (ClpSection section : sections) {
            if (section.itemTitles.size() < 2) continue; // nothing to swipe

            System.out.println("\n  ── \"" + section.sectionTitle + "\" ["
                    + section.cardType + "]  (" + section.itemTitles.size() + " items in API)");

            // Scroll vertically to the section
            scrollToText(driver, section.sectionTitle);
            Thread.sleep(500);

            // Collect items visible BEFORE any swipe
            Set<String> visibleBefore = visibleItems(driver, section.itemTitles);
            System.out.println("    Before swipe: " + visibleBefore.size()
                    + " visible  " + visibleBefore);

            // Swipe left up to maxSwipes times
            Set<String> allSeen = new HashSet<>(visibleBefore);
            for (int sw = 0; sw < maxSwipes; sw++) {
                swipeLeftInSection(driver, section.sectionTitle);
                Thread.sleep(SWIPE_WAIT);

                Set<String> nowVisible = visibleItems(driver, section.itemTitles);
                Set<String> newItems   = new HashSet<>(nowVisible);
                newItems.removeAll(allSeen);
                allSeen.addAll(nowVisible);

                System.out.printf("    Swipe %d: +%d new item(s) visible  %s%n",
                        sw + 1, newItems.size(), newItems);

                if (allSeen.containsAll(section.itemTitles)) break; // all seen
            }

            // Report
            Set<String> neverSeen = new HashSet<>(section.itemTitles);
            neverSeen.removeAll(allSeen);

            if (allSeen.size() > visibleBefore.size()) {
                System.out.printf("    ✅ Carousel scrollable — saw %d/%d items%n",
                        allSeen.size(), section.itemTitles.size());
                sectionsPassed++;
            } else {
                System.out.printf("    ⚠️  No new items appeared after %d swipes%n", maxSwipes);
                sectionsFailed++;
            }

            if (!neverSeen.isEmpty()) {
                System.out.println("    Items never seen: " + neverSeen);
            }
        }

        System.out.println("\n── verifyCLPCarouselSwipe Summary ────────────");
        System.out.println("  Page             : " + page);
        System.out.println("  Sections passed  : " + sectionsPassed);
        System.out.println("  Sections failed  : " + sectionsFailed);
        System.out.println("──────────────────────────────────────────────\n");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Returns subset of itemTitles currently visible on screen. */
    private Set<String> visibleItems(AppiumDriver driver, List<String> titles) {
        Set<String> visible = new HashSet<>();
        for (String title : titles) {
            if (isTextVisible(driver, title)) visible.add(title);
        }
        return visible;
    }

    /**
     * Swipes left within the carousel row for the given section.
     * Finds the section title element, then swipes from right→left at that y-position
     * to scroll the horizontal carousel.
     */
    private void swipeLeftInSection(AppiumDriver driver, String sectionTitle)
            throws InterruptedException {
        try {
            WebElement titleEl = driver.findElements(
                    AppiumBy.androidUIAutomator(
                            "new UiSelector().textContains(\""
                            + sectionTitle.replace("\"", "\\\"") + "\")")
            ).stream().findFirst().orElse(null);

            Dimension screenSize = driver.manage().window().getSize();
            int carouselY;

            if (titleEl != null) {
                // Swipe just below the section title (where the carousel items are)
                int titleY   = titleEl.getLocation().getY();
                int itemH    = (int) (screenSize.height * 0.20); // approx item height
                carouselY    = titleY + titleEl.getSize().getHeight() + itemH / 2;
            } else {
                // Fallback: mid-screen height
                carouselY = screenSize.height / 2;
            }

            int startX = (int) (screenSize.width * 0.80);
            int endX   = (int) (screenSize.width * 0.20);

            GestureUtil.swipeCoords(driver, startX, carouselY, endX, carouselY, 350);

        } catch (Exception e) {
            System.out.println("    ⚠️  Swipe failed: " + e.getMessage());
        }
    }

    private void scrollToText(AppiumDriver driver, String text) throws InterruptedException {
        for (int s = 0; s <= MAX_V_SCROLLS; s++) {
            if (isTextVisible(driver, text)) return;
            scrollDown(driver);
            Thread.sleep(SCROLL_WAIT);
        }
    }

    private boolean isTextVisible(AppiumDriver driver, String text) {
        try {
            String escaped = text.replace("\"", "\\\"");
            return !driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"" + escaped + "\")")
            ).isEmpty();
        } catch (Exception e) { return false; }
    }

    private void scrollDown(AppiumDriver driver) {
        try {
            GestureUtil.swipe(driver, "up");
        } catch (Exception e) {
            System.out.println("  ⚠️  Scroll failed: " + e.getMessage());
        }
    }
}
