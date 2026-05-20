package com.popclub.ai.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyses the accessibility tree of a screen to:
 *   1. Identify elements that have a test tag (content-desc) and validate naming convention
 *   2. Identify interactive elements that are missing a test tag entirely
 *   3. Suggest a TestTags constant name for every missing / badly named element
 *
 * Used by both ScanTagsAction (mid-test inline scan) and TagFinder (interactive CLI).
 */
public class QaTagAnalyzer {

    // ── Known screen prefixes from TestTags.kt convention ──────────────────────
    private static final List<String> KNOWN_PREFIXES = Arrays.asList(
        "login_", "otp_", "home_", "profile_", "send_money_", "enter_amount_",
        "tss_", "everything_upi_", "upi_", "bank_transfer_", "txn_",
        "payment_success_", "bill_", "recharge_", "cart_", "shop_", "product_",
        "order_", "rewards_", "cashback_", "pop_coins_", "credit_card_", "cc_",
        "rupay_", "toolbar_", "common_", "app_update_", "faq_", "help_support_",
        "address_", "wishlist_", "mandate_", "check_balance_", "request_money_",
        "add_faves_", "bank_transfer_", "link_bank_", "split_", "activate_lite_",
        "biometric_", "refer_", "no_cashback_", "clp_", "search_", "onboarding_",
        "kyc_", "notification_", "settings_", "voucher_", "offer_", "banner_"
    );

    // Tags that are purely accessibility labels — not test tags, skip them
    private static final Set<String> ALLOWED_GENERIC = new HashSet<>(Arrays.asList(
        "Close", "Back", "Navigate up", "More options",
        "Open navigation drawer", "Navigate up"
    ));

    // Elements that appear on every screen (bottom nav, toolbar) → use common_ prefix
    private static final Set<String> GLOBAL_LABELS = new HashSet<>(Arrays.asList(
        "Profile", "Shop", "Card", "Home", "Rewards", "Feed",
        "profile", "shop", "card", "home", "rewards", "feed"
    ));

    // Android widget classes that are always interactive
    private static final Set<String> INTERACTIVE_CLASSES = new HashSet<>(Arrays.asList(
        "android.widget.Button",
        "android.widget.ImageButton",
        "android.widget.EditText",
        "android.widget.CheckBox",
        "android.widget.RadioButton",
        "android.widget.Switch",
        "android.widget.ToggleButton",
        "android.widget.SeekBar"
    ));

    private static final Set<String> SCREEN_SUFFIXES = new LinkedHashSet<>(Arrays.asList(
        "ScreenContent", "FragmentContent", "Screen", "Fragment",
        "Dialog", "BottomSheet", "Activity", "Page", "View", "Content"
    ));

    // ── Result types ───────────────────────────────────────────────────────────

    public static class TagResult {
        public final String tag;
        public final String text;
        public final String className;
        public final String bounds;

        TagResult(String tag, String text, String className, String bounds) {
            this.tag = tag;
            this.text = text;
            this.className = className;
            this.bounds = bounds;
        }
    }

    public static class BadNamingResult extends TagResult {
        public final String problem;
        public final String suggested;

        BadNamingResult(String tag, String text, String className, String bounds,
                        String problem, String suggested) {
            super(tag, text, className, bounds);
            this.problem = problem;
            this.suggested = suggested;
        }
    }

    public static class MissingTagResult {
        public final String text;
        public final String className;
        public final String bounds;
        public final String suggested;

        MissingTagResult(String text, String className, String bounds, String suggested) {
            this.text = text;
            this.className = className;
            this.bounds = bounds;
            this.suggested = suggested;
        }
    }

    public static class ScreenReport {
        public final String screenName;
        public final String scannedAt;
        public final List<TagResult>        good        = new ArrayList<>();
        public final List<BadNamingResult>  badNaming   = new ArrayList<>();
        public final List<MissingTagResult> missing     = new ArrayList<>();

        ScreenReport(String screenName) {
            this.screenName = screenName;
            this.scannedAt  = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        public int goodCount()    { return good.size(); }
        public int badCount()     { return badNaming.size(); }
        public int missingCount() { return missing.size(); }
    }

    // ── Main analysis entry point ──────────────────────────────────────────────

    /**
     * Analyse all elements from a parsed page source for a given screen.
     *
     * @param elements  output of XmlElementParser.parse(pageSource)
     * @param screenName  e.g. "SendMoneyScreen" or current activity name
     */
    public static ScreenReport analyse(List<Map<String, String>> elements, String screenName) {
        ScreenReport report = new ScreenReport(screenName);

        // Pass 1: collect all elements that have a test tag (content-desc = snake_case)
        // Compose sets qaTestTag on ANY element, not just interactive ones.
        Set<String> seenTags = new LinkedHashSet<>();
        for (Map<String, String> el : elements) {
            String tag = el.getOrDefault("accessibilityId", "").trim();
            if (tag.isEmpty() || ALLOWED_GENERIC.contains(tag)) continue;
            if (seenTags.contains(tag)) continue;
            seenTags.add(tag);

            String text      = el.getOrDefault("text", "").trim();
            String className = el.getOrDefault("class", "").trim();
            String bounds    = el.getOrDefault("bounds", "").trim();

            String problem = checkConvention(tag);
            if (problem != null) {
                String suggested = suggestBadTag(screenName, tag, text, className);
                report.badNaming.add(new BadNamingResult(tag, text, className, bounds, problem, suggested));
            } else {
                report.good.add(new TagResult(tag, text, className, bounds));
            }
        }

        // Pass 2: find interactive elements (widget-level, not Compose containers) that
        // have NO test tag at all — these are genuinely missing.
        Map<String, Integer> typeCount = new LinkedHashMap<>();
        List<Map<String, String>> interactiveElements = new ArrayList<>();
        for (Map<String, String> el : elements) {
            if (!isInteractive(el)) continue;
            if (!"true".equals(el.get("enabled"))) continue;
            String tag = el.getOrDefault("accessibilityId", "").trim();
            if (!tag.isEmpty()) continue; // already counted in pass 1
            interactiveElements.add(el);
            typeCount.merge(typeKey(el), 1, Integer::sum);
        }

        Map<String, Integer> typeSeen = new LinkedHashMap<>();
        for (Map<String, String> el : interactiveElements) {
            String text      = el.getOrDefault("text", "").trim();
            String className = el.getOrDefault("class", "").trim();
            String bounds    = el.getOrDefault("bounds", "").trim();
            String key       = typeKey(el);
            int    nth       = typeSeen.getOrDefault(key, 0);
            typeSeen.put(key, nth + 1);
            boolean isIndexed = typeCount.getOrDefault(key, 1) > 1;
            report.missing.add(new MissingTagResult(text, className, bounds,
                    suggestTag(screenName, text, className, nth, isIndexed)));
        }

        return report;
    }

    // ── Naming convention checker ──────────────────────────────────────────────

    private static String checkConvention(String tag) {
        if (!tag.matches("[a-z][a-z0-9_]+")) {
            if (tag.matches("[A-Z].*"))
                return "PascalCase / Title Case — should be snake_case with screen prefix";
            if (tag.contains(" "))
                return "contains spaces — should be snake_case";
            return "not snake_case — use lowercase_with_underscores";
        }
        boolean hasKnownPrefix = KNOWN_PREFIXES.stream().anyMatch(tag::startsWith);
        if (!hasKnownPrefix)
            return "no screen prefix — too generic (e.g. prefix with 'home_' or 'send_money_')";
        return null; // OK
    }

    // ── Suggestion engine ──────────────────────────────────────────────────────

    private static String suggestTag(String screenName, String text,
                                     String className, int nth, boolean indexed) {
        String prefix     = screenPrefix(screenName);
        String label      = resolveLabel(text, className);
        String base       = dedup(prefix + "_" + label);

        if (indexed) {
            String funcName = snakeToCamel(base) + "Item";
            return "fun " + funcName + "(index: Int) = \"" + base + "_item_$index\"";
        }
        return "const val " + base.toUpperCase() + " = \"" + base + "\"";
    }

    /**
     * Suggest a fix for a badly named tag.
     * - If tag is already snake_case but missing screen prefix → prepend screen prefix
     * - If tag is PascalCase/Title Case → convert to snake_case and prepend screen prefix
     */
    private static String suggestBadTag(String screenName, String tag,
                                        String text, String className) {
        // Global elements (bottom nav, toolbar) → always use common_ prefix
        if (GLOBAL_LABELS.contains(tag) || GLOBAL_LABELS.contains(text)) {
            String snake = textToSnake(tag.isBlank() ? text : tag);
            String base  = dedup("common_" + snake);
            return "const val " + base.toUpperCase() + " = \"" + base + "\"";
        }

        String prefix = screenPrefix(screenName);

        // Already snake_case — just prepend screen prefix
        if (tag.matches("[a-z][a-z0-9_]+")) {
            String base = dedup(prefix + "_" + tag);
            return "const val " + base.toUpperCase() + " = \"" + base + "\"";
        }

        // PascalCase / Title Case / spaces — convert to snake_case with screen prefix
        String snake = textToSnake(tag);
        if (!snake.isBlank()) {
            String base = dedup(prefix + "_" + snake);
            return "const val " + base.toUpperCase() + " = \"" + base + "\"";
        }

        // Fallback
        return suggestTag(screenName, text, className, 0, false);
    }

    private static String resolveLabel(String text, String className) {
        if (text != null && !text.isBlank() && text.length() < 40)
            return textToSnake(text);
        return classToType(className);
    }

    private static String screenPrefix(String name) {
        String stem = name;
        // Strip known suffixes
        for (String suf : SCREEN_SUFFIXES) {
            if (stem.endsWith(suf) && stem.length() > suf.length()) {
                stem = stem.substring(0, stem.length() - suf.length());
                break;
            }
        }
        return camelToSnake(stem);
    }

    private static String classToType(String className) {
        if (className == null) return "element";
        String lc = className.toLowerCase();
        if (lc.contains("button"))    return "button";
        if (lc.contains("edittext"))  return "input";
        if (lc.contains("checkbox"))  return "checkbox";
        if (lc.contains("radio"))     return "radio";
        if (lc.contains("switch") || lc.contains("toggle")) return "switch";
        return "row";
    }

    private static String textToSnake(String text) {
        // Insert underscore before uppercase letters (PascalCase → pascal_case)
        String spaced = text.replaceAll("([a-z])([A-Z])", "$1 $2");
        String clean  = spaced.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
        return clean.replaceAll("\\s+", "_").toLowerCase();
    }

    private static String camelToSnake(String s) {
        String s1 = s.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2");
        return s1.replaceAll("([a-z\\d])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String snakeToCamel(String s) {
        StringBuilder sb = new StringBuilder();
        boolean next = false;
        for (char c : s.toCharArray()) {
            if (c == '_') { next = true; }
            else if (next) { sb.append(Character.toUpperCase(c)); next = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }

    /** Remove consecutive duplicate words: "send_send_money" → "send_money" */
    private static String dedup(String s) {
        String[] parts = s.split("_");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty() && (out.isEmpty() || !p.equals(out.get(out.size() - 1))))
                out.add(p);
        }
        return String.join("_", out);
    }

    // ── Interactivity detection ────────────────────────────────────────────────

    private static boolean isInteractive(Map<String, String> el) {
        String cls        = el.getOrDefault("class", "");
        String text       = el.getOrDefault("text", "").trim();
        String resourceId = el.getOrDefault("resourceId", "").trim();
        boolean clickable = "true".equals(el.get("clickable"));
        boolean focusable = "true".equals(el.get("focusable"));

        // Always interactive: known widget types
        if (INTERACTIVE_CLASSES.contains(cls)) return true;

        // Compose renders everything as android.view.View.
        // A bare View is only meaningful to flag if it has a resource-id
        // (i.e. it's a real named widget), not a layout container.
        if ("android.view.View".equals(cls) || "android.view.ViewGroup".equals(cls)) {
            return (clickable || focusable) && !resourceId.isEmpty();
        }

        return clickable || focusable;
    }

    private static String typeKey(Map<String, String> el) {
        String text = el.getOrDefault("text", "").trim();
        return textToSnake(text) + "|" + el.getOrDefault("class", "");
    }

    // ── Console printer ────────────────────────────────────────────────────────

    public static void printReport(ScreenReport r) {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.printf( "║  Screen: %-50s║%n", r.screenName);
        System.out.printf( "║  ✅ Good: %-3d  ⚠️  Bad naming: %-3d  ❌ Missing: %-3d     ║%n",
            r.goodCount(), r.badCount(), r.missingCount());
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        if (!r.good.isEmpty()) {
            System.out.println("\n✅  GOOD TAGS (ready to use in Forge tests):");
            for (TagResult e : r.good)
                System.out.printf("    %-50s  %s%n", e.tag,
                    e.text.isEmpty() ? "" : "(\"" + e.text + "\")");
        }

        if (!r.badNaming.isEmpty()) {
            System.out.println("\n⚠️   BAD NAMING (Appium may find wrong element):");
            for (BadNamingResult e : r.badNaming) {
                System.out.println("    tag     : \"" + e.tag + "\"");
                System.out.println("    problem : " + e.problem);
                System.out.println("    💡       " + e.suggested);
                System.out.println();
            }
        }

        if (!r.missing.isEmpty()) {
            System.out.println("\n❌  MISSING TAGS (Appium cannot locate these):");
            for (MissingTagResult e : r.missing) {
                String label = e.text.isEmpty() ? e.className : "\"" + e.text + "\"";
                System.out.printf("    %-45s  %s%n", label, e.bounds);
                System.out.println("    💡 " + e.suggested);
                System.out.println();
            }
        }
        System.out.println();
    }

    // ── YAML report writer ─────────────────────────────────────────────────────

    public static void writeYaml(ScreenReport r, String reportsDir) {
        try {
            Path dir = Paths.get(reportsDir, "qa-app-scan");
            Files.createDirectories(dir);
            Path out = dir.resolve(r.screenName + ".yaml");

            StringBuilder sb = new StringBuilder();
            sb.append("# Screen: ").append(r.screenName).append("\n");
            sb.append("# Scanned: ").append(r.scannedAt).append("\n");
            sb.append("# Good: ").append(r.goodCount())
              .append("  Bad naming: ").append(r.badCount())
              .append("  Missing: ").append(r.missingCount()).append("\n\n");

            sb.append("screen: ").append(r.screenName).append("\n");
            sb.append("scanned: \"").append(r.scannedAt).append("\"\n\n");

            sb.append("good_tags:\n");
            if (r.good.isEmpty()) {
                sb.append("  []\n");
            } else {
                for (TagResult e : r.good) {
                    sb.append("  - tag: ").append(e.tag).append("\n");
                    if (!e.text.isEmpty())
                        sb.append("    text: \"").append(e.text).append("\"\n");
                    sb.append("    class: ").append(e.className).append("\n");
                }
            }

            sb.append("\nbad_naming:\n");
            if (r.badNaming.isEmpty()) {
                sb.append("  []\n");
            } else {
                for (BadNamingResult e : r.badNaming) {
                    sb.append("  - tag: \"").append(e.tag).append("\"\n");
                    sb.append("    problem: \"").append(e.problem).append("\"\n");
                    if (!e.text.isEmpty())
                        sb.append("    text: \"").append(e.text).append("\"\n");
                    sb.append("    suggested: \"").append(e.suggested).append("\"\n");
                }
            }

            sb.append("\nmissing_tags:\n");
            if (r.missing.isEmpty()) {
                sb.append("  []\n");
            } else {
                for (MissingTagResult e : r.missing) {
                    sb.append("  - class: ").append(e.className).append("\n");
                    if (!e.text.isEmpty())
                        sb.append("    text: \"").append(e.text).append("\"\n");
                    sb.append("    bounds: \"").append(e.bounds).append("\"\n");
                    sb.append("    suggested: \"").append(e.suggested).append("\"\n");
                }
            }

            Files.writeString(out, sb.toString(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("  📄 " + out);

        } catch (IOException e) {
            System.err.println("  ⚠️  Could not write YAML report: " + e.getMessage());
        }
    }

    // ── Summary writer (missing_from_app.txt) ─────────────────────────────────

    public static void writeSummary(List<ScreenReport> reports, String reportsDir) {
        try {
            Path scanDir     = Paths.get(reportsDir, "qa-app-scan");
            Path summaryYaml = scanDir.resolve("summary.yaml");
            Path missingTxt  = scanDir.resolve("missing_from_app.txt");

            // summary.yaml
            StringBuilder sy = new StringBuilder();
            sy.append("# qa-app-scan summary\n");
            sy.append("# Generated: ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
            int totalGood = reports.stream().mapToInt(ScreenReport::goodCount).sum();
            int totalBad  = reports.stream().mapToInt(ScreenReport::badCount).sum();
            int totalMiss = reports.stream().mapToInt(ScreenReport::missingCount).sum();
            sy.append("total_screens: ").append(reports.size()).append("\n");
            sy.append("total_good_tags: ").append(totalGood).append("\n");
            sy.append("total_bad_naming: ").append(totalBad).append("\n");
            sy.append("total_missing_tags: ").append(totalMiss).append("\n\nscreens:\n");
            for (ScreenReport r : reports) {
                sy.append("  ").append(r.screenName).append(":\n");
                sy.append("    good: ").append(r.goodCount()).append("\n");
                sy.append("    bad_naming: ").append(r.badCount()).append("\n");
                sy.append("    missing: ").append(r.missingCount()).append("\n");
            }
            Files.writeString(summaryYaml, sy.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // missing_from_app.txt
            StringBuilder mt = new StringBuilder();
            mt.append("# Missing Tags — detected from LIVE APP (TagFinder / ScanTagsAction)\n");
            mt.append("# Generated: ").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            mt.append("# Source: runtime accessibility tree (ground truth for Appium)\n");
            mt.append("# Suggestions use visible text — more accurate than source-only scanning\n\n");

            for (ScreenReport r : reports) {
                if (r.badNaming.isEmpty() && r.missing.isEmpty()) continue;
                mt.append("─".repeat(80)).append("\n");
                mt.append("Screen: ").append(r.screenName).append("\n\n");

                if (!r.badNaming.isEmpty()) {
                    mt.append("  ⚠️  BAD NAMING (fix — Appium may pick wrong element):\n");
                    for (BadNamingResult e : r.badNaming) {
                        mt.append("    tag     : \"").append(e.tag).append("\"\n");
                        mt.append("    problem : ").append(e.problem).append("\n");
                        if (!e.text.isEmpty())
                            mt.append("    text    : \"").append(e.text).append("\"\n");
                        mt.append("    💡       ").append(e.suggested).append("\n\n");
                    }
                }

                if (!r.missing.isEmpty()) {
                    mt.append("  ❌  MISSING TAGS (Appium cannot locate these):\n");
                    for (MissingTagResult e : r.missing) {
                        String label = e.text.isEmpty() ? e.className : "\"" + e.text + "\"";
                        mt.append("    element : ").append(label)
                          .append("  [").append(e.className).append("]\n");
                        mt.append("    💡       ").append(e.suggested).append("\n\n");
                    }
                }
            }

            Files.writeString(missingTxt, mt.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("\n📄 Summary  : " + summaryYaml);
            System.out.println("📄 Full gaps: " + missingTxt);

        } catch (IOException e) {
            System.err.println("Could not write summary: " + e.getMessage());
        }
    }
}
