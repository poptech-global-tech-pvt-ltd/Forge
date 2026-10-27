package com.popclub.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.popclub.model.Step;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Loads reusable step sequences from {@code src/test/resources/flows/}.
 *
 * <p>Flow YAML format (just a steps list, no testName required):
 * <pre>
 * steps:
 *   - action: tap
 *     element: add_new_address_btn
 *   - action: enterText
 *     element: address_line1
 *     value: ${streetAddress}
 * </pre>
 *
 * <p>Tests call a flow with:
 * <pre>
 * - action: call
 *   flow: add_address          # → flows/add_address.yaml
 *   params:
 *     streetAddress: "123 Main St"
 * </pre>
 */
public class FlowLoader {

    private static final String FLOWS_DIR =
            "src/test/java/com/popclub/androidFlows/";

    /** Wrapper so the YAML mapper can deserialise the top-level {@code steps:} list. */
    private static class FlowFile {
        public List<Step> steps;
    }

    /**
     * Loads the steps from {@code flows/<name>.yaml}.
     * Throws a clear {@link RuntimeException} if the file is missing or malformed.
     */
    public static List<Step> load(String name) {
        String fileName = name.endsWith(".yaml") ? name : name + ".yaml";
        File file = new File(FLOWS_DIR + fileName);

        if (!file.exists()) {
            throw new RuntimeException(
                "Flow not found: '" + name + "' (looked for " + file.getAbsolutePath() + ").\n" +
                "Create it at src/test/resources/flows/" + fileName);
        }

        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            // A flow file may look like a TestCase (with testName, steps) or just {steps: [...]}
            // Try the FlowFile wrapper first, then fall back to raw step list.
            Map<?, ?> raw = mapper.readValue(file, Map.class);
            if (raw.containsKey("steps")) {
                FlowFile ff = mapper.readValue(file, FlowFile.class);
                if (ff.steps == null || ff.steps.isEmpty())
                    throw new RuntimeException("Flow '" + name + "' has no steps");
                return ff.steps;
            }
            throw new RuntimeException(
                "Flow '" + name + "': expected a top-level 'steps:' list");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load flow '" + name + "': " + e.getMessage(), e);
        }
    }

    /** Returns all .yaml file names (without extension) in the flows directory. */
    public static List<String> listFlows() {
        File dir = new File(FLOWS_DIR);
        if (!dir.exists()) return List.of();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yaml") || n.endsWith(".yml"));
        if (files == null) return List.of();
        return java.util.Arrays.stream(files)
                .map(f -> f.getName().replaceFirst("\\.(yaml|yml)$", ""))
                .sorted()
                .toList();
    }
}
