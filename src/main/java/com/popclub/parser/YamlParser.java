package com.popclub.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.popclub.model.TestCase;

import java.io.File;

public class YamlParser {

    public static TestCase parse(String path) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(new File(path), TestCase.class);
        } catch (Exception e) {
            throw new RuntimeException("YAML parsing failed", e);
        }
    }
}