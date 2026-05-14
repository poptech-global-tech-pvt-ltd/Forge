package com.popclub.mobile.actions;

import com.popclub.mobile.driver.DriverManager;
import com.popclub.model.Step;
import com.popclub.parser.XmlElementParser;
import io.appium.java_client.AppiumDriver;

import java.util.List;
import java.util.Map;

/**
 * Action: scanTags
 *
 * Dumps all accessibility IDs visible on the current screen to the console.
 * Use this as a step in any test YAML to discover element names on any screen:
 *
 *   - action: scanTags
 *
 * Output appears in the test run log under the step block.
 */
public class ScanTagsAction implements Action {

    @Override
    public void perform(Step step) {

        AppiumDriver driver = DriverManager.getDriver();

        try {
            Thread.sleep(800); // let UI settle before dumping source
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        try {
            String xml = driver.getPageSource();

            List<Map<String, String>> elements = XmlElementParser.parse(xml);

            List<String> testTags   = new java.util.ArrayList<>();
            List<String> otherTags  = new java.util.ArrayList<>();

            for (Map<String, String> el : elements) {
                String tag = el.get("accessibilityId");
                if (tag != null && !tag.isBlank()) {
                    if (tag.matches("[a-z][a-z0-9_]*")) {   // snake_case = test tag
                        testTags.add(tag);
                    } else {
                        otherTags.add(tag);
                    }
                }
            }

            System.out.println("\n========= TEST TAGS (use these in your YAML) =========");
            if (testTags.isEmpty()) {
                System.out.println("  (none found)");
            } else {
                testTags.forEach(t -> System.out.println("  " + t));
            }

            System.out.println("\n--------- Other accessibility labels (skip these) -----");
            otherTags.forEach(t -> System.out.println("  " + t));

            System.out.println("=======================================================\n");

        } catch (Exception e) {
            throw new RuntimeException("scanTags failed: " + e.getMessage(), e);
        }
    }
}
