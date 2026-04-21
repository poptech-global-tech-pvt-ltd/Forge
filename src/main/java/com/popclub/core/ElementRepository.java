package com.popclub.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.util.*;

public class ElementRepository {

    private static Map<String, Map<String, Object>> elements = new HashMap<>();

    public static void loadMultiple(Iterable<String> features) {

        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            for (String feature : features) {

                String path = "src/test/resources/elements/" + feature + ".yaml";

                Map<String, Map<String, Object>> featureElements =
                        mapper.readValue(new File(path), Map.class);

                elements.putAll(featureElements);

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
}