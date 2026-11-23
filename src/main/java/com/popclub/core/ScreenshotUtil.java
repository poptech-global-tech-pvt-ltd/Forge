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
            Path dirPath = Paths.get(SCREENSHOT_DIR);
            if (!Files.exists(dirPath)) Files.createDirectories(dirPath);

            String fileName = testName + "_" + System.currentTimeMillis() + ".png";
            Path destPath = Paths.get(SCREENSHOT_DIR + fileName);

            if (com.popclub.driver.ForgeDriverContext.isActive()) {
                byte[] png = com.popclub.driver.ForgeDriverContext.get().screenshot();
                Files.write(destPath, png);
                return destPath.toFile();
            }

            AppiumDriver driver = DriverManager.getDriver();

            // Capture screenshot
            File src = ((TakesScreenshot) driver)
                    .getScreenshotAs(OutputType.FILE);

            // Use Files.copy (more reliable than renameTo)
            Files.copy(src.toPath(), destPath);

            return destPath.toFile();

        } catch (Exception e) {
            System.out.println(" Screenshot failed: " + e.getMessage());
            return null;
        }
    }
}