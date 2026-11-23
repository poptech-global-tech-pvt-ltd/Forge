package com.popclub.android.actions;

import com.popclub.core.TestContext;
import com.popclub.model.Step;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class EvalScriptAction implements Action {

    @Override
    public void perform(Step step) {
        String script = step.value;
        if (script == null || script.isBlank()) {
            throw new RuntimeException("evalScript requires a 'value' field containing the JavaScript to run");
        }

        // Strip Maestro-style ${...} wrapper if present
        if (script.startsWith("${") && script.endsWith("}")) {
            script = script.substring(2, script.length() - 1);
        }

        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1); // interpreted mode — no JVM bytecode generation

            Scriptable scope = cx.initStandardObjects();

            // output.* — scripts set output.foo = "bar"; stored in TestContext as "output.foo"
            NativeObject outputObj = new NativeObject();
            // Pre-populate with any previously stored output.* values
            TestContext.getAllScalarData().forEach((k, v) -> {
                if (k.startsWith("output.")) {
                    String key = k.substring("output.".length());
                    ScriptableObject.putProperty(outputObj, key, v);
                }
            });
            ScriptableObject.putProperty(scope, "output", outputObj);

            // env.* — read-only view of all TestContext scalars
            NativeObject envObj = new NativeObject();
            TestContext.getAllScalarData().forEach((k, v) ->
                ScriptableObject.putProperty(envObj, k, v));
            ScriptableObject.putProperty(scope, "env", envObj);

            cx.evaluateString(scope, script, "evalScript", 1, null);

            // Persist output.* back to TestContext
            for (Object id : outputObj.getIds()) {
                String key = id.toString();
                Object val = NativeObject.getProperty(outputObj, key);
                if (val != null && !(val instanceof org.mozilla.javascript.Undefined)) {
                    String strVal = Context.toString(val);
                    TestContext.setScalarData("output." + key, strVal);
                    System.out.println("[evalScript] output." + key + " = " + strVal);
                }
            }
        } finally {
            Context.exit();
        }
    }
}
