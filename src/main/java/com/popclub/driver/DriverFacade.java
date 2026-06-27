package com.popclub.driver;

import com.popclub.android.driver.DriverManager;
import com.popclub.core.Locator;
import com.popclub.core.LocatorUtil;
import com.popclub.core.WaitUtil;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * DriverFacade — unified driver interface for Forge actions.
 *
 * Dual-mode:
 *   - ForgeDriver APK present → uses ForgeDriverClient (direct UiAutomator2, fast)
 *   - No APK / fallback       → uses AppiumDriver (existing behaviour, nothing breaks)
 *
 * Actions call DriverFacade instead of AppiumDriver directly.
 * No action rewrites needed — just replace DriverManager.getDriver() calls
 * with DriverFacade.get().waitForElement() etc.
 *
 * Speed comparison per tap:
 *   Appium:      ~200ms (Java → Appium HTTP → UiAutomator2)
 *   ForgeDriver:  ~20ms (Java → adb forward → UiAutomator2)
 */
public class DriverFacade {

    private static DriverFacade instance;
    private final boolean useForgeDriver;
    private final ForgeDriverClient forgeClient;
    private final AppiumDriver appiumDriver;

    private DriverFacade(AppiumDriver appiumDriver) {
        this.appiumDriver = appiumDriver;
        ForgeDriverClient client = new ForgeDriverClient();
        if (client.isAlive()) {
            this.useForgeDriver = true;
            this.forgeClient = client;
            System.out.println("[DriverFacade] ✅ ForgeDriver APK detected — using direct UiAutomator2");
        } else {
            this.useForgeDriver = false;
            this.forgeClient = null;
            System.out.println("[DriverFacade] Appium mode (ForgeDriver APK not running)");
        }
    }

    public static DriverFacade get() {
        if (instance == null) {
            instance = new DriverFacade(DriverManager.getDriver());
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }

    public boolean isForgeDriverMode() {
        return useForgeDriver;
    }

    // ── Wait / find ───────────────────────────────────────────────────────────

    /**
     * Wait until element is visible, return it.
     * ForgeDriver: single HTTP call with timeout (no Java-side polling)
     * Appium:      WaitUtil polling loop
     */
    public WebElement waitForElement(List<Locator> locators, int timeoutSeconds) {
        if (useForgeDriver) {
            return waitForgeDriver(locators, timeoutSeconds);
        }
        return WaitUtil.pollUntilVisible(appiumDriver, locators, timeoutSeconds);
    }

    public WebElement waitForElement(List<Locator> locators) {
        return waitForElement(locators, 30);
    }

    public boolean isPresent(List<Locator> locators) {
        if (useForgeDriver) {
            for (Locator loc : locators) {
                String tag = primaryTag(loc);
                if (tag != null && forgeClient.isPresent(tag)) return true;
            }
            return false;
        }
        return WaitUtil.findElementQuick(appiumDriver, locators) != null;
    }

    // ── Tap ───────────────────────────────────────────────────────────────────

    public void tap(List<Locator> locators, int timeoutSeconds) {
        if (useForgeDriver) {
            for (Locator loc : locators) {
                String tag = primaryTag(loc);
                if (tag != null) {
                    try {
                        Map<String, Integer> bounds = forgeClient.waitUntilPresent(tag, timeoutSeconds * 1000L);
                        if (bounds != null) {
                            forgeClient.tap(tag);
                            return;
                        }
                    } catch (Exception e) {
                        System.out.println("[DriverFacade] ForgeDriver tap failed for " + tag + ": " + e.getMessage());
                    }
                }
            }
            throw new RuntimeException("Element not found for tap: " + locators);
        }
        WaitUtil.pollUntilVisible(appiumDriver, locators, timeoutSeconds).click();
    }

    public void tapByCoords(int x, int y) {
        if (useForgeDriver) {
            try {
                forgeClient.tapByCoords(x, y);
                return;
            } catch (Exception e) {
                System.out.println("[DriverFacade] ForgeDriver tapByCoords failed: " + e.getMessage());
            }
        }
        tapByCoordinatesAppium(x, y);
    }

    public void tapByText(String text) {
        if (useForgeDriver) {
            try {
                forgeClient.tapByText(text);
                return;
            } catch (Exception e) {
                System.out.println("[DriverFacade] ForgeDriver tapByText failed: " + e.getMessage());
            }
        }
        // Appium fallback
        List<WebElement> els = appiumDriver.findElements(By.xpath("//*[@text='" + text + "']"));
        if (!els.isEmpty()) els.get(0).click();
        else throw new RuntimeException("Element not found with text: " + text);
    }

    // ── Type ─────────────────────────────────────────────────────────────────

    public void type(String text) {
        if (useForgeDriver) {
            try { forgeClient.type(text); return; }
            catch (Exception e) { System.out.println("[DriverFacade] ForgeDriver type failed: " + e.getMessage()); }
        }
        // Appium 10: getKeyboard() removed — find focused EditText and sendKeys
        try {
            org.openqa.selenium.WebElement focused = appiumDriver.findElement(
                io.appium.java_client.AppiumBy.xpath("//android.widget.EditText[@focused='true']"));
            focused.sendKeys(text);
        } catch (Exception e) {
            appiumDriver.findElement(io.appium.java_client.AppiumBy.xpath(
                "//android.widget.EditText")).sendKeys(text);
        }
    }

    public void clearAndType(List<Locator> locators, String text, int timeoutSeconds) {
        if (useForgeDriver) {
            try {
                // Tap the field first, then clear+type
                tap(locators, timeoutSeconds);
                forgeClient.clearAndType(text);
                return;
            } catch (Exception e) {
                System.out.println("[DriverFacade] ForgeDriver clearAndType failed: " + e.getMessage());
            }
        }
        WebElement el = WaitUtil.pollUntilVisible(appiumDriver, locators, timeoutSeconds);
        el.clear();
        el.sendKeys(text);
    }

    // ── Swipe ─────────────────────────────────────────────────────────────────

    public void swipe(String direction) {
        if (useForgeDriver) {
            try { forgeClient.swipe(direction); return; }
            catch (Exception e) { System.out.println("[DriverFacade] ForgeDriver swipe failed: " + e.getMessage()); }
        }
        swipeAppium(direction);
    }

    // ── Keys ─────────────────────────────────────────────────────────────────

    public void pressKey(String key) {
        if (useForgeDriver) {
            try {
                switch (key.toLowerCase()) {
                    case "back"   -> forgeClient.pressBack();
                    case "home"   -> forgeClient.pressHome();
                    case "enter"  -> forgeClient.pressEnter();
                    case "search" -> forgeClient.pressSearch();
                    case "delete" -> forgeClient.pressDelete();
                    default       -> throw new RuntimeException("Unknown key: " + key);
                }
                return;
            } catch (Exception e) {
                System.out.println("[DriverFacade] ForgeDriver pressKey failed: " + e.getMessage());
            }
        }
        pressKeyAppium(key);
    }

    // ── Page source (for WaitUtil batch detection) ────────────────────────────

    public String getPageSource() {
        if (useForgeDriver) {
            try { return forgeClient.getSource(); }
            catch (Exception e) { /* fall through */ }
        }
        return appiumDriver.getPageSource();
    }

    // ── Raw Appium driver (for actions not yet migrated) ──────────────────────

    public AppiumDriver appium() {
        return appiumDriver;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private WebElement waitForgeDriver(List<Locator> locators, int timeoutSeconds) {
        for (Locator loc : locators) {
            String tag = primaryTag(loc);
            if (tag != null) {
                Map<String, Integer> bounds = forgeClient.waitUntilPresent(tag, timeoutSeconds * 1000L);
                if (bounds != null) {
                    // Return a thin WebElement proxy backed by Appium for compatibility
                    // (some actions need .getText(), .getAttribute() etc.)
                    try {
                        By by = LocatorUtil.getLocator(loc);
                        List<WebElement> found = appiumDriver.findElements(by);
                        if (!found.isEmpty()) return found.get(0);
                    } catch (Exception ignored) {}
                }
            }
        }
        throw new RuntimeException("Element not found: " + locators);
    }

    /** Extract the primary tag value from a locator (for ForgeDriver tag-based lookup) */
    private String primaryTag(Locator loc) {
        if (loc == null || loc.value == null) return null;
        String type = loc.type == null ? "" : loc.type.toLowerCase();
        return switch (type) {
            case "accessibilityid", "id", "text", "uiautomator" -> loc.value;
            default -> null;
        };
    }

    private void tapByCoordinatesAppium(int x, int y) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        appiumDriver.perform(List.of(tap));
    }

    private void swipeAppium(String direction) {
        int w = appiumDriver.manage().window().getSize().width;
        int h = appiumDriver.manage().window().getSize().height;
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        int[] from = switch (direction.toLowerCase()) {
            case "up"    -> new int[]{w/2, h*3/4};
            case "down"  -> new int[]{w/2, h/4};
            case "left"  -> new int[]{w*3/4, h/2};
            case "right" -> new int[]{w/4, h/2};
            default      -> new int[]{w/2, h/2};
        };
        int[] to = switch (direction.toLowerCase()) {
            case "up"    -> new int[]{w/2, h/4};
            case "down"  -> new int[]{w/2, h*3/4};
            case "left"  -> new int[]{w/4, h/2};
            case "right" -> new int[]{w*3/4, h/2};
            default      -> new int[]{w/2, h/2};
        };
        Sequence swipe = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), from[0], from[1]))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerMove(Duration.ofMillis(600), PointerInput.Origin.viewport(), to[0], to[1]))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        appiumDriver.perform(List.of(swipe));
    }

    private void pressKeyAppium(String key) {
        switch (key.toLowerCase()) {
            case "back"   -> appiumDriver.navigate().back();
            case "home"   -> ((io.appium.java_client.android.AndroidDriver) appiumDriver)
                                .pressKey(new io.appium.java_client.android.nativekey.KeyEvent(
                                    io.appium.java_client.android.nativekey.AndroidKey.HOME));
            case "enter"  -> ((io.appium.java_client.android.AndroidDriver) appiumDriver)
                                .pressKey(new io.appium.java_client.android.nativekey.KeyEvent(
                                    io.appium.java_client.android.nativekey.AndroidKey.ENTER));
            case "search" -> ((io.appium.java_client.android.AndroidDriver) appiumDriver)
                                .pressKey(new io.appium.java_client.android.nativekey.KeyEvent(
                                    io.appium.java_client.android.nativekey.AndroidKey.SEARCH));
        }
    }
}
