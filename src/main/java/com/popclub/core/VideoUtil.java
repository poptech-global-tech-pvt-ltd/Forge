package com.popclub.core;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidStartScreenRecordingOptions;
import io.appium.java_client.screenrecording.CanRecordScreen;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;

public class VideoUtil {

    /**
     * Max recording length. Android's native screenrecord stops automatically
     * after ~180s by default, which silently truncates (or empties) the video
     * for long E2E tests. UiAutomator2 supports up to 1800s (30 min) — set it
     * high so the recording stays alive for the whole test run.
     */
    private static final Duration MAX_RECORDING = Duration.ofSeconds(1800);

    public static void startRecording(AppiumDriver driver) {
        try {
            ((CanRecordScreen) driver).startRecordingScreen(
                    new AndroidStartScreenRecordingOptions()
                            .withTimeLimit(MAX_RECORDING)
            );
            System.out.println("[VideoUtil] 🎥 Screen recording started (limit "
                    + MAX_RECORDING.toMinutes() + " min).");
        } catch (Exception e) {
            System.out.println("[VideoUtil] ⚠️  Failed to start screen recording: " + e.getMessage());
        }
    }

    public static File stopAndSave(AppiumDriver driver, String name) {

        try {
            String base64 = ((CanRecordScreen) driver).stopRecordingScreen();

            if (base64 == null || base64.isEmpty()) {
                System.out.println("[VideoUtil] ⚠️  No video data returned — recording was not active "
                        + "(never started or already stopped). Skipping save for '" + name + "'.");
                return null;
            }

            byte[] data = Base64.getDecoder().decode(base64);

            String path = "reports/videos/" + name + ".mp4";

            Files.createDirectories(Paths.get("reports/videos"));
            Files.write(Paths.get(path), data);

            System.out.println("[VideoUtil] 💾 Saved video " + path + " (" + data.length + " bytes).");
            return new File(path);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
