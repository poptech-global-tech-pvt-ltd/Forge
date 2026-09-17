package com.popclub.web.utils;

import com.microsoft.playwright.Page;
import com.popclub.web.factory.PlaywrightFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScreenshotUtil {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotUtil.class);

    private static final String SCREENSHOT_DIR = "reports/screenshots/";

    public static String capture(String testName) {
        Page page = PlaywrightFactory.getPage();
        if (page == null) return null;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String path = SCREENSHOT_DIR + testName + "_" + timestamp + ".png";
        log.info("[ScreenshotUtil] Capturing screenshot for '{}' → {}", testName, path);
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get(path))
                .setFullPage(true));
        log.info("[ScreenshotUtil] Screenshot saved: {}", path);
        return path;
    }
}
