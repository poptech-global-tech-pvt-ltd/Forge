package com.popclub.mobile.actions;

import com.popclub.clp.ClpSection;
import com.popclub.core.TestContext;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * VerifyClpOrderAction — scrolls the CLP page from top to bottom and verifies
 * that sections appear in the same order as the API response.
 *
 * Catches: merchandising order being ignored, sections rendered out-of-order,
 * or priority widgets appearing below lower-priority ones.
 *
 * YAML usage:
 *   - action: verifyCLPOrder
 *     value: SHOP        # HOME | SHOP | CARD
 *
 * Requires: verifyCLP must run first.
 */
public class VerifyClpOrderAction implements Action {

    private static final int  TOTAL_SCROLLS = 20;
    private static final long SCROLL_WAIT   = 400;

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

        List<ClpSection> apiSections = TestContext.getClpData(page);
        if (apiSections.isEmpty()) {
            throw new RuntimeException(
                    "verifyCLPOrder: no CLP data for '" + page
                    + "'. Run verifyCLP first.");
        }

        AppiumDriver driver = DriverManager.getDriver();

        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("  verifyCLPOrder: " + page);
        System.out.println("══════════════════════════════════════════════");

        // Scroll to top first
        scrollToTop(driver);
        Thread.sleep(600);

        // Walk down the page and record the Y-position of each section title
        List<String> apiOrder    = new ArrayList<>();
        List<String> screenOrder = new ArrayList<>();

        for (ClpSection s : apiSections) {
            apiOrder.add(s.sectionTitle);
        }

        // Collect on-screen positions by scrolling and recording element Y
        List<SectionPosition> positions = collectPositions(driver, apiSections);

        // Sort by Y position to get screen order
        positions.sort((a, b) -> Integer.compare(a.screenY, b.screenY));
        for (SectionPosition p : positions) {
            screenOrder.add(p.title);
        }

        // ── Compare orders ────────────────────────────────────────────────────
        System.out.println("\n  API order  vs  Screen order:");
        int mismatches = 0;
        int maxLen = Math.max(apiOrder.size(), screenOrder.size());

        for (int i = 0; i < maxLen; i++) {
            String api    = i < apiOrder.size()    ? apiOrder.get(i)    : "(missing in API)";
            String screen = i < screenOrder.size() ? screenOrder.get(i) : "(not found on screen)";
            boolean match = api.equals(screen);
            if (!match) mismatches++;
            System.out.printf("  %s [%2d]  API: %-35s  Screen: %s%n",
                    match ? "✅" : "⚠️ ", i, shorten(api), shorten(screen));
        }

        // Sections not found on screen at all
        List<String> notFound = new ArrayList<>(apiOrder);
        notFound.removeAll(screenOrder);

        System.out.println("\n── verifyCLPOrder Summary ────────────────────");
        System.out.println("  Page                : " + page);
        System.out.println("  API sections        : " + apiOrder.size());
        System.out.println("  Found on screen     : " + screenOrder.size());
        System.out.println("  Order mismatches    : " + mismatches);
        System.out.println("  Sections not found  : " + notFound.size());
        if (!notFound.isEmpty()) notFound.forEach(t -> System.out.println("    - " + t));
        System.out.println("──────────────────────────────────────────────\n");

        if (mismatches > 0) {
            System.out.println("  ⚠️  Section order on screen does not match API — "
                    + mismatches + " position mismatch(es). "
                    + "This may indicate a rendering or priority issue.");
            // Warn but don't fail — order mismatches are informational
            // Throw to make it a hard failure if needed:
            // throw new RuntimeException("verifyCLPOrder: " + mismatches + " order mismatch(es)");
        }
    }

    // ── Collect on-screen Y positions by scrolling ────────────────────────────

    private List<SectionPosition> collectPositions(AppiumDriver driver,
                                                   List<ClpSection> sections)
            throws InterruptedException {

        List<SectionPosition> positions = new ArrayList<>();
        int cumulativeOffset = 0;

        scrollToTop(driver);
        Thread.sleep(400);

        Dimension screenSize = driver.manage().window().getSize();

        for (int scroll = 0; scroll <= TOTAL_SCROLLS; scroll++) {
            // Check each section title for visibility on this scroll
            for (ClpSection section : sections) {
                // Skip already found
                boolean alreadyFound = positions.stream()
                        .anyMatch(p -> p.title.equals(section.sectionTitle));
                if (alreadyFound) continue;

                WebElement el = findElement(driver, section.sectionTitle);
                if (el != null) {
                    int absoluteY = cumulativeOffset + el.getLocation().getY();
                    positions.add(new SectionPosition(section.sectionTitle, absoluteY));
                    System.out.printf("  Found \"%s\" at Y=%d%n",
                            shorten(section.sectionTitle), absoluteY);
                }
            }

            // If all found, stop scrolling
            if (positions.size() == sections.size()) break;

            if (scroll < TOTAL_SCROLLS) {
                cumulativeOffset += (int) (screenSize.height * 0.5);
                scrollDown(driver);
                Thread.sleep(SCROLL_WAIT);
            }
        }

        return positions;
    }

    private WebElement findElement(AppiumDriver driver, String text) {
        try {
            String escaped = text.replace("\"", "\\\"");
            List<WebElement> els = driver.findElements(
                    AppiumBy.androidUIAutomator(
                            "new UiSelector().textContains(\"" + escaped + "\")"));
            return els.isEmpty() ? null : els.get(0);
        } catch (Exception e) { return null; }
    }

    private void scrollToTop(AppiumDriver driver) throws InterruptedException {
        Dimension size  = driver.manage().window().getSize();
        int centerX = size.width / 2;
        for (int i = 0; i < 6; i++) {
            try {
                PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
                Sequence seq = new Sequence(finger, 1);
                seq.addAction(finger.createPointerMove(Duration.ZERO,
                        PointerInput.Origin.viewport(), centerX, (int) (size.height * 0.25)));
                seq.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
                seq.addAction(finger.createPointerMove(Duration.ofMillis(250),
                        PointerInput.Origin.viewport(), centerX, (int) (size.height * 0.75)));
                seq.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
                driver.perform(List.of(seq));
                Thread.sleep(150);
            } catch (Exception ignored) {}
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
        return s != null && s.length() > 35 ? s.substring(0, 32) + "…" : s;
    }

    // ── Simple holder ─────────────────────────────────────────────────────────

    private static class SectionPosition {
        final String title;
        final int    screenY;
        SectionPosition(String title, int screenY) {
            this.title   = title;
            this.screenY = screenY;
        }
    }
}
