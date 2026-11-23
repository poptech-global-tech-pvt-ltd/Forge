package com.popclub.android.actions;

import com.popclub.ai.ClaudeVisionClient;
import com.popclub.android.driver.DriverManager;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

public class AssertWithAIAction implements Action {

    @Override
    public void perform(Step step) {
        String assertion = step.value;
        if (assertion == null || assertion.isBlank()) {
            throw new RuntimeException("assertWithAI requires a 'value' field with the assertion text");
        }

        try {
            AppiumDriver driver = DriverManager.getDriver();
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);

            System.out.println("[assertWithAI] Checking: " + assertion);
            String result = ClaudeVisionClient.assertScreen(png, assertion);
            System.out.println("[assertWithAI] " + result);

            if (!result.startsWith("PASS")) {
                throw new AssertionError("[assertWithAI] " + result);
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[assertWithAI] " + e.getMessage(), e);
        }
    }
}
