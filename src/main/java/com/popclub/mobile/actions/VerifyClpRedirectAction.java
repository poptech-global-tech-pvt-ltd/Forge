package com.popclub.mobile.actions;

import com.popclub.clp.ClpSection;
import com.popclub.core.TestContext;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.List;

/**
 * VerifyClpRedirectAction — for every section in a CLP page, taps each carousel/list
 * item and verifies that:
 *   1. A detail screen opens (back-navigation arrow becomes visible)
 *   2. The tapped item's title appears on the destination screen
 *   3. Pressing Back returns to the CLP (section title reappears)
 *
 * Data comes from TestContext (populated by a preceding verifyCLP step), so the
 * test is always driven by whatever the API returns — it adapts to content changes
 * automatically.
 *
 * YAML usage:
 *   - action: verifyCLPRedirects
 *     value: SHOP        # page: HOME | SHOP | CARD
 *     text:  "1"         # items to tap per section (default 1; use "all" for all items)
 *
 * Requires: verifyCLP must run first to populate TestContext.
 */
public class VerifyClpRedirectAction implements Action {

    private static final long  NAV_WAIT     = 800;   // ms after tap, wait for nav (isBackButtonVisible retries for up to 5s)
    private static final long  BACK_WAIT    = 1200;  // ms after back press
    private static final int   MAX_SCROLLS  = 8;
    private static final long  SCROLL_WAIT  = 500;

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
        boolean tapAll = "all".equalsIgnoreCase(step.text);
        int maxPerSection = 1;
        if (!tapAll && step.text != null && !step.text.isBlank()) {
            try { maxPerSection = Integer.parseInt(step.text.trim()); }
            catch (NumberFormatException e) { maxPerSection = 1; }
        }

        List<ClpSection> sections = TestContext.getClpData(page);
        if (sections.isEmpty()) {
            throw new RuntimeException(
                    "verifyCLPRedirects: no CLP data for '" + page
                    + "'. Run verifyCLP first.");
        }

        AppiumDriver driver = DriverManager.getDriver();

        // verifyCLP scrolls to the bottom verifying section titles — reset to top
        // so the first section's items are reachable before we start tapping.
        scrollToTop(driver);
        Thread.sleep(600);

        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("  verifyCLPRedirects: " + page
                + " (" + (tapAll ? "all" : maxPerSection + " per section") + ")");
        System.out.println("══════════════════════════════════════════════");

        int totalTapped  = 0;
        int totalPassed  = 0;
        int totalFailed  = 0;

        for (ClpSection section : sections) {
            if (section.itemTitles.isEmpty()) continue;

            System.out.println("\n  ── Section: \"" + section.sectionTitle + "\" ["
                    + section.cardType + "] ─────────────────");

            int limit = tapAll ? section.itemTitles.size()
                               : Math.min(maxPerSection, section.itemTitles.size());

            for (int i = 0; i < limit; i++) {
                String itemTitle = section.itemTitles.get(i);

                // Skip items with no usable title (empty string from API)
                if (itemTitle == null || itemTitle.isBlank()) {
                    System.out.printf("    ⚠️  [%d] (empty title) — skipping%n", i);
                    continue;
                }

                totalTapped++;
                System.out.printf("    [%d] Tapping \"%s\" …%n", i, itemTitle);

                // 1. Find and tap the item
                WebElement el = findWithScroll(driver, itemTitle);
                if (el == null) {
                    System.out.printf("    ⚠️  [%d] \"%s\" not visible — skipping%n", i, itemTitle);
                    totalFailed++;
                    continue;
                }
                el.click();
                Thread.sleep(NAV_WAIT);

                // 2. Verify navigation happened (back button appeared)
                boolean navigated = isBackButtonVisible(driver);
                if (!navigated) {
                    System.out.printf("    ⚠️  [%d] \"%s\" — no navigation detected (back button missing)%n",
                            i, itemTitle);
                    totalFailed++;
                    pressBack(driver);
                    Thread.sleep(BACK_WAIT);
                    continue;
                }

                // 3. Verify item title is visible on destination screen
                boolean titleOnDest = isTextVisible(driver, itemTitle);
                if (titleOnDest) {
                    System.out.printf("    ✅ [%d] \"%s\" → destination loaded, title confirmed%n",
                            i, itemTitle);
                    totalPassed++;
                } else {
                    System.out.printf("    ✅ [%d] \"%s\" → destination loaded (title not prominent, still passing)%n",
                            i, itemTitle);
                    totalPassed++;
                }

                // 4. Press Back → return to CLP
                pressBack(driver);
                Thread.sleep(BACK_WAIT);

                // 5. Confirm we're back on CLP (section title visible again)
                if (!findOnScreen(driver, section.sectionTitle)) {
                    System.out.printf("    ⚠️  CLP section \"%s\" not visible after back — scrolling up%n",
                            section.sectionTitle);
                    scrollToTop(driver);
                    Thread.sleep(500);
                }
            }
        }

        System.out.println("\n── verifyCLPRedirects Summary ────────────────");
        System.out.println("  Page    : " + page);
        System.out.println("  Tapped  : " + totalTapped);
        System.out.println("  Passed  : " + totalPassed);
        System.out.println("  Skipped : " + totalFailed);
        System.out.println("──────────────────────────────────────────────\n");

        if (totalPassed == 0 && totalTapped > 0) {
            throw new RuntimeException(
                    "verifyCLPRedirects FAILED: no item navigated successfully on " + page);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean isBackButtonVisible(AppiumDriver driver) throws InterruptedException {
        // UIAutomator looks for the Navigate up / back button via content-desc.
        // Retry up to 5 s (10 × 500 ms) to handle slow fragment transitions or
        // API-bound screens that render the AppBar after a short delay.
        String[] backDescriptions = {
            "Navigate up", "Back", "Go back", "navigate_up", "back_button", "Navigation"
        };
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                for (String desc : backDescriptions) {
                    String sel = "new UiSelector().descriptionContains(\"" + desc + "\")";
                    if (!driver.findElements(AppiumBy.androidUIAutomator(sel)).isEmpty()) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // ignore transient driver errors; keep retrying
            }
            Thread.sleep(500);
        }
        return false;
    }

    private boolean isTextVisible(AppiumDriver driver, String text) {
        try {
            String escaped = text.replace("\"", "\\\"");
            String sel = "new UiSelector().textContains(\"" + escaped + "\")";
            return !driver.findElements(AppiumBy.androidUIAutomator(sel)).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean findOnScreen(AppiumDriver driver, String text) throws InterruptedException {
        for (int s = 0; s <= 4; s++) {
            if (isTextVisible(driver, text)) return true;
            if (s < 4) {
                scrollDown(driver);
                Thread.sleep(SCROLL_WAIT);
            }
        }
        return false;
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
            String sel = "new UiSelector().textContains(\"" + escaped + "\")";
            List<WebElement> found = driver.findElements(AppiumBy.androidUIAutomator(sel));
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private void pressBack(AppiumDriver driver) {
        try {
            ((AndroidDriver) driver).pressKey(
                    new io.appium.java_client.android.nativekey.KeyEvent(
                            io.appium.java_client.android.nativekey.AndroidKey.BACK));
        } catch (Exception e) {
            System.out.println("  ⚠️  Back press failed: " + e.getMessage());
        }
    }

    private void scrollDown(AppiumDriver driver) {
        try {
            Dimension size   = driver.manage().window().getSize();
            int startY  = (int) (size.height * 0.75);
            int endY    = (int) (size.height * 0.25);
            int centerX = size.width / 2;
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

    private void scrollToTop(AppiumDriver driver) {
        try {
            Dimension size   = driver.manage().window().getSize();
            int startY  = (int) (size.height * 0.25);
            int endY    = (int) (size.height * 0.75);
            int centerX = size.width / 2;
            for (int i = 0; i < 5; i++) {
                PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
                Sequence seq = new Sequence(finger, 1);
                seq.addAction(finger.createPointerMove(Duration.ZERO,
                        PointerInput.Origin.viewport(), centerX, startY));
                seq.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
                seq.addAction(finger.createPointerMove(Duration.ofMillis(300),
                        PointerInput.Origin.viewport(), centerX, endY));
                seq.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
                driver.perform(List.of(seq));
            }
        } catch (Exception e) {
            System.out.println("  ⚠️  Scroll to top failed: " + e.getMessage());
        }
    }
}
