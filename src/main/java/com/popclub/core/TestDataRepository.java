package com.popclub.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TestDataRepository {

    private static final String[] DATA_FILES = {
            "src/test/resources/testdata/data/users.yaml",
            "src/test/resources/testdata/data/products.yaml"
    };

    private static Map<String, Map<String, String>> data = new HashMap<>();
    private static boolean loaded = false;

    public static synchronized void loadIfNeeded() {
        if (loaded) return;

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        for (String path : DATA_FILES) {
            File file = new File(path);
            if (!file.exists()) {
                System.out.println("[TestDataRepository] Skipped missing file: " + path);
                continue;
            }
            try {
                Map<String, Map<String, String>> fileData = mapper.readValue(file, Map.class);
                data.putAll(fileData);
                System.out.println("[TestDataRepository] Loaded: " + path
                        + " (" + fileData.size() + " entries)");
            } catch (Exception e) {
                throw new RuntimeException("Failed to load test data file: " + path, e);
            }
        }

        loaded = true;
    }

    public static Map<String, String> resolveObject(String name) {
        loadIfNeeded();
        Map<String, String> entry = data.get(name);
        if (entry == null) {
            throw new RuntimeException("dataRef object not found in test data files: " + name);
        }
        return entry;
    }

    public static String resolve(String dataRef) {
        int dot = dataRef.indexOf('.');
        if (dot < 0) {
            throw new RuntimeException(
                    "resolve() requires a dotted reference (e.g. \"name.field\"): " + dataRef);
        }

        String objectName = dataRef.substring(0, dot);
        String fieldName  = dataRef.substring(dot + 1);

        Map<String, String> object = resolveObject(objectName);
        String value = object.get(fieldName);
        if (value == null) {
            throw new RuntimeException(
                    "Field \"" + fieldName + "\" not found on \"" + objectName + "\": " + dataRef);
        }
        return value;
    }
}
