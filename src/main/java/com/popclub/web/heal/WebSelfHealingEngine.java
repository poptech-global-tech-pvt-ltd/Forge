package com.popclub.web.heal;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * WebSelfHealingEngine — when a Playwright locator fails, scans the live DOM
 * for the closest matching element and returns a healed CSS selector to retry with.
 *
 * Mirrors {@code SelfHealingEngine} for Appium, but uses {@code page.evaluate()}
 * instead of {@code driver.getPageSource()}.
 *
 * Matching strategy (Dice-coefficient on selector tokens):
 *   "input[name='first_name']" → tokens: {input, first, name}
 *   vs DOM element with name="first_name" → {first, name}
 *   → 2 common / (3+2) = 0.80 → match
 *
 * Minimum score threshold: 0.50
 *
 * Usage in page objects:
 * <pre>
 *   try {
 *       page.locator(selector).fill(value);
 *   } catch (PlaywrightException e) {
 *       String healed = WebSelfHealingEngine.tryHeal(page, selector, e);
 *       if (healed != null) page.locator(healed).fill(value);
 *       else throw e;
 *   }
 * </pre>
 *
 * Or use the convenience wrapper {@link #withHeal}:
 * <pre>
 *   WebSelfHealingEngine.withHeal(page, "input[name='first_name']", s -> page.locator(s).fill(value));
 * </pre>
 */
public class WebSelfHealingEngine {

    private static final Logger log = LoggerFactory.getLogger(WebSelfHealingEngine.class);
    private static final double MIN_SCORE = 0.50;

    // JS injected into the page to collect all interactive element descriptors
    private static final String COLLECT_ELEMENTS_JS = """
        (() => {
            const tags = ['input', 'button', 'select', 'textarea', 'a', 'label',
                          '[role="button"]', '[role="checkbox"]', '[role="radio"]',
                          '[role="option"]', '[role="combobox"]', '[role="textbox"]'];
            const results = [];
            document.querySelectorAll(tags.join(',')).forEach(el => {
                const entry = {
                    tag:       el.tagName.toLowerCase(),
                    id:        el.id        || '',
                    name:      el.getAttribute('name')       || '',
                    value:     el.getAttribute('value')      || '',
                    type:      el.getAttribute('type')       || '',
                    ariaLabel: el.getAttribute('aria-label') || '',
                    testId:    el.getAttribute('data-testid')|| '',
                    placeholder: el.getAttribute('placeholder') || '',
                    text:      (el.textContent || '').trim().substring(0, 60),
                    visible:   el.offsetParent !== null
                };
                // Only include elements that have at least one identifying attribute
                if (entry.id || entry.name || entry.value || entry.ariaLabel ||
                    entry.testId || entry.placeholder || entry.text) {
                    results.push(entry);
                }
            });
            return results;
        })()
        """;

    /**
     * Attempts to find a healed selector for a failing locator.
     *
     * @param page           the live Playwright page
     * @param failedSelector the CSS/attribute selector that timed out
     * @param cause          the exception that triggered healing (used for eligibility check)
     * @return a healed CSS selector string, or {@code null} if no match found
     */
    public static String tryHeal(Page page, String failedSelector, Exception cause) {
        if (!isElementNotFound(cause)) {
            log.debug("[WebHeal] Skipping — not an element-not-found error: {}", cause.getMessage());
            return null;
        }

        log.warn("[WebHeal] ⚡ Attempting to heal: \"{}\"", failedSelector);

        List<Map<String, String>> domElements = collectDomElements(page);
        if (domElements.isEmpty()) {
            log.warn("[WebHeal] DOM scan returned no elements — cannot heal.");
            return null;
        }

        Set<String> expectedTokens = tokenize(failedSelector);
        log.debug("[WebHeal] Expected tokens: {}", expectedTokens);

        String bestSelector = null;
        double bestScore    = MIN_SCORE;

        for (Map<String, String> el : domElements) {
            // Build candidate tokens from all identifying attributes
            Set<String> candidateTokens = new HashSet<>();
            candidateTokens.addAll(tokenize(el.get("id")));
            candidateTokens.addAll(tokenize(el.get("name")));
            candidateTokens.addAll(tokenize(el.get("value")));
            candidateTokens.addAll(tokenize(el.get("ariaLabel")));
            candidateTokens.addAll(tokenize(el.get("testId")));
            candidateTokens.addAll(tokenize(el.get("placeholder")));
            if (el.get("tag") != null) candidateTokens.add(el.get("tag"));
            if (el.get("type") != null && !el.get("type").isEmpty()) candidateTokens.add(el.get("type"));

            double s = diceScore(expectedTokens, candidateTokens);
            if (s > bestScore) {
                bestScore    = s;
                bestSelector = buildSelector(el);
            }
        }

        if (bestSelector == null) {
            log.warn("[WebHeal] No match above threshold ({}) for: \"{}\"", MIN_SCORE, failedSelector);
            return null;
        }

        log.warn("[WebHeal] ✅ Healed \"{}\" → \"{}\"  ({} similarity)",
                failedSelector, bestSelector, String.format("%.0f%%", bestScore * 100));
        return bestSelector;
    }

    /**
     * Convenience wrapper: runs {@code action} with the original selector; if it throws
     * a Playwright element-not-found error, heals once and retries.
     *
     * <pre>
     *   WebSelfHealingEngine.withHeal(page, "input[name='first_name']",
     *       s -> page.locator(s).fill("Deepa"));
     * </pre>
     *
     * @param page     live Playwright page
     * @param selector the CSS/attribute selector to use
     * @param action   lambda that receives the selector and performs the operation
     */
    public static void withHeal(Page page, String selector, SelectorConsumer action) {
        try {
            action.accept(selector);
        } catch (PlaywrightException e) {
            String healed = tryHeal(page, selector, e);
            if (healed != null) {
                log.info("[WebHeal] Retrying with healed selector: \"{}\"", healed);
                action.accept(healed);
            } else {
                throw e;
            }
        }
    }

    @FunctionalInterface
    public interface SelectorConsumer {
        void accept(String selector);
    }

    // ── DOM collection ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> collectDomElements(Page page) {
        try {
            Object raw = page.evaluate(COLLECT_ELEMENTS_JS);
            if (raw instanceof List<?> list) {
                return list.stream()
                        .filter(item -> item instanceof Map)
                        .map(item -> (Map<String, String>) item)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("[WebHeal] DOM collection failed: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Builds the most specific stable CSS selector for a DOM element descriptor.
     * Priority: id > name + tag > aria-label > value + tag > data-testid > text
     */
    private static String buildSelector(Map<String, String> el) {
        String tag       = el.getOrDefault("tag", "*");
        String id        = el.getOrDefault("id", "");
        String name      = el.getOrDefault("name", "");
        String value     = el.getOrDefault("value", "");
        String ariaLabel = el.getOrDefault("ariaLabel", "");
        String testId    = el.getOrDefault("testId", "");
        String type      = el.getOrDefault("type", "");

        if (!id.isEmpty())        return "#" + id;
        if (!name.isEmpty() && !type.isEmpty())  return tag + "[name='" + name + "'][type='" + type + "']";
        if (!name.isEmpty())      return tag + "[name='" + name + "']";
        if (!ariaLabel.isEmpty()) return tag + "[aria-label='" + ariaLabel + "']";
        if (!value.isEmpty())     return tag + "[value='" + value + "']";
        if (!testId.isEmpty())    return "[data-testid='" + testId + "']";
        return tag;
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    /**
     * Tokenizes a CSS selector or attribute string into snake_case / word tokens.
     * Examples:
     *   "input[name='first_name']" → {input, name, first, name} → {input, first, name}
     *   "first_name"               → {first, name}
     *   "aria-label"               → {aria, label}
     */
    private static Set<String> tokenize(String s) {
        if (s == null || s.isBlank()) return Collections.emptySet();
        // Strip CSS structural chars, keep alphanumeric + _ -
        String cleaned = s.replaceAll("[\\[\\]()'\".=#]", " ")
                          .replaceAll("[_\\-]", " ");
        return Arrays.stream(cleaned.toLowerCase().split("\\s+"))
                .filter(t -> t.length() > 1)  // skip single-char noise
                .collect(Collectors.toSet());
    }

    /**
     * Dice-coefficient: 2 * |intersection| / (|A| + |B|)
     */
    private static double diceScore(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        long common = a.stream().filter(b::contains).count();
        return 2.0 * common / (a.size() + b.size());
    }

    private static boolean isElementNotFound(Exception e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
        return msg != null && (
                msg.contains("Timeout")           ||
                msg.contains("timeout")           ||
                msg.contains("waiting for")       ||
                msg.contains("no element found")  ||
                msg.contains("not found")         ||
                msg.contains("locator.fill")      ||
                msg.contains("locator.click")     ||
                msg.contains("locator.waitFor")
        );
    }
}
