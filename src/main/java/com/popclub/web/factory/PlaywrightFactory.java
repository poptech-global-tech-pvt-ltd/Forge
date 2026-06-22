package com.popclub.web.factory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Geolocation;
import com.popclub.web.utils.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

public class PlaywrightFactory {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightFactory.class);

    private static final ThreadLocal<Playwright> playwright = new ThreadLocal<>();
    private static final ThreadLocal<Browser> browser       = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> context = new ThreadLocal<>();
    private static final ThreadLocal<Page> page             = new ThreadLocal<>();

    public static Page initBrowser() {
        log.info("[PlaywrightFactory] Initializing browser: {} headless={}", ConfigReader.get("browser"), ConfigReader.getBoolean("headless"));
        Playwright pw = Playwright.create();
        playwright.set(pw);
        patchMissingPackageJson(); // workaround for Playwright Java 1.52.0 extraction bug

        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(ConfigReader.getBoolean("headless"));

        Browser b = switch (ConfigReader.get("browser").toLowerCase()) {
            case "firefox" -> pw.firefox().launch(options);
            case "webkit"  -> pw.webkit().launch(options);
            default        -> pw.chromium().launch(options);
        };
        browser.set(b);

        BrowserContext ctx = b.newContext(new Browser.NewContextOptions()
                .setPermissions(java.util.List.of("geolocation", "camera", "microphone"))
                .setGeolocation(new Geolocation(12.9716, 77.5946)));
        context.set(ctx);

        Page p = ctx.newPage();
        p.setDefaultTimeout(ConfigReader.getInt("timeout"));
        page.set(p);
        log.info("[PlaywrightFactory] Browser initialized successfully");
        return p;
    }

    public static Page getPage() {
        return page.get();
    }

    public static void closeBrowser() {
        log.info("[PlaywrightFactory] Closing browser");
        try {
            if (page.get() != null)       page.get().close();
            patchMissingPackageJson(); // ensure package.json exists before context.close() triggers HarTracer
            if (context.get() != null)    context.get().close();
            if (browser.get() != null)    browser.get().close();
            if (playwright.get() != null) playwright.get().close();
        } finally {
            page.remove();
            context.remove();
            browser.remove();
            playwright.remove();
        }
    }

    /**
     * Playwright Java 1.52.0 does not extract package.json during driver extraction.
     * HarTracer.stop() requires it when closing a browser context — without it Node.js crashes.
     * Writes a minimal hardcoded package.json into every playwright-java-* temp dir.
     * Called both at initBrowser() and right before context.close() in closeBrowser().
     */
    private static void patchMissingPackageJson() {
        String playwrightVersion = resolvePlaywrightVersion();
        String json = "{\"name\":\"playwright\",\"version\":\"" + playwrightVersion + "\"}";
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));

        try (Stream<Path> dirs = Files.list(tmpDir)) {
            dirs.filter(p -> p.getFileName().toString().startsWith("playwright-java-"))
                .forEach(dir -> {
                    Path dest = dir.resolve("package").resolve("package.json");
                    try {
                        Files.createDirectories(dest.getParent());
                        Files.writeString(dest, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        log.debug("[PlaywrightFactory] Patched package.json ({}) → {}", playwrightVersion, dest);
                    } catch (IOException e) {
                        log.error("[PlaywrightFactory] Failed to patch package.json in {}", dir, e);
                    }
                });
        } catch (IOException e) {
            log.error("[PlaywrightFactory] Failed to scan temp dir for patch", e);
        }
    }

    private static String resolvePlaywrightVersion() {
        try {
            String v = Playwright.class.getPackage().getImplementationVersion();
            if (v != null && !v.isBlank()) return v;
        } catch (Exception ignored) {}
        return "1.52.0";
    }

}
