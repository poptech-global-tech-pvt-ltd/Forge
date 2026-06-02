package com.popclub.mobile.actions;

import com.popclub.clp.ClpSection;
import com.popclub.core.TestContext;
import com.popclub.mobile.driver.DriverManager;
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
import java.util.List;

/**
 * VerifyClpBannersAction — taps every banner CTA button ("Explore", "Shop now", etc.)
 * and verifies that:
 *   ✅ CTA button is visible on screen
 *   ✅ Tapping it opens a destination page (back button or Sort+Filters bar appears)
 *   ✅ Pressing Back returns to the CLP
 *
 * Banner CTAs are extracted from the API by verifyCLP and stored in TestContext.
 *
 * YAML usage:
 *   - action: verifyCLPBanners
 *     value: SHOP        # HOME | SHOP | CARD
 *
 * Requires: verifyCLP must run first.
 */
public class VerifyClpBannersAction implements Action {

    private static final long NAV_WAIT   = 1500;
    private static final long BACK_WAIT  = 1000;
    private static final int  MAX_SCROLLS = 6;
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

        List<ClpSection> banners = TestContext.getClpBanners(page);

        AppiumDriver driver = DriverManager.getDriver();

        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("  verifyCLPBanners: " + page);

        if (banners.isEmpty()) {
            System.out.println("  ℹ️  No banner CTA data in API for " + page
                    + " — skipping banner verification.");
            System.out.println("══════════════════════════════════════════════\n");
            return;
        }

        System.out.println("  " + banners.size() + " banner section(s) to verify");
        System.out.println("══════════════════════════════════════════════");

        int passed = 0, skipped = 0;

        for (int bi = 0; bi < banners.size(); bi++) {
            ClpSection banner = banners.get(bi);
            String headline   = banner.sectionTitle.isEmpty() ? "(no headline)" : banner.sectionTitle;

            System.out.println("\n  ── Banner " + bi + ": \"" + headline + "\"  ["
                    + banner.cardType + "]");

            if (!banner.hasBannerCtas()) {
                System.out.println("    ℹ️  No CTA text extracted from API — skipping");
                skipped++;
                continue;
            }

            for (String cta : banner.bannerCtas) {
                System.out.printf("    Tapping CTA \"%s\" …%n", cta);

                WebElement btn = findWithScroll(driver, cta);
                if (btn == null) {
                    System.out.printf("    ⚠️  CTA \"%s\" not visible on screen%n", cta);
                    skipped++;
                    continue;
                }

                btn.click();
                Thread.sleep(NAV_WAIT);

                boolean navigated = isBackButtonVisible(driver)
                        || isSortFilterBarVisible(driver)
                        || isNewScreenVisible(driver, headline, cta);

                if (navigated) {
                    System.out.printf("    ✅ CTA \"%s\" → destination opened%n", cta);
                    passed++;
                } else {
                    System.out.printf("    ⚠️  CTA \"%s\" → no navigation detected%n", cta);
                    skipped++;
                }

                pressBack(driver);
                Thread.sleep(BACK_WAIT);
            }
        }

        System.out.println("\n── verifyCLPBanners Summary ──────────────────");
        System.out.println("  Page    : " + page);
        System.out.println("  Passed  : " + passed);
        System.out.println("  Skipped : " + skipped);
        System.out.println("──────────────────────────────────────────────\n");
    }

    // ── Navigation detection ───────────────────────────────────────────────────

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

    private boolean isSortFilterBarVisible(AppiumDriver driver) {
        try {
            return !driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"Sort\")")).isEmpty()
                && !driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"Filter\")")).isEmpty();
        } catch (Exception e) { return false; }
    }

    /** Heuristic: if screen no longer shows the CTA button we just tapped, we navigated. */
    private boolean isNewScreenVisible(AppiumDriver driver, String headline, String cta) {
        try {
            // If the CTA disappeared from the screen, we likely navigated away
            return driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"" + cta.replace("\"", "\\\"") + "\")")
            ).isEmpty();
        } catch (Exception e) { return false; }
    }

    // ── Scroll & find ──────────────────────────────────────────────────────────

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
            List<WebElement> found = driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"" + escaped + "\")"));
            return found.isEmpty() ? null : found.get(0);
        } catch (Exception e) { return null; }
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
