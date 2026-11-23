package com.popclub.android.actions;

import com.popclub.ai.ClaudeVisionClient;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

public class AssertNoDefectsWithAIAction implements Action {

    @Override
    public void perform(Step step) {
        try {
            AppiumDriver driver = DriverManager.getDriver();
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);

            System.out.println("[assertNoDefectsWithAI] Scanning screen for defects...");
            String result = ClaudeVisionClient.findDefects(png);
            System.out.println("[assertNoDefectsWithAI] " + result);

            if (!result.startsWith("PASS")) {
                throw new AssertionError("[assertNoDefectsWithAI] " + result);
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[assertNoDefectsWithAI] " + e.getMessage(), e);
        }
    }
}
