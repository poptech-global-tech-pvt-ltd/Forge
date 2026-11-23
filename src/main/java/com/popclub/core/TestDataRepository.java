package com.popclub.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TestDataRepository {

    private static final String DATA_DIR = "src/test/resources/testdata/data/";

    private static final Map<String, Map<String, Map<String, String>>> loadedFiles = new HashMap<>();

    private static synchronized Map<String, Map<String, String>> loadFile(String fileName) {
        Map<String, Map<String, String>> cached = loadedFiles.get(fileName);
        if (cached != null) return cached;

        File file = new File(DATA_DIR + fileName + ".yaml");
        if (!file.exists()) {
            throw new RuntimeException(
                    "Failed to fetch test data — File: " + fileName + ".yaml (not found at " + file.getPath() + ")");
        }
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Map<String, String>> fileData = mapper.readValue(file, Map.class);
            loadedFiles.put(fileName, fileData);
            return fileData;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to fetch test data — File: " + fileName + ".yaml (could not be parsed)", e);
        }
    }

    public static Map<String, String> resolveObject(String dataRef) {
        String[] parts = dataRef.split("\\.");
        if (parts.length != 2) {
            throw new RuntimeException(
                    "Failed to fetch test data — Reference: " + dataRef
                    + " (expected \"file.object\")");
        }
        return getObject(parts[0], parts[1]);
    }

    public static String resolve(String dataRef) {
        String[] parts = dataRef.split("\\.");
        if (parts.length != 3) {
            throw new RuntimeException(
                    "Failed to fetch test data — Reference: " + dataRef
                    + " (expected \"file.object.field\")");
        }

        Map<String, String> object = getObject(parts[0], parts[1]);
        String value = object.get(parts[2]);
        if (value == null) {
            throw new RuntimeException(
                    "Failed to fetch test data — Key: " + parts[2] + ", Reference: " + parts[0] + "." + parts[1]);
        }
        return value;
    }

    private static Map<String, String> getObject(String fileName, String objectName) {
        Map<String, Map<String, String>> fileData = loadFile(fileName);
        Map<String, String> entry = fileData.get(objectName);
        if (entry == null) {
            throw new RuntimeException(
                    "Failed to fetch test data — Object: " + objectName + ", File: " + fileName + ".yaml"
                    + " (object not found)");
        }
        return entry;
    }
}
