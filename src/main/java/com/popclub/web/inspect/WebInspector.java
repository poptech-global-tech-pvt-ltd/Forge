package com.popclub.web.inspect;

import com.microsoft.playwright.*;
import org.testng.annotations.Test;
import java.util.*;

public class WebInspector {

    @Test
    public void debugConsentFlow() throws Exception {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(false)
            );
            Page page = browser.newPage();
            page.navigate("https://popcard-reskin-sit.popclub.co.in/otp");
            page.waitForLoadState();
            Thread.sleep(2000);

            page.fill("input[name='phone']", "9876543210");
            Thread.sleep(2000);

            // Check checkbox existence before toggle
            List<ElementHandle> checkboxesBefore = page.querySelectorAll("input[type='checkbox']");
            System.out.println("Checkboxes BEFORE toggle click: " + checkboxesBefore.size());
            for (ElementHandle cb : checkboxesBefore) {
                System.out.println("  name=" + cb.getAttribute("name") + " visible=" + cb.isVisible());
            }

            // Click toggle using Playwright (NOT JS)
            ElementHandle toggle = page.waitForSelector(".consent-toggle-wrapper .toggle-strip");
            System.out.println("\nToggle visible before click: " + toggle.isVisible());
            toggle.scrollIntoViewIfNeeded();
            toggle.click();
            Thread.sleep(1000);

            // Check checkbox existence after toggle
            List<ElementHandle> checkboxesAfter = page.querySelectorAll("input[type='checkbox']");
            System.out.println("\nCheckboxes AFTER toggle click: " + checkboxesAfter.size());
            for (ElementHandle cb : checkboxesAfter) {
                System.out.println("  name=" + cb.getAttribute("name") + " visible=" + cb.isVisible());
            }

            // Print DOM around toggle-content-wrapper
            String toggleHtml = (String) page.evaluate(
                "document.querySelector('.consent-toggle-wrapper').innerHTML");
            System.out.println("\nToggle wrapper HTML:\n" + toggleHtml.substring(0, Math.min(2000, toggleHtml.length())));

            browser.close();
        }
    }
}
