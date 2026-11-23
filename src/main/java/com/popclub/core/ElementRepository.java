package com.popclub.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ElementRepository {

    private static Map<String, Map<String, Object>> elements    = new HashMap<>();
    /** Tracks which elements YAML file each element key was loaded from. */
    private static Map<String, String>               elementFile = new HashMap<>();

    public static void loadMultiple(Iterable<String> features) {

        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            for (String feature : features) {

                String path = "src/test/resources/testdata/elements/" + feature + ".yaml";

                Map<String, Map<String, Object>> featureElements =
                        mapper.readValue(new File(path), Map.class);

                elements.putAll(featureElements);

                // Track source file for each element key
                for (String key : featureElements.keySet()) {
                    elementFile.put(key, path);
                }

                System.out.println("Loaded elements: " + feature);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load element files", e);
        }
    }

    public static List<Locator> getLocators(String element, String platform) {

        if (!elements.containsKey(element)) {
            throw new RuntimeException("Element not found: " + element);
        }

        Object raw = elements.get(element).get(platform);

        List<Map<String, String>> locatorMaps = (List<Map<String, String>>) raw;

        List<Locator> locators = new ArrayList<>();

        for (Map<String, String> map : locatorMaps) {
            Locator locator = new Locator();
            locator.type = map.get("type");
            locator.value = map.get("value");
            locators.add(locator);
        }

        return locators;
    }

    /**
     * Returns the element key whose first accessibilityId locator matches the given tag value.
     * Used by SelfHealingEngine to find whether a healed tag already has a named element key,
     * so the step's element reference can be updated instead of just the locator value.
     *
     * @param tagValue  e.g. "shop_search_button"
     * @param platform  "android" or "ios"
     * @return element key (e.g. "shop_search_button") or null if not found
     */
    public static String findElementKeyByTag(String tagValue, String platform) {
        for (Map.Entry<String, Map<String, Object>> entry : elements.entrySet()) {
            Object raw = entry.getValue().get(platform);
            if (raw == null) continue;
            List<Map<String, String>> locatorMaps = (List<Map<String, String>>) raw;
            for (Map<String, String> loc : locatorMaps) {
                if ("accessibilityId".equals(loc.get("type"))
                        && tagValue.equals(loc.get("value"))) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * Self-healing: updates the primary accessibilityId locator for an element
     * both in-memory (instant effect) and on disk (persists across runs).
     *
     * The healed value is written as the first locator so it takes priority.
     * The original locator is kept as a fallback comment line in YAML.
     *
     * @param elementKey   the element key (e.g. "shop_search_icon")
     * @param platform     "android" or "ios"
     * @param newTagValue  the correct accessibilityId found on screen
     */
    public static void updateLocator(String elementKey, String platform, String newTagValue) {

        // ── 1. Update in-memory map ──────────────────────────────────────────
        Map<String, Object> entry = elements.get(elementKey);
        if (entry == null) {
            entry = new HashMap<>();
            elements.put(elementKey, entry);
        }

        List<Map<String, String>> locators = new ArrayList<>();
        Map<String, String> primary = new LinkedHashMap<>();
        primary.put("type",  "accessibilityId");
        primary.put("value", newTagValue);
        locators.add(primary);

        entry.put(platform, locators);
        System.out.println("[ElementRepository] In-memory updated: "
                + elementKey + " → " + newTagValue);

        // ── 2. Write back to YAML file ───────────────────────────────────────
        String filePath = elementFile.get(elementKey);
        if (filePath == null) {
            System.out.println("[ElementRepository] ⚠️  Source file unknown for \""
                    + elementKey + "\" — skipping disk write.");
            return;
        }

        try {
            Path path  = Paths.get(filePath);
            String yaml = new String(Files.readAllBytes(path));

            // Find the block for this element and replace its first locator value.
            // Pattern: lines starting with "  - type: accessibilityId" immediately
            // after the platform key under elementKey.
            String patched = patchYaml(yaml, elementKey, platform, newTagValue);

            Files.write(path, patched.getBytes());
            System.out.println("[ElementRepository] ✅ YAML patched: " + filePath
                    + "  [" + elementKey + " → " + newTagValue + "]");

        } catch (Exception e) {
            System.out.println("[ElementRepository] ⚠️  YAML write-back failed: "
                    + e.getMessage());
        }
    }

    /**
     * Minimal YAML patcher — replaces the accessibilityId value under a given
     * element key without using a full YAML serializer (preserves comments and
     * formatting in the rest of the file).
     *
     * Looks for the pattern:
     *   elementKey:\n
     *     android:\n          ← or ios:
     *       - type: accessibilityId\n
     *         value: OLD_VALUE   ← replaced with newValue
     */
    private static String patchYaml(String yaml, String key, String platform, String newValue) {
        String[] lines  = yaml.split("\n", -1);
        boolean  inKey  = false;
        boolean  inPlat = false;
        boolean  inType = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.startsWith(key + ":")) {
                inKey  = true;
                inPlat = false;
                inType = false;
                continue;
            }
            if (inKey && line.trim().startsWith(platform + ":")) {
                inPlat = true;
                inType = false;
                continue;
            }
            if (inKey && inPlat && line.trim().equals("- type: accessibilityId")) {
                inType = true;
                continue;
            }
            if (inKey && inPlat && inType && line.trim().startsWith("value:")) {
                String indent   = line.substring(0, line.indexOf("value:"));
                String oldValue = line.trim().substring("value:".length()).trim();
                lines[i]        = indent + "value: " + newValue
                        + "  # healed from: " + oldValue;
                break;
            }
            // Reset state when we hit a new top-level key
            if (inKey && !line.isEmpty() && !line.startsWith(" ") && !line.startsWith("#")) {
                if (!line.startsWith(key + ":")) {
                    inKey = inPlat = inType = false;
                }
            }
        }

        return String.join("\n", lines);
    }
}