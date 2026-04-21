package com.popclub.core;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.util.List;

public class WaitUtil {

    public static WebElement waitForElement(AppiumDriver driver, List<Locator> locators) {

        for (Locator locator : locators) {

            try {
                AppiumBy by = (AppiumBy) (AppiumBy) LocatorUtil.getLocator(locator);

                WebElement element = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.visibilityOfElementLocated(by));

                System.out.println("Found using: " + locator.type);

                return element;

            } catch (Exception e) {
                System.out.println(" Failed: " + locator.type);
            }
        }

        throw new RuntimeException("Element not found");
    }
}