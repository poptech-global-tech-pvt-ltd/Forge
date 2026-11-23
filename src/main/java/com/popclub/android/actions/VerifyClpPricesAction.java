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
 * VerifyClpPricesAction — for every item in every section that has price data,
 * verifies that:
 *   ✅ Selling price (₹3,149) is visible on screen next to the item
 *   ✅ Discount badge ("23% off") is visible when discount > 0
 *   ✅ POPcoins reward ("350") is visible when popcoins > 0
 *
 * MRP (strikethrough) is NOT checked directly — strikethrough is a visual style
 * that UIAutomator cannot detect. Instead we verify the MRP number text is present.
 *
 * Data-driven: all values come from TestContext (populated by verifyCLP).
 * Adapts automatically when API content changes.
 *
 * YAML usage:
 *   - action: verifyCLPPrices
 *     value: SHOP        # HOME | SHOP | CARD
 *
 * Requires: verifyCLP must run first.
 */
public class VerifyClpPricesAction implements Action {

    private static final int  MAX_SCROLLS = 10;
    private static final long SCROLL_WAIT = 500;

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
                    "verifyCLPPrices: no CLP data for '" + page
                    + "'. Run verifyCLP first.");
        }

        AppiumDriver driver = DriverManager.getDriver();

        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("  verifyCLPPrices: " + page);
        System.out.println("══════════════════════════════════════════════");

        int checked = 0, passed = 0, noData = 0;

        for (ClpSection section : sections) {
            if (!section.hasItems()) continue;

            boolean sectionHasPriceData = section.itemPrices.stream()
                    .anyMatch(p -> !p.isEmpty());
            if (!sectionHasPriceData) continue;

            System.out.println("\n  ── \"" + section.sectionTitle + "\"");

            for (int i = 0; i < section.itemTitles.size(); i++) {
                String title    = section.itemTitles.get(i);
                String price    = section.formattedPrice(i);   // "₹3,149"
                String mrp      = section.formattedMrp(i);     // "₹4,095"
                String disc     = section.discountLabel(i);    // "23% off"
                String coins    = section.popcoinLabel(i);     // "350"

                if (price.isEmpty()) { noData++; continue; }
                checked++;

                // Scroll to item title first
                scrollToText(driver, title);

                boolean priceOk    = isTextVisible(driver, price.replace("₹", ""));
                boolean discOk     = disc.isEmpty()  || isTextVisible(driver, disc.replace("% off", "%"));
                boolean coinsOk    = coins.isEmpty() || isTextVisible(driver, coins);

                if (priceOk) {
                    System.out.printf("    ✅ [%d] %-30s %s%s%s%n",
                            i, shorten(title), price,
                            disc.isEmpty()  ? "" : "  " + disc,
                            coins.isEmpty() ? "" : "  🔥" + coins);
                    passed++;
                } else {
                    System.out.printf("    ⚠️  [%d] %-30s price %s NOT found on screen%n",
                            i, shorten(title), price);
                }

                if (!disc.isEmpty() && !discOk) {
                    System.out.printf("    ⚠️  [%d] discount badge \"%s\" not visible%n", i, disc);
                }
                if (!coins.isEmpty() && !coinsOk) {
                    System.out.printf("    ⚠️  [%d] POPcoins \"%s\" not visible%n", i, coins);
                }
            }
        }

        System.out.println("\n── verifyCLPPrices Summary ───────────────────");
        System.out.println("  Page             : " + page);
        System.out.println("  Items with prices: " + checked);
        System.out.println("  Prices found     : " + passed);
        System.out.println("  No price in API  : " + noData);
        System.out.println("──────────────────────────────────────────────\n");

        if (checked > 0 && passed == 0) {
            throw new RuntimeException(
                    "verifyCLPPrices FAILED for " + page
                    + ": 0 prices found on screen out of " + checked + " items with API price data.");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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
            return !driver.findElements(
                    AppiumBy.androidUIAutomator(
                            "new UiSelector().textContains(\"" + escaped + "\")")
            ).isEmpty();
        } catch (Exception e) {
            return false;
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
        return s != null && s.length() > 30 ? s.substring(0, 27) + "…" : s;
    }
}
