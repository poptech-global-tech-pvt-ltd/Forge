package com.popclub.android.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.popclub.clp.ClpApiClient;
import com.popclub.clp.ClpSection;
import com.popclub.core.TestContext;
import com.popclub.model.Step;
import com.popclub.android.driver.DriverManager;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.*;

/**
 * ClpScrollAndCheckAction — verifies shop CLP using indexed accessibilityId tags.
 *
 * Steps for each section at index i:
 *   1. Fetch shopmerch-30 presentation API to get all sections
 *   2. Scroll to clp_section_row_i (AccessibilityId)
 *   3. Read text from clp_section_title_i  (AccessibilityId)
 *   4. Assert read title matches API section title
 *   5. Log ✅ match / ❌ mismatch in a report table
 *
 * YAML:
 *   - action: clpScrollAndCheck
 *     value: SHOP       # HOME | SHOP | CARD
 */
public class ClpScrollAndCheckAction implements Action {

    private static final int    MAX_SCROLL_ATTEMPTS = 4;
    private static final long   SCROLL_WAIT_MS      = 600;
    private static final String ROW_TAG_PREFIX       = "clp_section_row_";
    private static final String TITLE_TAG_PREFIX     = "clp_section_title_";

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
        ClpApiClient.Page page = ClpApiClient.Page.valueOf(pageArg);

        System.out.println("\n══ clpScrollAndCheck: " + page.displayName + " ══");

        // ── 1. Fetch presentation API ─────────────────────────────────────────
        ClpApiClient client = new ClpApiClient(resolveUserId());
        List<JsonNode> allNodes = client.fetchAllSections(page);

        if (allNodes.isEmpty()) {
            throw new RuntimeException("CLP API returned no sections for " + page.displayName);
        }

        List<ClpSection> sections = parseSections(allNodes, page);
        System.out.println("  API returned " + sections.size() + " content sections");

        // Store in TestContext so downstream steps can use it
        TestContext.setClpData(pageArg, sections);

        // ── 2. Scroll & check each section ────────────────────────────────────
        AppiumDriver driver = DriverManager.getDriver();

        List<String[]> report = new ArrayList<>(); // [index, apiTitle, uiTitle, status]
        int matched   = 0;
        int mismatched = 0;
        int noTag     = 0;

        for (int i = 0; i < sections.size(); i++) {
            ClpSection section = sections.get(i);
            String rowTag   = ROW_TAG_PREFIX   + i;
            String titleTag = TITLE_TAG_PREFIX + i;

            // Scroll to the section container
            boolean rowFound = scrollToAccessibilityId(driver, rowTag);

            if (!rowFound) {
                String reason = "row tag not found";
                System.out.printf("  [%2d] %-35s  — %-30s  ⚠️  %s%n",
                        i, section.sectionTitle, "", reason);
                report.add(new String[]{String.valueOf(i), section.sectionTitle, "", reason});
                noTag++;
                continue;
            }

            // Read the title from the tagged Text element
            String uiTitle = readAccessibilityText(driver, titleTag);

            if (uiTitle == null) {
                // Section row is present but title tag was not found
                // (e.g. section has no title — grid-only sections)
                if (section.sectionTitle.isEmpty()) {
                    System.out.printf("  [%2d] %-35s  — (no title, expected)                  ✅%n", i, "(no title)");
                    report.add(new String[]{String.valueOf(i), "", "", "no title (expected)"});
                    matched++;
                } else {
                    System.out.printf("  [%2d] %-35s  — title tag missing on screen           ⚠️%n",
                            i, section.sectionTitle);
                    report.add(new String[]{String.valueOf(i), section.sectionTitle, "", "title tag missing"});
                    noTag++;
                }
                continue;
            }

            // Compare API title vs UI title
            boolean matches = normalize(section.sectionTitle).equals(normalize(uiTitle));
            String status = matches ? "✅" : "❌";

            System.out.printf("  [%2d] API: %-30s  UI: %-30s  %s%n",
                    i, section.sectionTitle, uiTitle, status);

            report.add(new String[]{String.valueOf(i), section.sectionTitle, uiTitle, status});

            if (matches) matched++;
            else         mismatched++;
        }

        // ── 3. Summary report ─────────────────────────────────────────────────
        System.out.println("\n── clpScrollAndCheck Report ──────────────────────────────────────");
        System.out.printf("  Page          : %s%n", page.displayName);
        System.out.printf("  Total sections: %d%n", sections.size());
        System.out.printf("  ✅ Matched    : %d%n", matched);
        System.out.printf("  ❌ Mismatched : %d%n", mismatched);
        System.out.printf("  ⚠️  No tag     : %d%n", noTag);

        if (mismatched > 0) {
            System.out.println("\n  Mismatched sections:");
            for (String[] row : report) {
                if ("❌".equals(row[3])) {
                    System.out.printf("    [%s] API: \"%s\"  →  UI: \"%s\"%n",
                            row[0], row[1], row[2]);
                }
            }
        }
        System.out.println("──────────────────────────────────────────────────────────────────\n");

        if (mismatched > 0) {
            throw new RuntimeException(
                    "clpScrollAndCheck FAILED: " + mismatched + " section title(s) mismatch. "
                    + "See report above.");
        }

        if (matched == 0 && sections.size() > 0) {
            throw new RuntimeException(
                    "clpScrollAndCheck FAILED: no section rows found on screen. "
                    + "Is the app on the Shop CLP? Are clp_section_row_N tags applied?");
        }
    }

    // ── Scroll to accessibilityId ─────────────────────────────────────────────

    private boolean scrollToAccessibilityId(AppiumDriver driver, String accessibilityId) {
        // First try without scrolling
        if (findById(driver, accessibilityId) != null) return true;

        // Scroll down and retry
        for (int attempt = 0; attempt < MAX_SCROLL_ATTEMPTS; attempt++) {
            scrollDown(driver);
            try { Thread.sleep(SCROLL_WAIT_MS); } catch (InterruptedException ignored) {}
            if (findById(driver, accessibilityId) != null) return true;
        }
        return false;
    }

    private WebElement findById(AppiumDriver driver, String accessibilityId) {
        try {
            List<WebElement> els = driver.findElements(AppiumBy.accessibilityId(accessibilityId));
            if (!els.isEmpty()) return els.get(0);
        } catch (Exception ignored) {}
        return null;
    }

    // ── Read text from accessibilityId element ─────────────────────────────────

    private String readAccessibilityText(AppiumDriver driver, String accessibilityId) {
        try {
            WebElement el = findById(driver, accessibilityId);
            if (el == null) return null;
            String text = el.getText();
            return (text != null && !text.isBlank()) ? text.trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── Scroll helper ─────────────────────────────────────────────────────────

    private void scrollDown(AppiumDriver driver) {
        Dimension size   = driver.manage().window().getSize();
        int       startY = (int) (size.height * 0.75);
        int       endY   = (int) (size.height * 0.30);
        int       centerX = size.width / 2;

        PointerInput finger   = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence     sequence = new Sequence(finger, 1);
        sequence.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), centerX, startY));
        sequence.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        sequence.addAction(finger.createPointerMove(Duration.ofMillis(400), PointerInput.Origin.viewport(), centerX, endY));
        sequence.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(List.of(sequence));
    }

    // ── Parse sections from raw API nodes ────────────────────────────────────

    private static final Set<String> TITLED_TYPES = new HashSet<>(Arrays.asList(
            "custom_carousel", "landscape_carousel", "landscape_slider",
            "brand_slider", "products_carousel", "grid",
            "portrait_carousel", "spotlight_carousel", "editor_pick",
            "rewards_carousel", "rewards_mini_carousel", "category_product_carousal",
            "horizontal_list", "vertical_list", "banner_with_title",
            "tab_carousel", "featured_carousel"
    ));

    private static final Set<String> SKIP_TYPES = new HashSet<>(Arrays.asList(
            "search_bar", "search", "search_widget",
            "header", "toolbar", "app_bar", "top_bar",
            "tab", "tabs", "bottom_tab", "bottom_nav", "nav_bar", "navigation",
            "banner", "hero_banner", "full_banner", "image_banner",
            "divider", "spacer", "separator", "static", "static_text", "label"
    ));

    private List<ClpSection> parseSections(List<JsonNode> nodes, ClpApiClient.Page page) {
        List<ClpSection> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            String cardType = textOf(node, "cardType", "card_type", "type");
            if (SKIP_TYPES.contains(cardType)) continue;

            String title    = textOf(node, "title");
            String subtitle = textOf(node, "subTitle", "subtitle", "sub_title");

            // Include sections with or without titles (to match widget position index)
            if (!cardType.isEmpty() && !TITLED_TYPES.contains(cardType)) continue;

            List<String> itemTitles = new ArrayList<>();
            List<String> itemPrices = new ArrayList<>();
            collectItems(node, itemTitles, itemPrices);

            result.add(new ClpSection(
                    title, subtitle, cardType,
                    itemTitles, new ArrayList<>(),
                    itemPrices, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>(), new ArrayList<>(), false));
        }
        return result;
    }

    private void collectItems(JsonNode node, List<String> titles, List<String> prices) {
        JsonNode images = node.path("images");
        if (!images.isArray()) return;
        for (JsonNode img : images) {
            String title = textOf(img, "title", "text", "name");
            JsonNode product = img.path("product");
            if (!product.isMissingNode()) {
                title = textOf(product, "productTitle", "title", "name");
                String price = textOf(product.path("price"), "inclTax", "selling_price");
                prices.add(price);
            } else {
                prices.add("");
            }
            if (!title.isEmpty()) titles.add(title);
        }
    }

    private String textOf(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.path(f);
            if (!n.isMissingNode() && n.isTextual() && !n.asText().isBlank()) {
                return n.asText().trim();
            }
        }
        return "";
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private String resolveUserId() {
        String token = TestContext.getUserToken();
        return (token != null && !token.isBlank()) ? "null" : "null";
    }
}
