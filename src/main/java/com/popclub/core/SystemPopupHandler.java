package com.popclub.core;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;

public class SystemPopupHandler {

    public static void handle(AppiumDriver driver) {

//        try {
//            driver.findElement(AppiumBy.id("com.google.android.gms:id/cancel")).click();
//            System.out.println("Closed Google popup");
//        } catch (Exception ignored) {}

        if (driver == null) return;

        System.out.println("🔍 Waiting for Google popup...");

        for (int i = 0; i < 10; i++) { // retry for ~5 sec

            try {
                if (driver.findElements(
                        AppiumBy.id("com.google.android.gms:id/cancel")
                ).size() > 0) {

                    driver.findElement(
                            AppiumBy.id("com.google.android.gms:id/cancel")
                    ).click();

                    System.out.println("✅ Google popup closed");
                    return;
                }

            } catch (Exception ignored) {}

            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
        }

        System.out.println("⚠️ Google popup not found");

//        try {
//            driver.findElement(AppiumBy.xpath("//*[@text='Allow']")).click();
//            System.out.println("Accepted notification popup");
//        } catch (Exception ignored) {}
    }
}