package com.popclub.testdata;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

public final class TestDataLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestDataLoader() {}

    public static TestUser loadValidUser() {
        try (InputStream is = TestDataLoader.class.getClassLoader()
                .getResourceAsStream("testdata/card/valid_user_details_card_onboarding.json")) {
            if (is == null) {
                throw new RuntimeException("valid_user_details_card_onboarding.json not found in testdata/card/");
            }
            return MAPPER.readValue(is, TestUser.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load valid_user_details_card_onboarding.json: " + e.getMessage(), e);
        }
    }

    public static InvalidCases loadInvalidCases() {
        try (InputStream is = TestDataLoader.class.getClassLoader()
                .getResourceAsStream("testdata/card/invalid_cases_card_onboarding.json")) {
            if (is == null) {
                throw new RuntimeException("invalid_cases_card_onboarding.json not found in testdata/card/");
            }
            return MAPPER.readValue(is, InvalidCases.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load invalid_cases_card_onboarding.json: " + e.getMessage(), e);
        }
    }
}
