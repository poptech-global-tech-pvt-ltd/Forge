package com.popclub.heal;

import com.popclub.core.ElementRepository;
import com.popclub.core.Locator;
import com.popclub.core.TestContext;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import com.popclub.parser.XmlElementParser;
import io.appium.java_client.AppiumDriver;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SelfHealingEngine — when an element is not found on screen, scans the live
 * page source for the closest matching qaTestTag, patches ElementRepository
 * in-memory and writes back to the elements YAML, then updates the step's
 * locators so the caller can retry immediately.
 *
 * Matching uses Dice-coefficient token overlap on snake_case tag names:
 *   "shop_search_icon" vs "shop_search_button" → 2 common tokens / 3+3 = 0.67 → match
 *
 * Minimum score threshold: 0.40  (i.e. at least ~40% token overlap required)
 *
 * Usage (inside TestExecutor catch block, before throwing):
 *   boolean healed = SelfHealingEngine.tryHeal(step, platform, lastException);
 *   if (healed) { retry the step }
 */
public class SelfHealingEngine {

    private static final double MIN_SCORE    = 0.60;
    // Tags that look like proper qaTestTags: snake_case, at least one underscore
    private static final String QA_TAG_REGEX = "^[a-z][a-z0-9]*(_[a-z0-9]+)+$";

    /**
     * Attempts to heal a failing step by finding the closest matching tag on
     * the current screen and patching the element locator.
     *
     * @param step      the step that just failed
     * @param platform  "android" or "ios"
     * @param lastError the exception thrown by the action
     * @return true if a match was found and step.locators was updated for retry
     */
    public static boolean tryHeal(Step step, String platform, Exception lastError) {

        // Only attempt healing for element-not-found errors
        if (!isElementNotFound(lastError)) {
            System.out.println("[SelfHeal] Skipping — error is not element-not-found: "
                    + lastError.getMessage());
            return false;
        }

        // Need an element key to patch back
        if (step.element == null || step.element.isBlank()) {
            System.out.println("[SelfHeal] Skipping — step has no element key to heal.");
            return false;
        }

        System.out.println("\n[SelfHeal] ⚡ Attempting to self-heal: element=\""
                + step.element + "\"");

        // ── 1. Get current screen state ─────────────────────────────────────
        List<String> screenTags = getScreenTags();
        if (screenTags.isEmpty()) {
            System.out.println("[SelfHeal] No qaTestTags found on screen — cannot heal.");
            return false;
        }

        System.out.println("[SelfHeal] Tags on screen: " + screenTags);

        // ── 2. Determine the expected value we were looking for ──────────────
        String expectedValue = getExpectedValue(step, platform);
        System.out.println("[SelfHeal] Expected locator value: \"" + expectedValue + "\"");

        // ── 3. Find best matching tag on screen ──────────────────────────────
        String bestMatch = findBestMatch(expectedValue, screenTags);
        if (bestMatch == null) {
            System.out.println("[SelfHeal] No match found above threshold ("
                    + MIN_SCORE + ") — cannot heal.");
            return false;
        }

        System.out.println("[SelfHeal] ✅ Best match: \"" + bestMatch + "\"  ("
                + String.format("%.0f%%", score(expectedValue, bestMatch) * 100) + " similarity)");

        // ── 4. Check if the healed tag already has a named element key ───────
        // e.g. screen has "shop_search_button" and elements YAML already has
        // an entry "shop_search_button" → update the step to use that key.
        String existingKey = ElementRepository.findElementKeyByTag(bestMatch, platform);

        // ── 5. Patch ElementRepository in-memory and write elements YAML ─────
        ElementRepository.updateLocator(step.element, platform, bestMatch);

        // ── 6. Patch the test YAML step ───────────────────────────────────────
        patchTestStep(step, existingKey != null ? existingKey : step.element, bestMatch);

        // ── 7. Update this step's locators for the immediate retry ────────────
        Locator healed = new Locator();
        healed.type  = "accessibilityId";
        healed.value = bestMatch;
        step.locators = List.of(healed);

        // If the step should now reference a different element key, update it
        if (existingKey != null && !existingKey.equals(step.element)) {
            System.out.println("[SelfHeal] Step element key updated: \""
                    + step.element + "\" → \"" + existingKey + "\"");
            step.element = existingKey;
        }

        System.out.println("[SelfHeal] Patched element \"" + step.element
                + "\" → accessibilityId: \"" + bestMatch + "\"");
        return true;
    }

    // ── Test YAML patcher ────────────────────────────────────────────────────

    /**
     * Patches the test YAML file to fix the failing step:
     *
     *  Case A — step has `element: old_key` and a better element key exists:
     *    element: old_key  →  element: new_key  # healed from: old_key
     *
     *  Case B — step has `element: old_key` but same key is kept (locator fixed):
     *    Adds a comment on the element line noting the heal.
     *
     *  Case C — step has `locator: old_tag` (direct tag, no element key):
     *    locator: old_tag  →  locator: new_tag  # healed from: old_tag
     */
    private static void patchTestStep(Step step, String newElementKey, String newTagValue) {
        String testFile = TestContext.getTestSourceFile();
        if (testFile == null || testFile.isBlank()) {
            System.out.println("[SelfHeal] Test source file unknown — skipping step patch.");
            return;
        }

        try {
            Path path  = Paths.get(testFile);
            String yaml = new String(Files.readAllBytes(path));
            String[] lines = yaml.split("\n", -1);
            boolean patched = false;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];

                // Case A/B: `element: old_key`
                if (step.element != null
                        && line.matches("\\s+element:\\s+" + step.element + "\\s*.*")) {
                    String indent   = line.substring(0, line.indexOf("element:"));
                    String oldValue = step.element;
                    if (!newElementKey.equals(oldValue)) {
                        // Better element key found → update the key
                        lines[i] = indent + "element: " + newElementKey
                                + "  # healed from: " + oldValue;
                    } else {
                        // Same key, locator value was fixed in elements YAML
                        lines[i] = indent + "element: " + oldValue
                                + "  # healed locator → " + newTagValue;
                    }
                    patched = true;
                    break;
                }

                // Case C: `locator: old_tag`
                if (step.locator != null
                        && line.matches("\\s+locator:\\s+" + step.locator + "\\s*.*")) {
                    String indent = line.substring(0, line.indexOf("locator:"));
                    lines[i] = indent + "locator: " + newTagValue
                            + "  # healed from: " + step.locator;
                    patched = true;
                    break;
                }
            }

            if (patched) {
                Files.write(path, String.join("\n", lines).getBytes());
                System.out.println("[SelfHeal] ✅ Test YAML step patched: " + testFile);
            } else {
                System.out.println("[SelfHeal] ⚠️  Could not locate step line in: " + testFile);
            }

        } catch (Exception e) {
            System.out.println("[SelfHeal] ⚠️  Test YAML patch failed: " + e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns all snake_case qaTestTag-style contentDescriptions visible on screen.
     */
    private static List<String> getScreenTags() {
        try {
            AppiumDriver driver = DriverManager.getDriver();
            String xml = driver.getPageSource();
            List<Map<String, String>> elements = XmlElementParser.parse(xml);
            return elements.stream()
                    .map(e -> e.get("accessibilityId"))
                    .filter(t -> t != null && !t.isBlank() && t.matches(QA_TAG_REGEX))
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.out.println("[SelfHeal] Failed to get page source: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Gets the first locator value currently configured for this step/element.
     * Falls back to the element key itself if no locators are resolved yet.
     */
    private static String getExpectedValue(Step step, String platform) {
        // Try already-resolved locators first
        if (step.locators != null && !step.locators.isEmpty()) {
            for (Locator l : step.locators) {
                if (l.value != null && !l.value.isBlank()) return l.value;
            }
        }
        // Fall back: element key is often the same as the tag value
        return step.element;
    }

    /**
     * Finds the highest-scoring candidate above MIN_SCORE threshold.
     * Returns null if none qualifies.
     */
    private static String findBestMatch(String expected, List<String> candidates) {
        String best      = null;
        double bestScore = MIN_SCORE;

        for (String candidate : candidates) {
            double s = score(expected, candidate);
            if (s > bestScore) {
                bestScore = s;
                best      = candidate;
            }
        }
        return best;
    }

    /**
     * Dice-coefficient token overlap: tokens split on underscore.
     * score("shop_search_icon", "shop_search_button") = 2*2/(3+3) = 0.67
     */
    private static double score(String a, String b) {
        if (a == null || b == null) return 0;
        // Exact match
        if (a.equals(b)) return 1.0;

        Set<String> ta = new HashSet<>(Arrays.asList(a.split("_")));
        Set<String> tb = new HashSet<>(Arrays.asList(b.split("_")));

        // Check if one contains the other as a prefix (e.g. shop_search_icon vs shop_search_icon_button)
        if (a.startsWith(b) || b.startsWith(a)) return 0.85;

        long common = ta.stream().filter(tb::contains).count();
        return 2.0 * common / (ta.size() + tb.size());
    }

    private static boolean isElementNotFound(Exception e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
        return msg != null && (
                msg.contains("Element not found") ||
                msg.contains("element not found") ||
                msg.contains("no such element") ||
                msg.contains("TimeoutException") ||
                msg.contains("NoSuchElementException")
        );
    }
}
