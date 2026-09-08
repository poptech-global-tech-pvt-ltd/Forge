package com.popclub.core;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidStartScreenRecordingOptions;
import io.appium.java_client.screenrecording.CanRecordScreen;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

public class VideoUtil {

    private static final Duration MAX_RECORDING = Duration.ofSeconds(1800);

    // Tracks whether recording actually started; prevents stopRecordingScreen being called
    // on a driver that never started recording (e.g. Android 16 where screenrecord is blocked).
    private static final ThreadLocal<Boolean> recordingActive = ThreadLocal.withInitial(() -> false);

    public static void startRecording(AppiumDriver driver) {
        recordingActive.set(false);
        // Primary: default Appium recorder (adb screenrecord)
        try {
            ((CanRecordScreen) driver).startRecordingScreen(
                    new AndroidStartScreenRecordingOptions()
                            .withTimeLimit(MAX_RECORDING)
            );
            recordingActive.set(true);
            System.out.println("[VideoUtil] 🎥 Screen recording started (limit "
                    + MAX_RECORDING.toMinutes() + " min).");
            return;
        } catch (Exception primary) {
            System.out.println("[VideoUtil] ⚠️  screenrecord unavailable, trying screenrecordsService: "
                    + primary.getMessage());
        }

        // Fallback: io.appium.settings RecorderService (works on Android 14+ where adb screenrecord
        // is blocked). Requires io.appium.settings app with SYSTEM_ALERT_WINDOW granted.
        try {
            driver.executeScript("mobile: startScreenRecording", Map.of(
                    "videoType", "screenrecordsService",
                    "timeLimit", MAX_RECORDING.getSeconds()
            ));
            recordingActive.set(true);
            System.out.println("[VideoUtil] 🎥 Screen recording started via screenrecordsService.");
        } catch (Exception fallback) {
            System.out.println("[VideoUtil] ⚠️  screenrecordsService also failed — video will not be captured: "
                    + fallback.getMessage());
        }
    }

    public static File stopAndSave(AppiumDriver driver, String name) {
        if (!recordingActive.get()) {
            System.out.println("[VideoUtil] ℹ️  No active recording — skipping video save for '" + name + "'.");
            return null;
        }

        try {
            String base64 = ((CanRecordScreen) driver).stopRecordingScreen();
            recordingActive.set(false);

            if (base64 == null || base64.isEmpty()) {
                System.out.println("[VideoUtil] ⚠️  No video data returned. Skipping save for '" + name + "'.");
                return null;
            }

            byte[] data = Base64.getDecoder().decode(base64);
            String path = "reports/videos/" + name + ".mp4";
            Files.createDirectories(Paths.get("reports/videos"));
            Files.write(Paths.get(path), data);
            System.out.println("[VideoUtil] 💾 Saved video " + path + " (" + data.length + " bytes).");
            return new File(path);

        } catch (Exception e) {
            recordingActive.set(false);
            throw new RuntimeException(e);
        }
    }
}
