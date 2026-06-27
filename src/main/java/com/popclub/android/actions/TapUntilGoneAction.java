package com.popclub.android.actions;

import com.popclub.driver.DriverFacade;
import com.popclub.model.Step;

/**
 * TapUntilGoneAction — taps an element repeatedly until it disappears.
 * Useful for dismissing stacked toasts, onboarding tooltips, permission dialogs.
 *
 * YAML usage:
 *   - action: tapUntilGone
 *     element: onboarding_tooltip_close
 *     maxTaps: 5
 *
 *   - action: tapUntilGone
 *     text: "Got it"
 *     value: "3"
 */
public class TapUntilGoneAction implements Action {

    private static final int DEFAULT_MAX_TAPS = 5;

    @Override
    public void perform(Step step) {
        DriverFacade facade = DriverFacade.get();
        int maxTaps = step.maxScrolls > 0 ? step.maxScrolls : DEFAULT_MAX_TAPS;
        if (step.value != null && step.value.matches("\\d+")) {
            maxTaps = Integer.parseInt(step.value.trim());
        }

        for (int i = 1; i <= maxTaps; i++) {
            if (!facade.isPresent(step.locators)) {
                System.out.println("[tapUntilGone] Element gone after " + (i-1) + " tap(s)");
                return;
            }
            try { facade.tap(step.locators, 3); } catch (Exception ignored) {}
            System.out.println("[tapUntilGone] Tap " + i + "/" + maxTaps);
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        if (!facade.isPresent(step.locators)) {
            System.out.println("[tapUntilGone] Element gone after " + maxTaps + " tap(s)");
        } else {
            System.out.println("[tapUntilGone] Element still present after " + maxTaps + " taps");
        }
    }
}
