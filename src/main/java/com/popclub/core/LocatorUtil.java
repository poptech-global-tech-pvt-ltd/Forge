package com.popclub.core;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

public class LocatorUtil {

    public static By getLocator(Locator locator) {

        switch (locator.type.toLowerCase()) {

            case "accessibilityid":
            case "locator":
                return AppiumBy.accessibilityId(locator.value);

            case "id":
            case "resourceid":
                // Accept short ("submit_btn") or full ("com.pkg:id/submit_btn")
                String resId = locator.value.contains(":id/")
                        ? locator.value
                        : "com.popclub.android:id/" + locator.value;
                return By.id(resId);

            case "xpath":
                return By.xpath(locator.value);

            case "text":
                return By.xpath("//*[contains(@text,'" + locator.value
                        + "') or contains(@content-desc,'" + locator.value + "')]");

            case "uiautomator":
                return AppiumBy.androidUIAutomator(
                        "new UiSelector().textContains(\"" + locator.value + "\")");

            case "point":
                // "x,y" — handled directly in TapAction, not via By
                // Return a dummy By that will be caught by TapAction before WaitUtil is called
                throw new RuntimeException("POINT_LOCATOR:" + locator.value);

            default:
                throw new RuntimeException("Unknown locator type: " + locator.type);
        }
    }
}
