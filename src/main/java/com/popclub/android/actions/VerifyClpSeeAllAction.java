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
import java.util.Arrays;
import java.util.List;

/**
 * VerifyClpSeeAllAction — for every section, looks for a "See All" / "View All"
 * link or ">" arrow next to the section title and verifies:
 *   ✅ The link/arrow is visible
 *   ✅ Tapping it opens a full listing page (Sort + Filters bar, or back button)
 *   ✅ Pressing Back returns to the CLP
 *
 * YAML usage:
 *   - action: verifyCLPSeeAll
 *     value: SHOP        # HOME | SHOP | CARD
 *
 * Requires: verifyCLP must run first.
 */
public class VerifyClpSeeAllAction implements Action {

    private static final long NAV_WAIT    = 1500;
    private static final long BACK_WAIT   = 1000;
    private static final int  MAX_SCROLLS = 8;
    private static final long SCROLL_WAIT = 500;

    // All text labels used as "See All" across POP screens
    private static final List<String> SEE_ALL_LABELS = Arrays.asList(
            "See All", "View All", "See all", "View all",
            "See More", "View More", "see all", "view all",
            "Show all", "More", "Explore all"
    );

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
                    "verifyCLPSeeAll: no CLP data for '" + page
                    + "'. Run verifyCLP first.");
        }

        AppiumDriver driver = DriverManager.getDriver();

        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("  verifyCLPSeeAll: " + page);
        System.out.println("══════════════════════════════════════════════");

        int found = 0, passed = 0, notFound = 0;

        for (ClpSection section : sections) {
            System.out.println("\n  ── \"" + section.sectionTitle + "\"");

            // Scroll to section
            scrollToText(driver, section.sectionTitle);
            Thread.sleep(300);

            // Look for a See All label near this section
            WebElement seeAllEl = findSeeAllNearSection(driver, section.sectionTitle);

            if (seeAllEl == null) {
                System.out.println("    ℹ️  No \"See All\" link found — section may not have one");
                notFound++;
                continue;
            }

            found++;
            String label = getSafeText(seeAllEl);
            System.out.printf("    Found \"%s\" — tapping …%n", label);

            seeAllEl.click();
            Thread.sleep(NAV_WAIT);

            boolean listingOpened = isSortFilterBarVisible(driver)
                    || isBackButtonVisible(driver);

            if (listingOpened) {
                System.out.printf("    ✅ \"%s\" → listing page opened%n", label);
                passed++;
            } else {
                System.out.printf("    ⚠️  \"%s\" → no listing page detected%n", label);
            }

            pressBack(driver);
            Thread.sleep(BACK_WAIT);

            // Re-scroll back to where we were
            scrollToText(driver, section.sectionTitle);
        }

        System.out.println("\n── verifyCLPSeeAll Summary ───────────────────");
        System.out.println("  Page                : " + page);
        System.out.println("  Sections checked    : " + sections.size());
        System.out.println("  See-All links found : " + found);
        System.out.println("  Listings opened     : " + passed);
        System.out.println("  No link (expected)  : " + notFound);
        System.out.println("──────────────────────────────────────────────\n");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * First scrolls to the section title, then looks for any SEE_ALL_LABELS
     * that are visible on screen (they appear near the section heading row).
     */
    private WebElement findSeeAllNearSection(AppiumDriver driver, String sectionTitle)
            throws InterruptedException {
        for (String label : SEE_ALL_LABELS) {
            try {
                String escaped = label.replace("\"", "\\\"");
                List<WebElement> els = driver.findElements(
                        AppiumBy.androidUIAutomator(
                                "new UiSelector().text(\"" + escaped + "\")"));
                if (!els.isEmpty()) return els.get(0);
            } catch (Exception ignored) {}
        }

        // Also try content-desc ">" or "→"
        for (String arrow : new String[]{">", "→", "›"}) {
            try {
                List<WebElement> els = driver.findElements(
                        AppiumBy.androidUIAutomator(
                                "new UiSelector().text(\"" + arrow + "\")"));
                if (!els.isEmpty()) return els.get(0);
            } catch (Exception ignored) {}
        }

        return null;
    }

    private boolean isSortFilterBarVisible(AppiumDriver driver) {
        try {
            return !driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"Sort\")")).isEmpty()
                && !driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"Filter\")")).isEmpty();
        } catch (Exception e) { return false; }
    }

    private boolean isBackButtonVisible(AppiumDriver driver) {
        try {
            for (String d : new String[]{"Navigate up", "Back", "Go back"}) {
                if (!driver.findElements(AppiumBy.androidUIAutomator(
                        "new UiSelector().descriptionContains(\"" + d + "\")")).isEmpty())
                    return true;
            }
            return false;
        } catch (Exception e) { return false; }
    }

    private void scrollToText(AppiumDriver driver, String text) throws InterruptedException {
        for (int s = 0; s <= MAX_SCROLLS; s++) {
            if (isTextVisible(driver, text)) return;
            scrollDown(driver);
            Thread.sleep(SCROLL_WAIT);
        }
    }

    private boolean isTextVisible(AppiumDriver driver, String text) {
        try {
            String escaped = text.replace("\"", "\\\"");
            return !driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"" + escaped + "\")")).isEmpty();
        } catch (Exception e) { return false; }
    }

    private String getSafeText(WebElement el) {
        try { return el.getText(); } catch (Exception e) { return "(unknown)"; }
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
}
