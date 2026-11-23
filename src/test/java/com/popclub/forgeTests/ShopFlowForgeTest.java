package com.popclub.forgeTests;

import com.popclub.driver.ForgeDriverClient;
import com.popclub.driver.ForgeDriverManager;
import com.popclub.forgedriver.proto.AccessibilityNode;
import org.testng.Assert;
import org.testng.annotations.*;

import java.util.List;

/**
 * ShopFlowForgeTest — end-to-end shop flow using the ForgeDriver gRPC server.
 *
 * Flow: Home → Shop tab → Search → PLP → PDP → Add to Cart → Cart
 *
 * Prerequisites:
 *   1. forge-driver APK installed on device
 *   2. ForgeDriverManager.start() handles port-forward + am instrument
 *
 * Run:
 *   mvn test -DsuiteXmlFile=src/test/resources/suites/testng-forge-shop.xml
 *   -DdeviceSerial=a207699b
 */
public class ShopFlowForgeTest {

    private static final String DEVICE_SERIAL = System.getProperty("deviceSerial", "a207699b");
    private static final String TEST_PHONE    = "1234561122";
    private static final String TEST_OTP      = "560102";
    private static final String SEARCH_TERM   = "shirt";

    private ForgeDriverClient forge;

    @BeforeClass
    public void setUp() throws Exception {
        forge = ForgeDriverManager.start(DEVICE_SERIAL);
        Assert.assertTrue(forge.isAlive(), "ForgeDriver is not alive on " + DEVICE_SERIAL);
        System.out.println("[Forge] Connected to device: " + DEVICE_SERIAL);

        // Relaunch the app without clearing data (preserves login session)
        Process p = new ProcessBuilder(
            "adb", "-s", DEVICE_SERIAL,
            "shell", "am", "start", "-n", "com.popclub.android/.LauncherFresh"
        ).redirectErrorStream(true).start();
        p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        Thread.sleep(2000);
        System.out.println("[Forge] App relaunched (data preserved)");
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        ForgeDriverManager.stop(DEVICE_SERIAL);
    }

    // ── 1. Login (if needed) ──────────────────────────────────────────────────

    @Test(priority = 1)
    public void ensureLoggedIn() {
        // If login screen is visible, perform login
        boolean loginVisible = forge.isPresent("login_screen_title_text");
        if (!loginVisible) {
            System.out.println("[Forge] Already logged in — skipping login");
            return;
        }

        System.out.println("[Forge] Login screen detected — logging in");

        // Enter phone number
        forge.tap("login_phone_input");
        forge.clearAndType(TEST_PHONE);
        forge.tap("login_continue_button");

        // Wait for OTP screen
        int[] otpField = forge.waitUntilPresent("login_otp_input", 15_000);
        if (otpField == null) {
            otpField = forge.waitUntilPresent("login_merged_otp_entry_content_button", 10_000);
        }
        Assert.assertNotNull(otpField, "OTP screen did not appear after entering phone");

        // Enter OTP
        forge.tap("login_otp_input");
        forge.type(TEST_OTP);
        forge.tap("login_otp_submit_button");

        // Wait for home
        int[] homeTab = forge.waitUntilPresent("common_home_tab", 20_000);
        Assert.assertNotNull(homeTab, "Home tab not visible after login — login may have failed");
        System.out.println("[Forge] ✅ Logged in successfully");
    }

    // ── 2. Navigate to Shop ───────────────────────────────────────────────────

    @Test(priority = 2, dependsOnMethods = "ensureLoggedIn")
    public void navigateToShop() {
        // Press back up to 5 times to clear any overlay/detail screens and reach a tab-bar screen
        for (int i = 0; i < 5; i++) {
            if (forge.isPresent("common_shop_tab")) break;
            forge.pressBack();
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
        }

        int[] shopTab = forge.waitUntilPresent("common_shop_tab", 8_000);
        Assert.assertNotNull(shopTab, "Bottom nav bar not visible — could not reach tabbed navigation");

        forge.tap("common_shop_tab");

        // Wait for shop CLP to load
        int[] searchBtn = forge.waitUntilPresent("toolbar_search_button", 10_000);
        Assert.assertNotNull(searchBtn, "Shop screen did not load — search button not found");
        System.out.println("[Forge] ✅ Navigated to Shop tab");
    }

    // ── 3. Search for a product ───────────────────────────────────────────────

    @Test(priority = 3, dependsOnMethods = "navigateToShop")
    public void searchForProduct() {
        forge.tap("toolbar_search_button");

        int[] searchInput = forge.waitUntilPresent("pop_input_field_search_input", 8_000);
        Assert.assertNotNull(searchInput, "Search input did not appear");

        forge.tap("pop_input_field_search_input");
        forge.type(SEARCH_TERM);
        forge.pressSearch();

        // Pick first category suggestion (drives into PLP)
        int[] suggestion = forge.waitUntilPresent("search_category_suggestion_item_0", 5_000);
        if (suggestion != null) {
            forge.tap("search_category_suggestion_item_0");
        }

        // Verify product list loaded
        int[] firstItem = forge.waitUntilPresent("product_list_item_0", 15_000);
        Assert.assertNotNull(firstItem, "Product list did not load for search: " + SEARCH_TERM);

        // Verify at least one result has a price
        List<AccessibilityNode> prices = forge.search("product_list_item_price_0");
        Assert.assertFalse(prices.isEmpty(), "No price visible on first product");
        String price = prices.get(0).getText();
        Assert.assertFalse(price.isEmpty(), "Price text is empty on PLP item 0");
        System.out.println("[Forge] ✅ PLP loaded — first price: " + price);
    }

    // ── 4. Open product details page ─────────────────────────────────────────

    @Test(priority = 4, dependsOnMethods = "searchForProduct")
    public void openProductDetails() {
        // Capture PLP price for later assertion
        List<AccessibilityNode> plpPrices = forge.search("product_list_item_price_0");
        String plpPrice = plpPrices.isEmpty() ? "" : plpPrices.get(0).getText();

        forge.tap("product_list_item_0");

        int[] pdpTitle = forge.waitUntilPresent("product_details_title", 15_000);
        Assert.assertNotNull(pdpTitle, "Product details page did not load");

        // Verify PDP has a price
        List<AccessibilityNode> pdpPrices = forge.search("pdp_product_price");
        Assert.assertFalse(pdpPrices.isEmpty(), "No price on PDP");
        String pdpPrice = pdpPrices.get(0).getText();

        System.out.println("[Forge] ✅ PDP loaded — PLP price: " + plpPrice + " | PDP price: " + pdpPrice);

        // Both prices should be non-empty (exact match not guaranteed due to discount formatting)
        Assert.assertFalse(pdpPrice.isEmpty(), "PDP price should not be empty");
    }

    // ── 5. Add to cart ────────────────────────────────────────────────────────

    @Test(priority = 5, dependsOnMethods = "openProductDetails")
    public void addToCart() {
        forge.tap("product_details_bottom_sticky_bar_button");

        // Handle size selection sheet (not all products have sizes)
        int[] sizeOption = forge.waitUntilPresent("product_details_size_option_0", 5_000);
        if (sizeOption != null) {
            System.out.println("[Forge] Size selector appeared — selecting first size");
            forge.tap("product_details_size_option_0");
            forge.tap("size_selection_add_to_cart_button");
        } else {
            System.out.println("[Forge] No size selector — direct add");
        }

        // Wait for the add-to-cart confirmation toast
        int[] toast = forge.waitUntilPresent("product_details_add_to_cart_toast", 8_000);
        Assert.assertNotNull(toast, "Add-to-cart toast did not appear — item may not have been added");
        System.out.println("[Forge] ✅ Item added to cart (toast confirmed)");
    }

    // ── 6. Verify cart ────────────────────────────────────────────────────────

    @Test(priority = 6, dependsOnMethods = "addToCart")
    public void verifyCart() {
        // Navigate to cart via toolbar icon
        forge.tap("toolbar_cart_button");

        int[] cartRoot = forge.waitUntilPresent("cart_screen_root", 10_000);
        Assert.assertNotNull(cartRoot, "Cart screen did not load");

        // Verify at least one cart item is present
        int[] cartItem = forge.waitUntilPresent("cart_item_row_0", 8_000);
        Assert.assertNotNull(cartItem, "Cart is empty — expected at least one item");

        // Verify checkout button is present
        int[] checkoutBtn = forge.waitUntilPresent("cart_composables_s2_s_payment_button", 8_000);
        Assert.assertNotNull(checkoutBtn, "Checkout/payment button not found in cart");

        // Verify delivery address is set
        boolean hasAddress = forge.isPresent("cart_delivery_address_card");
        Assert.assertTrue(hasAddress, "No delivery address in cart");

        // Report item price
        List<AccessibilityNode> prices = forge.search("cart_item_final_price_0");
        String finalPrice = prices.isEmpty() ? "unknown" : prices.get(0).getText();
        System.out.println("[Forge] ✅ Cart verified — final price of item 0: " + finalPrice);
        System.out.println("[Forge] ✅ Shop flow complete");
    }
}
