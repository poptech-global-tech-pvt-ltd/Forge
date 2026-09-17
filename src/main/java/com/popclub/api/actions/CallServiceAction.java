package com.popclub.api.actions;

import com.popclub.android.actions.Action;
import com.popclub.core.LoggerUtil;
import com.popclub.core.TestContext;
import com.popclub.model.Step;
import io.restassured.response.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * callService — calls any registered REST Assured service impl and stores
 * extracted fields as TestContext variables.
 *
 * Services are registered in {@link ServiceRegistry}. Adding a new service
 * requires only one new {@code case} there — no new action class.
 *
 * YAML syntax:
 * <pre>
 *   - action: callService
 *     service: search.plp        # registered name in ServiceRegistry
 *     params:
 *       query:   "shirt"         # service-specific params
 *       pincode: "560102"
 *       phone:   "1234561122"    # auth — optional, defaults to "1234561122"
 *       otp:     "560102"        # auth — optional, defaults to "560102"
 *     extract:
 *       product_name:  "data.results[0].name"
 *       product_brand: "data.results[0].brand_info.name"
 * </pre>
 */
public class CallServiceAction implements Action {

    @Override
    public void perform(Step step) {
        if (step.service == null || step.service.isBlank())
            throw new RuntimeException("callService: 'service' is required");

        // Resolve ${...} in all param values
        Map<String, String> params = resolveParams(step.params);

        LoggerUtil.step("[callService] " + step.service + "  params=" + params.keySet());

        Response response = ServiceRegistry.call(step.service, params);

        int    status = response.statusCode();
        String body   = response.getBody().asString();

        LoggerUtil.step("[callService] status=" + status + "  length=" + body.length() + " chars");

        if (status < 200 || status >= 300)
            throw new RuntimeException(
                "[callService] HTTP " + status + " from " + step.service
                + "\n" + truncate(body, 400));

        // Diagnostic: show whether key sections are present in the raw JSON body
        boolean hasCoinBurnRules = body.contains("coin_burn_rules");
        boolean hasOfferPrice    = body.contains("offer_price_detail");
        LoggerUtil.step("[callService] body-scan: coin_burn_rules=" + hasCoinBurnRules
                + "  offer_price_detail=" + hasOfferPrice);

        // Extract fields → TestContext
        if (step.extract != null) {
            for (Map.Entry<String, String> entry : step.extract.entrySet()) {
                String varName  = entry.getKey();
                // Trim: YAML `>` folded scalar adds a trailing \n; multiline is folded
                // into spaces. REST Assured's GPath fails on untrimmed expressions.
                // Also interpolate ${varName} so paths like hits[${product_index}] work
                // when the index was captured in an earlier extract or callService step.
                String rawPath  = entry.getValue() != null ? entry.getValue().trim() : "";
                String jsonPath = rawPath.contains("${") ? interpolate(rawPath) : rawPath;
                try {
                    Object raw   = response.jsonPath().get(jsonPath);
                    String value = raw != null ? normalise(String.valueOf(raw)) : "";
                    TestContext.setScalarData(varName, value);
                    if (value.isEmpty()) {
                        LoggerUtil.step("[callService] ⚠ " + varName + " → null/empty  path: " + jsonPath);
                    } else {
                        LoggerUtil.step("[callService] " + varName + " = \"" + value + "\"");
                    }
                } catch (Exception e) {
                    TestContext.setScalarData(varName, "");
                    LoggerUtil.step("[callService] ⚠ " + varName + " extraction failed: " + e.getMessage()
                            + "  path: " + jsonPath);
                }
            }
        }

        // Log the first product's price structure to help debug field names
        try {
            Object priceObj = response.jsonPath().get("data[0].hits[0].offer_price_detail.price");
            LoggerUtil.step("[callService] hits[0].offer_price_detail.price = " + priceObj);
            Object coinBurnObj = response.jsonPath().get("data[0].hits[0].coin_burn_rules");
            LoggerUtil.step("[callService] hits[0].coin_burn_rules = " + coinBurnObj);
        } catch (Exception ignored) {}

        // Log first 2000 chars so response structure is visible in Forge UI
        LoggerUtil.step("[callService] response (first 2000): " + truncate(body, 2000));
    }

    private static Map<String, String> resolveParams(Map<String, String> raw) {
        if (raw == null) return Map.of();
        Map<String, String> out = new HashMap<>();
        raw.forEach((k, v) -> out.put(k, v != null && v.contains("${") ? interpolate(v) : v));
        return out;
    }

    private static String interpolate(String s) {
        if (s == null || !s.contains("${")) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int start = s.indexOf("${", i);
            if (start < 0) { sb.append(s, i, s.length()); break; }
            sb.append(s, i, start);
            int end = s.indexOf("}", start);
            if (end < 0) { sb.append(s, start, s.length()); break; }
            String key = s.substring(start + 2, end);
            String val = TestContext.getScalarData(key);
            sb.append(val != null ? val : "");
            i = end + 1;
        }
        return sb.toString();
    }

    /**
     * Strips a trailing {@code .0} from whole-number floats so API values like
     * {@code "699.0"} are stored as {@code "699"} — matching the UI price format.
     * Non-numeric and already-integer strings are returned unchanged.
     */
    private static String normalise(String s) {
        if (s != null && s.endsWith(".0")) {
            String stripped = s.substring(0, s.length() - 2);
            // Verify the stripped part is a valid integer before committing
            try { Long.parseLong(stripped); return stripped; } catch (NumberFormatException ignored) {}
        }
        return s;
    }

    private static String truncate(String s, int max) {
        return s == null ? "(null)" : s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
