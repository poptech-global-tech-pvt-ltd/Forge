package com.popclub.core;

import com.popclub.android.driver.DriverManager;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ScreenshotUtil {

    private static final String SCREENSHOT_DIR = "reports/screenshots/";

    public static File capture(String testName) {

        try {
            AppiumDriver driver = DriverManager.getDriver();

            // Ensure directory exists
            Path dirPath = Paths.get(SCREENSHOT_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // Capture screenshot
            File src = ((TakesScreenshot) driver)
                    .getScreenshotAs(OutputType.FILE);

            String fileName = testName + "_" + System.currentTimeMillis() + ".png";

            Path destPath = Paths.get(SCREENSHOT_DIR + fileName);

            // Use Files.copy (more reliable than renameTo)
            Files.copy(src.toPath(), destPath);

            return destPath.toFile();

        } catch (Exception e) {
            System.out.println(" Screenshot failed: " + e.getMessage());
            return null;
        }
    }
}