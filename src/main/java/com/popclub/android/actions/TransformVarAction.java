package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.model.Step;

/**
 * transformVar — applies a transformation to a stored variable.
 *
 * YAML syntax:
 *   - action: transformVar
 *     variable: ui_cart_total
 *     value: "stripNonNumeric"       # removes everything except digits and dots
 *
 * Supported transforms: stripNonNumeric
 */
public class TransformVarAction implements Action {

    @Override
    public void perform(Step step) {
        String varName = step.variable;
        String transform = step.value;
        if (varName == null || varName.isBlank()) {
            throw new RuntimeException("transformVar: 'variable' is required");
        }

        String current = TestContext.getScalarData(varName);
        if (current == null) current = "";

        String result = switch (transform) {
            case "stripNonNumeric" -> current.replaceAll("[^0-9.]", "");
            default -> throw new RuntimeException("transformVar: unknown transform '" + transform + "'");
        };

        TestContext.setScalarData(varName, result);
        System.out.printf("[transformVar] ${%s}: \"%s\" → \"%s\" (%s)%n", varName, current, result, transform);
    }
}
