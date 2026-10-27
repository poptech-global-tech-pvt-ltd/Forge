package com.popclub.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class PhoneNumberManager {

    private static final Logger       log        = LoggerFactory.getLogger(PhoneNumberManager.class);
    private static final File         STATE_FILE = new File("ai/web/phone-state.json");
    private static final ObjectMapper MAPPER     = new ObjectMapper();

    public enum PhoneState { FIRST_TIME_FOUND, ONBOARDING_STARTED, EKYC_COMPLETED, NOT_FIRST_TIME }

    @SuppressWarnings("unchecked")
    private static Map<String, String> loadState() {
        if (!STATE_FILE.exists()) return new LinkedHashMap<>();
        try { return MAPPER.readValue(STATE_FILE, LinkedHashMap.class); }
        catch (IOException e) { log.warn("[PhoneManager] Cannot read state — starting fresh", e); return new LinkedHashMap<>(); }
    }

    private static void saveState(Map<String, String> state) {
        try { STATE_FILE.getParentFile().mkdirs(); MAPPER.writerWithDefaultPrettyPrinter().writeValue(STATE_FILE, state); }
        catch (IOException e) { log.error("[PhoneManager] Failed to persist state", e); }
    }

    public static String nextUntried() {
        Map<String, String> state = loadState();
        for (long n = ApiConstants.PHONE_BASE; n <= ApiConstants.PHONE_MAX; n++) {
            String phone = ApiConstants.PHONE_PREFIX + n;
            if (!state.containsKey(phone)) { log.info("[PhoneManager] Next untried: {}", phone); return phone; }
        }
        log.warn("[PhoneManager] All numbers exhausted"); return null;
    }

    public static String nextFirstTimeFound() {
        return loadState().entrySet().stream()
                .filter(e -> PhoneState.FIRST_TIME_FOUND.name().equalsIgnoreCase(e.getValue()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public static String nextOnboardingStarted() {
        return loadState().entrySet().stream()
                .filter(e -> PhoneState.ONBOARDING_STARTED.name().equalsIgnoreCase(e.getValue()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public static void markFirstTimeFound(String phone)    { update(phone, PhoneState.FIRST_TIME_FOUND); }
    public static void markOnboardingStarted(String phone) { update(phone, PhoneState.ONBOARDING_STARTED); }
    public static void markEkycCompleted(String phone)     { update(phone, PhoneState.EKYC_COMPLETED); }
    public static void markNotFirstTime(String phone)      { update(phone, PhoneState.NOT_FIRST_TIME); }

    private static void update(String phone, PhoneState state) {
        Map<String, String> s = loadState(); s.put(phone, state.name()); saveState(s);
        log.info("[PhoneManager] {} → {}", phone, state);
    }
}
