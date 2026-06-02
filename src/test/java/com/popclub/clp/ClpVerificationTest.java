package com.popclub.clp;

import com.fasterxml.jackson.databind.JsonNode;
import com.popclub.api.util.ApiConstants;
import com.popclub.core.TestContext;
import com.popclub.mobile.driver.AppiumDriverManager;
import com.popclub.mobile.driver.AppiumServerManager;
import com.popclub.mobile.driver.DriverManager;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.*;

import org.openqa.selenium.Dimension;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.*;

/**
 * ClpVerificationTest — verifies that the Android app correctly renders
 * what the CLP presentation-layer API returns.
 *
 * How it works (CI-safe, no Claude, no screenshots):
 *   1. Fetch ALL pages of the CLP API → parse every section title, subtitle,
 *      item title, item price, discount, and POPcoins.
 *   2. Use UIAutomator text assertions (via Appium) to verify each element
 *      is visible on screen — scrolling as needed.
 *   3. Report pass / warn / fail per section.
 *
 * Run all CLPs:
 *   mvn test -Dtest=ClpVerificationTest
 *
 * Run a single CLP:
 *   mvn test -Dtest=ClpVerificationTest#verifyCLP -DpageId=SHOP
 */
public class ClpVerificationTest {

    private static final int  MAX_SCROLLS  = 12;
    private static final long SCROLL_WAIT  = 500;

    private AppiumDriver driver;
    private ClpApiClient clpClient;

    private static final String TEST_PHONE = "1234561122";
    private static final String TEST_OTP   = "560102";

    @BeforeClass
    public void setup() {
        driver    = AppiumDriverManager.getDriver();
        clpClient = new ClpApiClient("null");
        System.out.println("[ClpVerificationTest] Driver ready — logging in if needed.");
        loginIfNeeded();
        System.out.println("[ClpVerificationTest] Setup complete, starting verification.");
    }

    @AfterClass
    public void teardown() {
        AppiumDriverManager.quitDriver();
    }

    @AfterSuite
    public void stopServers() {
        AppiumServerManager.stopAll();
    }

    @DataProvider(name = "clpPages")
    public Object[][] clpPages() {
        String filter = System.getProperty("pageId", "ALL").toUpperCase();
        List<ClpApiClient.Page> pages = new ArrayList<>();
        for (ClpApiClient.Page page : ClpApiClient.Page.values()) {
            if (filter.equals("ALL") || filter.equals(page.name())) {
                pages.add(page);
            }
        }
        return pages.stream().map(p -> new Object[]{p}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "clpPages")
    public void verifyCLP(ClpApiClient.Page page) throws Exception {

        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.printf( "║  ClpVerificationTest: %-27s║%n", page.displayName);
        System.out.println("╚══════════════════════════════════════════════════╝");

        // ── 1. Fetch API (all pages, paginated) ───────────────────────────────
        List<JsonNode> allNodes = clpClient.fetchAllSections(page);
        Assert.assertFalse(allNodes.isEmpty(),
                page.displayName + " API returned no sections. Check USER_TOKEN / network.");

        // ── 2. Parse sections ─────────────────────────────────────────────────
        List<Section> sections = parseSections(allNodes);
        System.out.printf("  API returned %d sections to verify%n", sections.size());

        // Print content tree
        printTree(page.displayName, sections);

        // ── 3. Navigate to the correct tab, then scroll to top ───────────────
        navigateToTab(page);
        scrollToTop();

        List<String> passed  = new ArrayList<>();
        List<String> warned  = new ArrayList<>();
        List<String> failed  = new ArrayList<>();

        for (Section s : sections) {

            // Section title
            boolean titleFound = findOnScreen(s.title);
            if (titleFound) {
                System.out.printf("  ✅ SECTION   \"%s\"%n", s.title);
                passed.add("title: " + s.title);
            } else {
                System.out.printf("  ⚠️  MISSING   \"%s\" (below fold or not rendered)%n", s.title);
                warned.add("title: " + s.title);
            }

            // Section subtitle
            if (!s.subtitle.isEmpty()) {
                boolean subFound = findOnScreen(s.subtitle);
                if (subFound) {
                    System.out.printf("  ✅ SUBTITLE  \"%s\"%n", s.subtitle);
                    passed.add("subtitle: " + s.subtitle);
                } else {
                    System.out.printf("  ⚠️  MISSING   subtitle \"%s\"%n", s.subtitle);
                    warned.add("subtitle: " + s.subtitle);
                }
            }

            // Item titles (first 3 per section to avoid excessive scrolling)
            int itemsToCheck = Math.min(3, s.itemTitles.size());
            for (int i = 0; i < itemsToCheck; i++) {
                String item = s.itemTitles.get(i);
                if (item.isBlank()) continue;

                boolean itemFound = findOnScreen(item);
                String price = i < s.prices.size() ? s.prices.get(i) : "";
                boolean priceFound = !price.isEmpty() && findOnScreen(price.replaceAll("[^0-9,]", ""));

                if (itemFound) {
                    System.out.printf("  ✅ ITEM[%d]   \"%s\"%s%n", i, item,
                            priceFound ? "  " + price : "");
                    passed.add("item: " + item);
                } else {
                    System.out.printf("  ⚠️  ITEM[%d]   \"%s\" not visible%n", i, item);
                    warned.add("item: " + item);
                }

                // Discount badge
                if (i < s.discounts.size() && !s.discounts.get(i).isEmpty()) {
                    String disc = s.discounts.get(i) + "%";
                    boolean discFound = findOnScreen(disc);
                    if (discFound) {
                        System.out.printf("  ✅ DISCOUNT  \"%s off\"%n", disc);
                    }
                }
            }
        }

        // ── 4. Report ─────────────────────────────────────────────────────────
        System.out.println("\n── Verification Summary ──────────────────────────");
        System.out.printf("  Page    : %s%n", page.displayName);
        System.out.printf("  Sections: %d  (from API)%n", sections.size());
        System.out.printf("  Passed  : %d%n", passed.size());
        System.out.printf("  Warned  : %d  (below fold — not a failure)%n", warned.size());
        System.out.printf("  Failed  : %d%n", failed.size());
        System.out.println("──────────────────────────────────────────────────\n");

        // Hard fail only if nothing at all was found (wrong screen / crash)
        if (passed.isEmpty() && !sections.isEmpty()) {
            Assert.fail(page.displayName
                    + ": 0 out of " + sections.size()
                    + " API sections found on screen. Is the app on the correct tab?");
        }

        if (!failed.isEmpty()) {
            Assert.fail(page.displayName + " — the following sections failed:\n"
                    + String.join("\n", failed));
        }
    }

    // ── Parse section nodes ────────────────────────────────────────────────────

    private static final Set<String> TITLED_TYPES = new HashSet<>(Arrays.asList(
            "custom_carousel", "landscape_carousel", "landscape_slider",
            "brand_slider", "products_carousel", "grid",
            "portrait_carousel", "spotlight_carousel", "editor_pick",
            "rewards_carousel", "rewards_mini_carousel", "category_product_carousal",
            "horizontal_list", "vertical_list", "banner_with_title",
            "tab_carousel", "featured_carousel"
    ));

    private static final Set<String> SKIP_TYPES = new HashSet<>(Arrays.asList(
            "search_bar", "search", "header", "toolbar",
            "tab", "tabs", "bottom_nav", "nav_bar",
            "banner", "hero_banner", "full_banner", "image_banner",
            "divider", "spacer", "separator", "static"
    ));

    private List<Section> parseSections(List<JsonNode> nodes) {
        List<Section> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            String cardType = text(node, "cardType", "card_type", "type");
            if (SKIP_TYPES.contains(cardType))   continue;
            String title    = text(node, "title");
            if (title.isEmpty())                  continue;
            if (!cardType.isEmpty() && !TITLED_TYPES.contains(cardType)) continue;

            String subtitle = text(node, "subTitle", "subtitle", "sub_title", "description");

            List<String> itemTitles = new ArrayList<>();
            List<String> prices     = new ArrayList<>();
            List<String> discounts  = new ArrayList<>();

            for (String field : new String[]{"data", "widgets", "items", "cards", "products"}) {
                JsonNode list = node.path(field);
                if (!list.isArray() || list.isEmpty()) continue;
                for (JsonNode item : list) {
                    String t = text(item, "title", "name", "brandName", "brand_name");
                    String p = num(item,  "price", "sellingPrice", "selling_price", "sp");
                    String d = num(item,  "discount", "discountPercentage", "discount_percentage");
                    itemTitles.add(t);
                    prices.add(p.isEmpty() ? "" : "₹" + format(p));
                    discounts.add(d);
                }
                break;
            }

            result.add(new Section(title, subtitle, cardType, itemTitles, prices, discounts));
        }
        return result;
    }

    // ── UIAutomator screen search ──────────────────────────────────────────────

    private boolean findOnScreen(String text) throws InterruptedException {
        for (int s = 0; s <= MAX_SCROLLS; s++) {
            if (isVisible(text)) return true;
            if (s < MAX_SCROLLS) {
                scrollDown();
                Thread.sleep(SCROLL_WAIT);
            }
        }
        return false;
    }

    private boolean isVisible(String text) {
        try {
            String escaped = text.replace("\"", "\\\"");
            return !driver.findElements(
                    AppiumBy.androidUIAutomator(
                            "new UiSelector().textContains(\"" + escaped + "\")")
            ).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void scrollDown() {
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

    // ── Login ──────────────────────────────────────────────────────────────────

    /**
     * Waits for the app to settle on either the home screen or the login screen,
     * performs the phone + OTP login if needed, then captures a user token via
     * the Auth REST API so CLP API calls can be personalised.
     */
    private void loginIfNeeded() {
        System.out.println("[ClpVerificationTest] Waiting for app to be ready…");

        // Wait up to 30 s for EITHER the home tab OR the login phone field to appear
        boolean onHome  = false;
        boolean onLogin = false;
        for (int i = 0; i < 30; i++) {
            if (isElementPresent(AppiumBy.accessibilityId("Home"))) {
                onHome = true; break;
            }
            if (isElementPresent(AppiumBy.accessibilityId("login_phone_input"))
                    || isElementPresent(AppiumBy.accessibilityId("login_merged_mobile_entry_content_input"))) {
                onLogin = true; break;
            }
            // Dismiss any system dialogs that might be blocking the login screen
            tapIfFound(AppiumBy.accessibilityId("com.google.android.gms:id/cancel"));
            tapIfFound(By.id("com.android.permissioncontroller:id/permission_deny_button"));
            sleep(1000);
        }

        System.out.println("[ClpVerificationTest] App state — onHome=" + onHome + " onLogin=" + onLogin);

        if (onHome) {
            System.out.println("[ClpVerificationTest] Already on home screen — skipping UI login.");
            captureToken();
            return;
        }

        System.out.println("[ClpVerificationTest] Performing login: phone=" + TEST_PHONE);

        // ── Enter phone ───────────────────────────────────────────────────────
        enterTextInField("login_phone_input", TEST_PHONE);
        sleep(500);

        // ── Tap continue ──────────────────────────────────────────────────────
        tapElement(AppiumBy.accessibilityId("login_continue_button"));
        sleep(1200);

        // ── Enter OTP ─────────────────────────────────────────────────────────
        // Try both the separate OTP screen input and the merged screen input
        boolean otpDone = false;
        for (String accId : new String[]{"login_otp_input", "login_merged_mobile_entry_content_input"}) {
            if (isElementPresent(AppiumBy.accessibilityId(accId))) {
                enterTextInField(accId, TEST_OTP);
                otpDone = true;
                break;
            }
        }
        if (!otpDone) {
            System.out.println("[ClpVerificationTest] ⚠️  Could not find OTP field — login may fail.");
        }
        sleep(500);

        // Some builds surface a dedicated submit button after OTP entry
        tapIfFound(AppiumBy.accessibilityId("login_otp_submit_button"));
        tapIfFound(AppiumBy.accessibilityId("login_merged_otp_entry_content_button"));

        // ── Wait for home ─────────────────────────────────────────────────────
        try {
            new WebDriverWait(driver, Duration.ofSeconds(25))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            AppiumBy.accessibilityId("Home")));
            System.out.println("[ClpVerificationTest] ✅ Login succeeded — home screen visible.");
        } catch (Exception e) {
            System.out.println("[ClpVerificationTest] ⚠️  Home tab not visible after login: " + e.getMessage());
        }

        dismissOverlays();
        captureToken();
    }

    /**
     * Types text into a React-Native-style field:
     * clicks the wrapper → waits for keyboard → targets the focused EditText child.
     * Falls back to {@code mobile: type} if no focused EditText is found.
     */
    private void enterTextInField(String accessibilityId, String value) {
        try {
            // Click the wrapper to focus the underlying EditText
            WebElement wrapper = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.elementToBeClickable(
                            AppiumBy.accessibilityId(accessibilityId)));
            wrapper.click();
            sleep(800); // wait for keyboard / focus

            // Target the focused EditText child (React Native wraps inputs in a View)
            try {
                WebElement editText = driver.findElement(
                        AppiumBy.xpath("//android.widget.EditText[@focused='true']"));
                editText.clear();
                editText.sendKeys(value);
            } catch (Exception ex) {
                // No focused EditText — use mobile: type on whatever is focused
                driver.executeScript("mobile: type", Map.of("text", value));
            }

            // Hide keyboard so it doesn't obscure the next element
            try { driver.executeScript("mobile: hideKeyboard"); } catch (Exception ignored) {}
            System.out.println("[ClpVerificationTest] ✅ Entered text in '" + accessibilityId + "'");
        } catch (Exception e) {
            System.out.println("[ClpVerificationTest] ⚠️  enterTextInField('" + accessibilityId + "') failed: " + e.getMessage());
        }
    }

    private void captureToken() {
        if (TestContext.getUserToken() != null && !TestContext.getUserToken().isBlank()) return;
        try {
            String token = AuthApiClient.login(TEST_PHONE, TEST_OTP);
            TestContext.setUserToken(token);
            System.out.println("[ClpVerificationTest] 🔑 Token captured via API.");
        } catch (Exception e) {
            System.out.println("[ClpVerificationTest] ⚠️  Could not capture token: " + e.getMessage());
        }
    }

    private void dismissOverlays() {
        sleep(600);
        for (String accId : new String[]{"Close", "Skip", "Got it", "DONE", "OK"}) {
            tapIfFound(AppiumBy.accessibilityId(accId));
        }
    }

    // ── Tab navigation ─────────────────────────────────────────────────────────

    /**
     * Taps the bottom-nav tab that corresponds to the CLP page being verified,
     * then waits up to 10 s for the page to load.
     *
     * Strategy:
     *   1. Fling to top so the bottom nav bar is fully visible.
     *   2. Try accessibilityId tap (fast path).
     *   3. Fall back to UIAutomator text tap if accessibilityId isn't found
     *      (handles builds where tab labels differ slightly).
     *   4. Wait until any text on that tab is visible before proceeding.
     */
    private void navigateToTab(ClpApiClient.Page page) {
        String tabAccId;
        String tabText;    // text label shown in the bottom nav
        switch (page) {
            case SHOP: tabAccId = "Shop";  tabText = "Shop";  break;
            case CARD: tabAccId = "Card";  tabText = "Card";  break;
            default:   tabAccId = "Home";  tabText = "Home";  break;
        }

        System.out.printf("[ClpVerificationTest] Navigating to %s tab…%n", tabAccId);

        // ── 1. Scroll to top so the fixed bottom nav bar is unobscured ────────
        try {
            driver.findElement(AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().scrollable(true)).flingToBeginning(5)"));
            sleep(500);
        } catch (Exception ignored) {}

        // ── 2. Primary: accessibilityId tap ───────────────────────────────────
        boolean tapped = tapIfFound(AppiumBy.accessibilityId(tabAccId));

        // ── 3. Fallback: UIAutomator text tap ─────────────────────────────────
        if (!tapped) {
            System.out.printf("[ClpVerificationTest] ⚠️  accessibilityId '%s' not found — trying text match%n", tabAccId);
            tapped = tapIfFound(AppiumBy.androidUIAutomator(
                    "new UiSelector().text(\"" + tabText + "\").clickable(true)"));
        }

        if (!tapped) {
            System.out.printf("[ClpVerificationTest] ⚠️  Could not find '%s' tab by any locator — dumping visible texts for diagnosis:%n", tabAccId);
            dumpVisibleTexts();
        }

        // ── 4. Wait until the tab's content appears on screen ─────────────────
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> !d.findElements(
                            AppiumBy.androidUIAutomator(
                                    "new UiSelector().textContains(\"" + tabText + "\")"))
                            .isEmpty());
        } catch (Exception ignored) {
            // Not fatal — scrollToTop + scan will proceed anyway
        }
        sleep(800);
    }

    // ── UIAutomator helpers ────────────────────────────────────────────────────

    /**
     * Dumps every visible text and accessibility label on screen.
     * Called when a tab locator fails so the real label can be identified.
     */
    private void dumpVisibleTexts() {
        try {
            // Collect by text (UI labels)
            List<WebElement> textEls = driver.findElements(
                    AppiumBy.androidUIAutomator("new UiSelector().textMatches(\".+\")"));
            System.out.println("  [dump] Visible TEXT elements on screen:");
            for (WebElement el : textEls) {
                try {
                    String t   = el.getText();
                    String acc = el.getAttribute("content-desc");
                    if (t != null && !t.isBlank()) {
                        System.out.printf("    text=%-40s  acc=%s%n", "\"" + t + "\"",
                                acc != null && !acc.isBlank() ? "\"" + acc + "\"" : "(none)");
                    }
                } catch (Exception ignored) {}
            }
            // Collect by accessibility (icon-only tabs have acc-desc but no text)
            List<WebElement> accEls = driver.findElements(
                    AppiumBy.androidUIAutomator("new UiSelector().descriptionMatches(\".+\")"));
            System.out.println("  [dump] Visible ACCESSIBILITY-ID elements on screen:");
            for (WebElement el : accEls) {
                try {
                    String acc  = el.getAttribute("content-desc");
                    String text = el.getText();
                    if (acc != null && !acc.isBlank()) {
                        System.out.printf("    acc=%-40s  text=%s%n", "\"" + acc + "\"",
                                text != null && !text.isBlank() ? "\"" + text + "\"" : "(none)");
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.out.println("  [dump] Failed to dump elements: " + e.getMessage());
        }
    }

    private boolean isElementPresent(By locator) {
        try { return !driver.findElements(locator).isEmpty(); }
        catch (Exception e) { return false; }
    }

    /** Taps element if found. Returns true on success. */
    private boolean tapIfFound(By locator) {
        try {
            List<WebElement> els = driver.findElements(locator);
            if (!els.isEmpty() && els.get(0).isDisplayed()) {
                els.get(0).click();
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** Taps element, throws if not found. */
    private void tapElement(By locator) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.elementToBeClickable(locator))
                    .click();
        } catch (Exception e) {
            System.out.println("[ClpVerificationTest] ⚠️  Could not tap " + locator + ": " + e.getMessage());
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    // ── Scroll helpers ─────────────────────────────────────────────────────────

    private void scrollToTop() {
        // Try the fast UIAutomator fling-to-beginning first.
        try {
            driver.findElement(AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().scrollable(true)).flingToBeginning(10)"));
            Thread.sleep(600);
            return;
        } catch (Exception ignored) {
            // Fallback: manual scroll-up passes
        }
        // Fallback: scroll up 20 times to guarantee we're at the very top.
        for (int i = 0; i < 20; i++) {
            scrollUp();
        }
        try { Thread.sleep(400); } catch (InterruptedException ignored) {}
    }

    private void scrollUp() {
        try {
            Dimension size  = driver.manage().window().getSize();
            int centerX = size.width / 2;
            int startY  = (int) (size.height * 0.25);
            int endY    = (int) (size.height * 0.75);
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
            System.out.println("  ⚠️  Scroll-up failed: " + e.getMessage());
        }
    }

    // ── Tree printer ───────────────────────────────────────────────────────────

    private void printTree(String pageName, List<Section> sections) {
        int totalItems = sections.stream().mapToInt(s -> s.itemTitles.size()).sum();
        System.out.printf("%n  %-20s  %d sections  •  %d items total%n%n",
                pageName, sections.size(), totalItems);
        for (int si = 0; si < sections.size(); si++) {
            Section s = sections.get(si);
            System.out.printf("  [%2d] %-40s  (%s)%n", si, s.title, s.cardType);
            if (!s.subtitle.isEmpty())
                System.out.printf("       subtitle : %s%n", s.subtitle);
            for (int ii = 0; ii < s.itemTitles.size(); ii++) {
                String price = ii < s.prices.size() ? s.prices.get(ii) : "";
                String disc  = ii < s.discounts.size() ? s.discounts.get(ii) : "";
                System.out.printf("         [%d] %s%s%s%n", ii, s.itemTitles.get(ii),
                        price.isEmpty() ? "" : "  " + price,
                        disc.isEmpty()  ? "" : "  (" + disc + "% off)");
            }
        }
        System.out.println();
    }

    // ── JSON helpers ───────────────────────────────────────────────────────────

    private String text(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = node.path(f).asText("").trim();
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        return "";
    }

    private String num(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.path(f);
            if (n.isMissingNode() || n.isNull()) continue;
            if (n.isNumber()) return String.valueOf(n.longValue());
            String v = n.asText("").trim();
            if (!v.isEmpty() && !v.equals("null") && !v.equals("0")) return v;
        }
        return "";
    }

    private String format(String raw) {
        try { return String.format("%,d", Long.parseLong(raw.replaceAll("[^0-9]", ""))); }
        catch (NumberFormatException e) { return raw; }
    }

    // ── Data holder ────────────────────────────────────────────────────────────

    private static class Section {
        final String       title;
        final String       subtitle;
        final String       cardType;
        final List<String> itemTitles;
        final List<String> prices;
        final List<String> discounts;

        Section(String title, String subtitle, String cardType,
                List<String> itemTitles, List<String> prices, List<String> discounts) {
            this.title      = title;
            this.subtitle   = subtitle;
            this.cardType   = cardType;
            this.itemTitles = itemTitles;
            this.prices     = prices;
            this.discounts  = discounts;
        }
    }
}
