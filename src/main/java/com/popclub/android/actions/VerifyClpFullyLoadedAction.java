package com.popclub.android.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.popclub.clp.ClpApiClient;
import com.popclub.clp.ClpSection;
import com.popclub.core.GestureUtil;
import com.popclub.core.TestContext;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VerifyClpFullyLoadedAction — validates the CLP page is fully rendered by
 * comparing what the network API returned with what is actually visible on screen.
 *
 * Two data sources (used together):
 *   1. Live API call  — fetches fresh CLP sections via ClpApiClient
 *   2. TestContext    — falls back to data stored by a prior verifyCLP step
 *
 * Checks:
 *   ✅ No loading spinners / shimmer placeholders remain visible
 *   ✅ Every section the API returned has its title visible on screen
 *   ✅ No section title appears without any items below it (empty section)
 *   ✅ Visible section count matches API section count
 *
 * YAML usage:
 *   - action: verifyCLPFullyLoaded
 *     value: SHOP        # HOME | SHOP | CARD
 */
public class VerifyClpFullyLoadedAction implements Action {

    private static final int  TOTAL_SCROLLS = 15;
    private static final long SCROLL_WAIT   = 400;

    private static final List<String> LOADING_INDICATORS = Arrays.asList(
            "shimmer", "skeleton", "loading", "progress", "spinner",
            "placeholder", "loading_view", "shimmer_layout"
    );

    // Same skip/titled sets as VerifyClpAction — only titled content sections matter
    private static final Set<String> SKIP_TYPES = new HashSet<>(Arrays.asList(
            "search_bar", "search", "search_widget",
            "header", "toolbar", "app_bar", "top_bar",
            "tab", "tabs", "bottom_tab", "bottom_nav", "nav_bar", "navigation",
            "banner", "hero_banner", "full_banner", "image_banner",
            "divider", "spacer", "separator",
            "static", "static_text", "label"
    ));

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
        String pageArg = step.value != null ? step.value.trim().toUpperCase() : "SHOP";

        ClpApiClient.Page page;
        try {
            page = ClpApiClient.Page.valueOf(pageArg);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "verifyCLPFullyLoaded: unknown page '" + pageArg + "'. Use HOME, SHOP or CARD.");
        }

        AppiumDriver driver = DriverManager.getDriver();

        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("  verifyCLPFullyLoaded: " + page.displayName);
        System.out.println("══════════════════════════════════════════════");

        // ── 1. Fetch sections from API (live) ─────────────────────────────────
        List<ClpSection> apiSections = fetchFromApi(page);

        // ── 2. Fall back to TestContext if API call failed ────────────────────
        List<ClpSection> sections = !apiSections.isEmpty()
                ? apiSections
                : TestContext.getClpData(pageArg);

        if (sections.isEmpty()) {
            throw new RuntimeException(
                    "verifyCLPFullyLoaded: no CLP data available for '" + pageArg
                    + "'. Either the API call failed or verifyCLP has not run yet.");
        }

        String dataSource = !apiSections.isEmpty() ? "API (live)" : "TestContext (cached)";
        System.out.println("  Data source   : " + dataSource);
        System.out.println("  API sections  : " + sections.size());

        // ── 3. Check for loading indicators ──────────────────────────────────
        List<String> spinnersFound = findLoadingIndicators(driver);
        if (spinnersFound.isEmpty()) {
            System.out.println("  ✅ No loading spinners/shimmer detected");
        } else {
            System.out.println("  ⚠️  Loading indicators still visible: " + spinnersFound);
        }

        // ── 4. Scroll through page and verify each API section is on screen ───
        scrollToTop(driver);
        Thread.sleep(500);

        int          visibleSections  = 0;
        int          emptySections    = 0;
        List<String> missingSections  = new ArrayList<>();
        List<String> emptySectionNames = new ArrayList<>();

        for (ClpSection section : sections) {
            if (section.sectionTitle.isEmpty()) continue;

            boolean titleVisible = isTextVisible(driver, section.sectionTitle);
            if (!titleVisible) {
                scrollToText(driver, section.sectionTitle);
                titleVisible = isTextVisible(driver, section.sectionTitle);
            }

            if (!titleVisible) {
                missingSections.add(section.sectionTitle);
                System.out.printf("  ❌ MISSING SECTION: \"%s\" (API returned, not on screen)%n",
                        section.sectionTitle);
                continue;
            }

            visibleSections++;

            // Verify at least one item is visible below this section
            if (section.hasItems()) {
                boolean hasVisibleItem = section.itemTitles.stream()
                        .anyMatch(t -> isTextVisible(driver, t));

                if (!hasVisibleItem) {
                    emptySections++;
                    emptySectionNames.add(section.sectionTitle);
                    System.out.printf("  ⚠️  EMPTY SECTION : \"%s\" — title visible but no items%n",
                            section.sectionTitle);
                } else {
                    System.out.printf("  ✅ \"%s\" — loaded%n", section.sectionTitle);
                }
            } else {
                System.out.printf("  ✅ \"%s\" — visible%n", section.sectionTitle);
            }
        }

        // ── 5. Summary ────────────────────────────────────────────────────────
        int apiTotal    = sections.size();
        int matchPct    = apiTotal > 0 ? (visibleSections * 100 / apiTotal) : 0;

        System.out.println("\n── verifyCLPFullyLoaded Summary ──────────────");
        System.out.println("  Page              : " + page.displayName);
        System.out.println("  Data source       : " + dataSource);
        System.out.println("  API sections      : " + apiTotal);
        System.out.println("  Visible on screen : " + visibleSections + " (" + matchPct + "%)");
        System.out.println("  Missing sections  : " + missingSections.size());
        System.out.println("  Empty sections    : " + emptySections);
        System.out.println("  Spinners found    : " + spinnersFound.size());
        if (!missingSections.isEmpty()) {
            System.out.println("  Missing list:");
            missingSections.forEach(t -> System.out.println("    - " + t));
        }
        System.out.println("──────────────────────────────────────────────\n");

        // ── 6. Fail conditions ────────────────────────────────────────────────
        if (!spinnersFound.isEmpty()) {
            throw new RuntimeException(
                    "verifyCLPFullyLoaded FAILED: loading indicators still visible for "
                    + page.displayName + ": " + spinnersFound);
        }
        if (!emptySectionNames.isEmpty()) {
            throw new RuntimeException(
                    "verifyCLPFullyLoaded FAILED: " + emptySections
                    + " section(s) have no visible items: " + emptySectionNames);
        }
        if (!missingSections.isEmpty()) {
            throw new RuntimeException(
                    "verifyCLPFullyLoaded FAILED: " + missingSections.size()
                    + " section(s) from API not visible on screen: " + missingSections);
        }
    }

    // ── API fetch ─────────────────────────────────────────────────────────────

    /**
     * Fetches sections from the live CLP API.
     * Returns empty list on any error (caller falls back to TestContext).
     */
    private List<ClpSection> fetchFromApi(ClpApiClient.Page page) {
        try {
            System.out.println("  Fetching fresh CLP data from API …");
            ClpApiClient client   = new ClpApiClient("null");
            List<JsonNode> nodes  = client.fetchAllSections(page);

            List<ClpSection> result = new ArrayList<>();
            for (JsonNode node : nodes) {
                String cardType = textOf(node, "cardType", "card_type", "type");
                if (SKIP_TYPES.contains(cardType)) continue;

                String title = textOf(node, "title");
                if (title.isEmpty()) continue;

                // Collect item titles for empty-section check
                List<String> itemTitles = new ArrayList<>();
                for (String listField : new String[]{"data", "widgets", "items", "cards", "products"}) {
                    JsonNode list = node.path(listField);
                    if (!list.isArray() || list.isEmpty()) continue;
                    for (JsonNode item : list) {
                        String t = textOf(item, "title", "name", "brandName", "brand_name");
                        if (!t.isEmpty()) itemTitles.add(t);
                    }
                    break;
                }

                result.add(new ClpSection(
                        title, "", cardType,
                        itemTitles,
                        new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                        new ArrayList<>(), new ArrayList<>(),
                        new ArrayList<>(), new ArrayList<>(), false));
            }
            System.out.println("  ✅ API returned " + result.size() + " sections");
            return result;

        } catch (Exception e) {
            System.out.println("  ⚠️  API fetch failed — using TestContext data. Reason: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private List<String> findLoadingIndicators(AppiumDriver driver) {
        List<String> found = new ArrayList<>();
        for (String indicator : LOADING_INDICATORS) {
            try {
                List<?> byId = driver.findElements(AppiumBy.androidUIAutomator(
                        "new UiSelector().resourceIdMatches(\".*" + indicator + ".*\")"));
                if (!byId.isEmpty()) found.add(indicator + "(resourceId)");

                List<?> byDesc = driver.findElements(AppiumBy.androidUIAutomator(
                        "new UiSelector().descriptionContains(\"" + indicator + "\")"));
                if (!byDesc.isEmpty()) found.add(indicator + "(desc)");

                if (indicator.equals("progress")) {
                    List<?> byClass = driver.findElements(AppiumBy.androidUIAutomator(
                            "new UiSelector().className(\"android.widget.ProgressBar\")"));
                    if (!byClass.isEmpty()) found.add("ProgressBar");
                }
            } catch (Exception ignored) {}
        }
        return found;
    }

    private void scrollToText(AppiumDriver driver, String text) throws InterruptedException {
        for (int s = 0; s <= TOTAL_SCROLLS; s++) {
            if (isTextVisible(driver, text)) return;
            scrollDown(driver);
            Thread.sleep(SCROLL_WAIT);
        }
    }

    private boolean isTextVisible(AppiumDriver driver, String text) {
        try {
            String escaped = text.replace("\"", "\\\"");
            return !driver.findElements(AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"" + escaped + "\")")).isEmpty();
        } catch (Exception e) { return false; }
    }

    private void scrollToTop(AppiumDriver driver) {
        GestureUtil.scrollToTop(driver, 6);
    }

    private void scrollDown(AppiumDriver driver) {
        try {
            GestureUtil.swipe(driver, "up");
        } catch (Exception e) {
            System.out.println("  ⚠️  Scroll failed: " + e.getMessage());
        }
    }

    private String textOf(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = node.path(f).asText("").trim();
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        return "";
    }
}
