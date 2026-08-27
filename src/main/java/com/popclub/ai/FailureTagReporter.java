package com.popclub.ai;

import com.popclub.ai.app.QaTagAnalyzer;
import com.popclub.parser.XmlElementParser;
import io.appium.java_client.AppiumDriver;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * FailureTagReporter — called from TestListener after a test failure.
 *
 * On failure it:
 *   1. Grabs the live page source from Appium
 *   2. Runs QaTagAnalyzer to find missing / badly-named qaTestTags
 *   3. Writes a human-readable YAML report → reports/qa-tag-report/<testName>.yaml
 *   4. Appends missing-tag entries to reports/qa-tag-report/_missing.yaml
 *      (TagPatcher format — run TagPatcher to auto-patch popdroid Composables)
 *
 * Usage:
 *   FailureTagReporter.report(driver, failingElementKey, testName, screenshotPath);
 */
public class FailureTagReporter {

    private static final String REPORT_DIR = "reports/qa-tag-report";

    /**
     * @param driver          the live Appium driver (can be null — skips analysis)
     * @param failingElement  the element key that caused the failure (may be null)
     * @param testName        the TestNG test name (used for file naming)
     * @param screenshotPath  absolute path to the failure screenshot (may be null)
     */
    public static void report(AppiumDriver driver,
                              String failingElement,
                              String testName,
                              String screenshotPath) {
        if (driver == null) {
            System.out.println("[TagReport] Driver null — skipping tag analysis.");
            return;
        }

        try {
            // ── 1. Capture live page source ─────────────────────────────────────
            String pageSource;
            try {
                pageSource = driver.getPageSource();
            } catch (Exception e) {
                System.out.println("[TagReport] Could not get page source: " + e.getMessage());
                return;
            }

            List<Map<String, String>> elements = XmlElementParser.parse(pageSource);
            if (elements.isEmpty()) {
                System.out.println("[TagReport] Page source empty — skipping.");
                return;
            }

            // ── 2. Derive screen name from failing element key ──────────────────
            String screenName = deriveScreenName(failingElement, elements);

            // ── 3. Run QaTagAnalyzer ────────────────────────────────────────────
            QaTagAnalyzer.ScreenReport report = QaTagAnalyzer.analyse(elements, screenName);

            System.out.println("[TagReport] Screen: " + screenName
                    + "  |  good=" + report.goodCount()
                    + "  bad=" + report.badCount()
                    + "  missing=" + report.missingCount());

            if (report.badCount() == 0 && report.missingCount() == 0) {
                System.out.println("[TagReport] All visible elements already have valid tags — no report needed.");
                return;
            }

            // ── 4. Create output directory ─────────────────────────────────────
            Path dir = Paths.get(REPORT_DIR);
            Files.createDirectories(dir);

            // ── 5. Write per-test YAML report ──────────────────────────────────
            String safeName = testName.replaceAll("[^a-zA-Z0-9_-]", "_");
            QaTagAnalyzer.writeYaml(report, REPORT_DIR);
            System.out.println("[TagReport] 📄 Full report: " + dir.resolve(screenName + ".yaml"));

            // ── 6. Append to _missing.yaml (TagPatcher format) ─────────────────
            writeMissingYaml(report, failingElement, screenshotPath, safeName);

        } catch (Exception e) {
            System.out.println("[TagReport] ⚠️  Tag report failed: " + e.getMessage());
        }
    }

    // ── _missing.yaml writer (TagPatcher-compatible) ──────────────────────────

    /**
     * Appends missing + bad-naming entries to _missing.yaml so TagPatcher can
     * automatically add qaTestTag() annotations to popdroid Composables.
     *
     * Format (matches TagPatcher.parseMissingYaml):
     *
     *   # ── ScreenName ──────────────────────────────────────────────
     *   # test: MyTestName   failing_element: shop_search_button
     *   # scanned: 2026-06-09 14:32:00
     *
     *   - element: Search
     *     class: android.widget.EditText
     *     bounds: "[0,200][1080,260]"
     *     suggestion: const val SHOP_SEARCH_INPUT = "shop_search_input"
     *     screenshot: /abs/path/FAIL_MyTest.png
     */
    private static void writeMissingYaml(QaTagAnalyzer.ScreenReport report,
                                         String failingElement,
                                         String screenshotPath,
                                         String testName) throws IOException {

        boolean hasMissing = !report.missing.isEmpty() || !report.badNaming.isEmpty();
        if (!hasMissing) return;

        Path out = Paths.get(REPORT_DIR, "_missing.yaml");
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();

        // Separator + header
        sb.append("\n# ── ").append(report.screenName)
          .append(" ").append("─".repeat(Math.max(0, 60 - report.screenName.length())))
          .append("\n");
        sb.append("# test: ").append(testName);
        if (failingElement != null && !failingElement.isBlank())
            sb.append("   failing_element: ").append(failingElement);
        sb.append("\n");
        sb.append("# scanned: ").append(ts).append("\n\n");

        // Missing tags
        for (QaTagAnalyzer.MissingTagResult m : report.missing) {
            sb.append("- element: ").append(m.text.isEmpty() ? "(unknown)" : m.text).append("\n");
            sb.append("  class: ").append(m.className).append("\n");
            sb.append("  bounds: \"").append(m.bounds).append("\"\n");
            sb.append("  suggestion: ").append(m.suggested).append("\n");
            if (screenshotPath != null && !screenshotPath.isBlank())
                sb.append("  screenshot: ").append(screenshotPath).append("\n");
            sb.append("\n");
        }

        // Bad naming
        for (QaTagAnalyzer.BadNamingResult b : report.badNaming) {
            sb.append("- element: ").append(b.text.isEmpty() ? b.tag : b.text).append("\n");
            sb.append("  class: ").append(b.className).append("\n");
            sb.append("  bounds: \"").append(b.bounds).append("\"\n");
            sb.append("  suggestion: ").append(b.suggested).append("\n");
            if (screenshotPath != null && !screenshotPath.isBlank())
                sb.append("  screenshot: ").append(screenshotPath).append("\n");
            sb.append("\n");
        }

        // Append (create if not exists)
        Files.writeString(out, sb.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        System.out.println("[TagReport] 📋 _missing.yaml updated: " + out.toAbsolutePath());
        System.out.println("[TagReport] 💡 Open Claude Code in popdroid and run:  /qa-tags");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Infer a screen name from the failing element key or the visible tags on screen.
     * E.g. "shop_search_button" → "shop"  →  "ShopScreen"
     */
    private static String deriveScreenName(String failingElement,
                                           List<Map<String, String>> elements) {
        // Try from failing element key first
        if (failingElement != null && failingElement.contains("_")) {
            String prefix = failingElement.split("_")[0];
            return toTitleCase(prefix) + "Screen";
        }

        // Try from the most common prefix among visible tags
        java.util.Map<String, Integer> prefixCount = new java.util.LinkedHashMap<>();
        for (Map<String, String> el : elements) {
            String tag = el.getOrDefault("accessibilityId", "").trim();
            if (tag.contains("_")) {
                String prefix = tag.split("_")[0];
                prefixCount.merge(prefix, 1, Integer::sum);
            }
        }

        String topPrefix = prefixCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");

        return toTitleCase(topPrefix) + "Screen";
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
