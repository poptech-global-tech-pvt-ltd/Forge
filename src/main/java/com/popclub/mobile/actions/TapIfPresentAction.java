package com.popclub.mobile.actions;

import com.popclub.core.Locator;
import com.popclub.core.LocatorUtil;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class TapIfPresentAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        for (Locator locator : step.locators) {
            try {
                WebElement element = new WebDriverWait(driver, Duration.ofSeconds(3))
                        .until(ExpectedConditions.visibilityOfElementLocated(
                                LocatorUtil.getLocator(locator)));
                element.click();
                System.out.println("Dialog found and tapped: " + locator.value);
                return;
            } catch (Exception ignored) {
                // Not found with this locator — try next
            }
        }

        System.out.println("No dialog found — skipping");
    }
}
