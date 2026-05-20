package com.popclub.mobile.actions;

import com.popclub.ai.app.QaTagAnalyzer;
import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import com.popclub.parser.XmlElementParser;
import io.appium.java_client.AppiumDriver;

import java.util.List;
import java.util.Map;

/**
 * Action: scanTags
 *
 * Analyses QA test tags on the current screen: reports present tags, missing tags,
 * and naming-convention violations. Writes per-screen YAML to reports/qa-app-scan/.
 *
 * Usage in test YAML:
 *   - action: scanTags
 *     value: MyScreenName   # optional; inferred from activity if omitted
 *
 * Output appears in the test run log under the step block.
 */
public class ScanTagsAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        try {
            Thread.sleep(800);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        try {
            String xml = driver.getPageSource();

            List<Map<String, String>> elements = XmlElementParser.parse(xml);

            String screenName = resolveScreenName(step, driver);

            QaTagAnalyzer.ScreenReport report = QaTagAnalyzer.analyse(elements, screenName);
            QaTagAnalyzer.printReport(report);
            QaTagAnalyzer.writeYaml(report, "reports");

        } catch (Exception e) {
            throw new RuntimeException("scanTags failed: " + e.getMessage(), e);
        }
    }

    private String resolveScreenName(Step step, AppiumDriver driver) {
        if (step.value != null && !step.value.isBlank()) {
            return step.value.trim();
        }
        try {
            String activity = ((io.appium.java_client.android.AndroidDriver) driver).currentActivity();
            String[] parts = activity.split("\\.");
            return parts[parts.length - 1];
        } catch (Exception ignored) {
            return "unknown_screen";
        }
    }
}
