package com.popclub.core;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

public class LocatorUtil {

    public static By getLocator(Locator locator) {

        switch (locator.type.toLowerCase()) {

            case "accessibilityid":
                return AppiumBy.accessibilityId(locator.value);

            case "id":
                return By.id(locator.value);

            case "xpath":
                return By.xpath(locator.value);

            case "text":
                return By.xpath("//*[contains(@text,'" + locator.value + "')]");

            default:
                throw new RuntimeException("Unknown locator type: " + locator.type);
        }
    }
}