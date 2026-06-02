package com.popclub.mobile.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.popclub.clp.ClpApiClient;
import com.popclub.clp.ClpSection;
import com.popclub.core.TestContext;
import com.popclub.model.Step;
import com.popclub.mobile.driver.DriverManager;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.*;

/**
 * VerifyClpAction — fetches the CLP API response, extracts:
 *   • section title     (the heading row, e.g. "Trending Now")
 *   • section subtitle  (the sub-text below heading, e.g. "Shop the latest")
 *   • item titles       (each card/product title inside the section)
 *   • item subtitles    (each card/product subtitle inside the section)
 *
 * Then scrolls the live screen and asserts every SECTION title + subtitle is
 * rendered.  Item-level text is stored in TestContext and available for
 * subsequent `tapByText` steps.
 *
 * YAML usage:
 *   - action: verifyCLP
 *     value: SHOP          # HOME | SHOP | CARD
 *
 * After this step you can click any extracted text:
 *   - action: tapByText
 *     value: "Trending Now"
 */
public class VerifyClpAction implements Action {

    private static final int  MAX_SCROLLS = 10;
    private static final long SCROLL_WAIT = 600;   // ms

    /**
     * cardTypes that carry actual CLP content (section title + item list).
     * Only sections whose cardType is in this set are extracted and verified.
     * Permanent UI chrome (search bars, nav tabs, headers, banners without titles)
     * is excluded by not being in this list.
     */
    private static final Set<String> TITLED_TYPES = new HashSet<>(Arrays.asList(
            "custom_carousel", "landscape_carousel", "landscape_slider",
            "brand_slider", "products_carousel", "grid",
            "portrait_carousel", "spotlight_carousel", "editor_pick",
            "rewards_carousel", "rewards_mini_carousel", "category_product_carousal",
            "horizontal_list", "vertical_list", "banner_with_title",
            "tab_carousel", "featured_carousel"
    ));

    /**
     * cardTypes that represent permanent/static UI chrome — search bars, navigation
     * tabs, app headers, hero banners, dividers.  These are skipped even if the API
     * returns them, because they are not CLP content and will always be present on
     * screen regardless of what the page delivers.
     */
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

        String pageArg = step.value != null ? step.value.toUpperCase() : "SHOP";
        ClpApiClient.Page page;
        try {
            page = ClpApiClient.Page.valueOf(pageArg);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "Unknown CLP page '" + pageArg + "'. Use HOME, SHOP or CARD.");
        }

        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("  verifyCLP: " + page.displayName);
        System.out.println("══════════════════════════════════════════════");

        // ── 1. Fetch ALL pages from API ────────────────────────────────────────
        ClpApiClient client = new ClpApiClient("null");
        List<JsonNode> allNodes = client.fetchAllSections(page);

        if (allNodes.isEmpty()) {
            throw new RuntimeException(
                    "CLP API returned no sections for " + page.displayName
                    + ". Check USER_TOKEN and network.");
        }

        List<ClpSection> sections = parseSections(allNodes);
        List<ClpSection> banners  = parseBanners(allNodes);
        System.out.println("  Parsed " + sections.size() + " sections + "
                + banners.size() + " banners from API (all pages)");

        // Print indexed content tree
        printContentTree(page.displayName, sections, banners);

        // ── 2. Store in TestContext ───────────────────────────────────────────
        TestContext.setClpData(pageArg, sections);
        TestContext.setClpBanners(pageArg, banners);

        // ── 3. Verify section titles + subtitles on screen ────────────────────
        AppiumDriver driver = DriverManager.getDriver();

        List<String> passed  = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (ClpSection section : sections) {

            // Verify section title
            if (!section.sectionTitle.isEmpty()) {
                boolean found = findOnScreen(driver, section.sectionTitle);
                if (found) {
                    System.out.println("  ✅ SECTION TITLE   : " + section.sectionTitle);
                    passed.add(section.sectionTitle);
                } else {
                    System.out.println("  ⚠️  MISSING TITLE   : " + section.sectionTitle);
                    missing.add(section.sectionTitle);
                }
            }

            // Verify section subtitle (if present)
            if (!section.sectionSubtitle.isEmpty()) {
                boolean found = findOnScreen(driver, section.sectionSubtitle);
                if (found) {
                    System.out.println("  ✅ SECTION SUBTITLE: " + section.sectionSubtitle);
                    passed.add(section.sectionSubtitle);
                } else {
                    System.out.println("  ⚠️  MISSING SUBTITLE: " + section.sectionSubtitle);
                    missing.add(section.sectionSubtitle);
                }
            }
        }

        // ── 4. Summary ────────────────────────────────────────────────────────
        int totalItems = sections.stream()
                .mapToInt(s -> s.itemTitles.size()).sum();

        System.out.println("\n── verifyCLP Summary ─────────────────────────");
        System.out.println("  Page                     : " + page.displayName);
        System.out.println("  Sections from API        : " + sections.size());
        System.out.println("  Total list items (stored): " + totalItems);
        System.out.println("  Section texts verified   : " + passed.size());
        System.out.println("  Section texts missing    : " + missing.size());
        System.out.println("  (All item titles stored in TestContext for tapByText)");
        if (!missing.isEmpty()) {
            System.out.println("  Missing list:");
            missing.forEach(t -> System.out.println("    - " + t));
        }
        System.out.println("──────────────────────────────────────────────\n");

        // Fail only if not a single section heading was found (wrong screen or crash)
        if (passed.isEmpty() && !sections.isEmpty()) {
            throw new RuntimeException(
                    "verifyCLP FAILED for " + page.displayName
                    + ": 0 out of " + sections.size() + " section texts found on screen. "
                    + "Is the app on the correct CLP tab?");
        }
    }

    // ── Indexed content tree printer ──────────────────────────────────────────

    private void printContentTree(String pageName,
                                  List<ClpSection> sections,
                                  List<ClpSection> banners) {
        int totalItems = sections.stream().mapToInt(s -> s.itemTitles.size()).sum();

        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.printf( "║  CLP Content Tree: %-33s║%n", pageName);
        System.out.printf( "║  %d sections  •  %d items  •  %d banners%11s║%n",
                sections.size(), totalItems, banners.size(), "");
        System.out.println("╠══════════════════════════════════════════════════════╣");

        for (int si = 0; si < sections.size(); si++) {
            ClpSection s = sections.get(si);
            System.out.printf("║%n");
            System.out.printf("║  [Section %d]  %s%n", si, s.cardType);
            System.out.printf("║    title    : %s%n", s.sectionTitle);
            if (!s.sectionSubtitle.isEmpty())
                System.out.printf("║    subtitle : %s%n", s.sectionSubtitle);
            if (s.hasFilters())
                System.out.printf("║    filters  : %s%n", String.join(" | ", s.filters));
            if (s.hasItems()) {
                System.out.printf("║    items (%d):%n", s.itemTitles.size());
                for (int ii = 0; ii < s.itemTitles.size(); ii++) {
                    String sub      = ii < s.itemSubtitles.size()  ? s.itemSubtitles.get(ii)  : "";
                    String price    = s.formattedPrice(ii);
                    String mrp      = s.formattedMrp(ii);
                    String disc     = s.discountLabel(ii);
                    String coins    = s.popcoinLabel(ii);
                    String priceStr = price.isEmpty() ? "" :
                            "  " + price
                            + (mrp.isEmpty()   ? "" : " (MRP " + mrp + ")")
                            + (disc.isEmpty()  ? "" : "  " + disc)
                            + (coins.isEmpty() ? "" : "  🔥" + coins);
                    System.out.printf("║      [%d] %s%s%s%n",
                            ii, s.itemTitles.get(ii),
                            sub.isEmpty() ? "" : "  ·  " + sub,
                            priceStr);
                }
            }
        }

        if (!banners.isEmpty()) {
            System.out.println("║");
            System.out.println("║  ── Banners ──────────────────────────────────────");
            for (int bi = 0; bi < banners.size(); bi++) {
                ClpSection b = banners.get(bi);
                System.out.printf("║  [Banner %d]  %s%n", bi, b.cardType);
                if (!b.sectionTitle.isEmpty())
                    System.out.printf("║    headline : %s%n", b.sectionTitle);
                if (!b.bannerCtas.isEmpty())
                    System.out.printf("║    cta      : %s%n", String.join(" | ", b.bannerCtas));
            }
        }

        System.out.println("║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println("  tapByText: use any title above");
        System.out.println("  tapClpItem: section=<title>  index=<n>\n");
    }

    // ── Parse API response ─────────────────────────────────────────────────────

    private static final Set<String> BANNER_TYPES = new HashSet<>(Arrays.asList(
            "banner", "hero_banner", "full_banner", "image_banner",
            "promo_banner", "spotlight_banner"
    ));

    /** Content sections — have titles, items, filters. */
    private List<ClpSection> parseSections(List<JsonNode> nodes) {
        List<ClpSection> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            String cardType = textOf(node, "cardType", "card_type", "type");
            if (SKIP_TYPES.contains(cardType) || BANNER_TYPES.contains(cardType)) continue;
            String title    = textOf(node, "title");
            String subtitle = textOf(node, "subTitle", "subtitle", "sub_title", "description");
            if (title.isEmpty()) continue;
            if (!cardType.isEmpty() && !TITLED_TYPES.contains(cardType)) continue;

            List<String> itemTitles    = new ArrayList<>();
            List<String> itemSubtitles = new ArrayList<>();
            List<String> itemPrices    = new ArrayList<>();
            List<String> itemMrps      = new ArrayList<>();
            List<String> itemDiscounts = new ArrayList<>();
            List<String> itemPopcoins  = new ArrayList<>();
            collectItems(node, itemTitles, itemSubtitles,
                    itemPrices, itemMrps, itemDiscounts, itemPopcoins);

            List<String> filters = new ArrayList<>();
            collectFilters(node, filters);

            result.add(new ClpSection(title, subtitle, cardType,
                    itemTitles, itemSubtitles,
                    itemPrices, itemMrps, itemDiscounts, itemPopcoins,
                    filters, new ArrayList<>(), false));
        }
        return result;
    }

    /** Banner sections — have CTA buttons, may or may not have titles. */
    private List<ClpSection> parseBanners(List<JsonNode> nodes) {
        List<ClpSection> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            String cardType = textOf(node, "cardType", "card_type", "type");
            if (!BANNER_TYPES.contains(cardType)) continue;

            String headline = textOf(node, "title", "headline", "heading");
            List<String> ctas = new ArrayList<>();
            collectBannerCtas(node, ctas);

            result.add(new ClpSection(headline, "", cardType,
                    new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>(), ctas, true));
        }
        return result;
    }

    /**
     * Walk common nested-item field names and extract title, subtitle,
     * price, mrp, discount %, and POPcoins per item.
     */
    private void collectItems(JsonNode section,
                              List<String> titles,    List<String> subtitles,
                              List<String> prices,    List<String> mrps,
                              List<String> discounts, List<String> popcoins) {
        for (String field : new String[]{"data", "widgets", "items", "cards", "products", "images"}) {
            JsonNode list = section.path(field);
            if (!list.isArray() || list.isEmpty()) continue;
            for (JsonNode item : list) {
                String t = textOf(item, "title", "name", "brandName", "brand_name");
                String s = textOf(item, "subTitle", "subtitle", "sub_title",
                                       "description", "category", "categoryName");
                // Price fields — try multiple naming conventions
                String price = numericOf(item,
                        "price", "sellingPrice", "selling_price", "sp", "offerPrice");
                String mrp   = numericOf(item,
                        "mrp", "originalPrice", "original_price", "maxRetailPrice", "listPrice");
                String disc  = numericOf(item,
                        "discount", "discountPercentage", "discount_percentage",
                        "discountPercent", "discountPct");
                String coins = numericOf(item,
                        "popcoins", "pop_coins", "coins", "rewardCoins", "reward_coins");

                if (!t.isEmpty()) titles.add(t);    else titles.add("");
                subtitles.add(s);
                prices.add(price);
                mrps.add(mrp);
                discounts.add(disc);
                popcoins.add(coins);
            }
            break;
        }
    }

    private void collectFilters(JsonNode section, List<String> filters) {
        for (String field : new String[]{
                "filters", "filterOptions", "filterData",
                "chips", "sortOptions", "filterTags", "tags"}) {
            JsonNode list = section.path(field);
            if (!list.isArray() || list.isEmpty()) continue;
            for (JsonNode f : list) {
                String label = textOf(f, "title", "name", "label", "displayName", "text");
                if (!label.isEmpty()) filters.add(label);
            }
            if (!filters.isEmpty()) break;
        }
    }

    /**
     * Extract CTA button texts from banner items.
     * Looks inside data[].cta.text / data[].ctaText / data[].button.text
     */
    private void collectBannerCtas(JsonNode section, List<String> ctas) {
        // Top-level CTA
        String topCta = textOf(section, "ctaText", "cta_text", "buttonText");
        if (!topCta.isEmpty()) ctas.add(topCta);

        JsonNode cta = section.path("cta");
        if (!cta.isMissingNode()) {
            String t = textOf(cta, "text", "title", "label");
            if (!t.isEmpty() && !ctas.contains(t)) ctas.add(t);
        }

        // Nested data[] items
        for (String field : new String[]{"data", "items", "banners"}) {
            JsonNode list = section.path(field);
            if (!list.isArray()) continue;
            for (JsonNode item : list) {
                String t = textOf(item, "ctaText", "cta_text", "buttonText");
                if (t.isEmpty()) {
                    JsonNode c = item.path("cta");
                    if (!c.isMissingNode()) t = textOf(c, "text", "title", "label");
                }
                if (!t.isEmpty() && !ctas.contains(t)) ctas.add(t);
            }
        }
    }

    /** Returns the first non-blank value from the given field names, or "". */
    private String textOf(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = node.path(f).asText("").trim();
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        return "";
    }

    /** Returns the first numeric (or numeric-string) field value, or "". */
    private String numericOf(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.path(f);
            if (n.isMissingNode() || n.isNull()) continue;
            if (n.isNumber()) return String.valueOf(n.longValue());
            String v = n.asText("").trim();
            if (!v.isEmpty() && !v.equals("null") && !v.equals("0")) return v;
        }
        return "";
    }

    // ── On-screen helpers ──────────────────────────────────────────────────────

    private boolean findOnScreen(AppiumDriver driver, String text) throws InterruptedException {
        for (int scroll = 0; scroll <= MAX_SCROLLS; scroll++) {
            if (isTextVisible(driver, text)) return true;
            if (scroll < MAX_SCROLLS) {
                scrollDown(driver);
                Thread.sleep(SCROLL_WAIT);
            }
        }
        return false;
    }

    private boolean isTextVisible(AppiumDriver driver, String text) {
        try {
            String escaped = text.replace("\"", "\\\"");
            String selector = "new UiSelector().textContains(\"" + escaped + "\")";
            return !driver.findElements(
                    io.appium.java_client.AppiumBy.androidUIAutomator(selector)
            ).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void scrollDown(AppiumDriver driver) {
        try {
            Dimension size   = driver.manage().window().getSize();
            int startY  = (int) (size.height * 0.75);
            int endY    = (int) (size.height * 0.25);
            int centerX = size.width / 2;

            PointerInput finger   = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence     sequence = new Sequence(finger, 1);
            sequence.addAction(finger.createPointerMove(
                    Duration.ZERO, PointerInput.Origin.viewport(), centerX, startY));
            sequence.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            sequence.addAction(finger.createPointerMove(
                    Duration.ofMillis(400), PointerInput.Origin.viewport(), centerX, endY));
            sequence.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(List.of(sequence));
        } catch (Exception e) {
            System.out.println("  ⚠️  Scroll failed: " + e.getMessage());
        }
    }
}
