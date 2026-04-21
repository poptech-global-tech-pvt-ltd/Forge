package com.popclub.core;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.screenrecording.CanRecordScreen;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class VideoUtil {

    public static void startRecording(AppiumDriver driver) {
        ((CanRecordScreen) driver).startRecordingScreen();
    }

    public static File stopAndSave(AppiumDriver driver, String name) {

        try {
            String base64 = ((CanRecordScreen) driver).stopRecordingScreen();

            byte[] data = Base64.getDecoder().decode(base64);

            String path = "reports/videos/" + name + ".mp4";

            Files.createDirectories(Paths.get("reports/videos"));
            Files.write(Paths.get(path), data);

            return new File(path);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}