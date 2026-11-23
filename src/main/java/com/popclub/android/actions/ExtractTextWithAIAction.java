package com.popclub.android.actions;

import com.popclub.ai.ClaudeVisionClient;
import com.popclub.android.driver.DriverManager;
import com.popclub.core.TestContext;
import com.popclub.model.Step;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

public class ExtractTextWithAIAction implements Action {

    @Override
    public void perform(Step step) {
        String query = step.value;
        if (query == null || query.isBlank()) {
            throw new RuntimeException("extractTextWithAI requires a 'value' field describing what to extract");
        }

        // Store result under step.variable, or default to "aiOutput"
        String outputVar = (step.variable != null && !step.variable.isBlank())
                ? step.variable : "aiOutput";

        try {
            AppiumDriver driver = DriverManager.getDriver();
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);

            System.out.println("[extractTextWithAI] Extracting: " + query);
            String extracted = ClaudeVisionClient.extractText(png, query);
            System.out.println("[extractTextWithAI] " + outputVar + " = " + extracted);

            TestContext.setScalarData(outputVar, extracted);
        } catch (Exception e) {
            throw new RuntimeException("[extractTextWithAI] " + e.getMessage(), e);
        }
    }
}
