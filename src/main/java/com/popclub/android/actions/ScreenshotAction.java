package com.popclub.android.actions;

import com.popclub.core.ScreenshotUtil;
import com.popclub.model.Step;

/**
 * ScreenshotAction — captures a named screenshot mid-test.
 *
 * YAML usage:
 *   - action: screenshot
 *     value: "after_add_to_cart"
 */
public class ScreenshotAction implements Action {
    @Override
    public void perform(Step step) {
        String name = step.value != null && !step.value.isBlank()
                ? step.value.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_")
                : "checkpoint_" + System.currentTimeMillis();
        ScreenshotUtil.capture(name);
        System.out.println("[screenshot] Captured: " + name);
    }
}
