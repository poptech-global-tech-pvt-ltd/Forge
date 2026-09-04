package com.popclub.android.actions;

import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dumps the full XCUITest / UIAutomator2 page source to reports/source_dump.xml
 * and prints all accessibilityId / name values found.
 *
 * Usage in YAML:   - action: getSource
 */
public class GetSourceAction implements Action {

    private static final Pattern ATTR = Pattern.compile(
            "(?:name|label|accessibilityIdentifier|content-desc|resource-id)=\"([^\"]+)\"");

    @Override
    public void perform(Step step) {
        AppiumDriver driver = DriverManager.getDriver();
        System.out.println("[GetSource] Fetching page source...");

        String source;
        try {
            source = driver.getPageSource();
        } catch (Exception e) {
            System.out.println("[GetSource] ❌ Failed to get page source: " + e.getMessage());
            return;
        }

        try {
            Path out = Path.of("reports/source_dump.xml");
            Files.createDirectories(out.getParent());
            Files.writeString(out, source);
            System.out.println("[GetSource] ✅ Saved " + source.length() + " chars → " + out.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("[GetSource] ⚠️  Could not save file: " + e.getMessage());
        }

        // Print unique non-empty identifiers from the tree
        System.out.println("\n[GetSource] ── Accessibility IDs / names on screen ──────────────");
        Matcher m = ATTR.matcher(source);
        m.results()
         .map(r -> r.group(1).trim())
         .filter(v -> !v.isEmpty())
         .distinct()
         .sorted()
         .forEach(v -> System.out.println("  • " + v));
        System.out.println("[GetSource] ─────────────────────────────────────────────────────\n");
    }
}
