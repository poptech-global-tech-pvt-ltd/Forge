package com.popclub.driver;

/**
 * ThreadLocal holder for the ForgeDriverClient used by the YAML test runner.
 * Forge mode is active when -Ddriver=forge is set (or when this context holds a client).
 */
public class ForgeDriverContext {

    private static final ThreadLocal<ForgeDriverClient> CLIENT = new ThreadLocal<>();
    private static final ThreadLocal<String> DEVICE_SERIAL = new ThreadLocal<>();

    public static void set(ForgeDriverClient client, String deviceSerial) {
        CLIENT.set(client);
        DEVICE_SERIAL.set(deviceSerial);
    }

    public static ForgeDriverClient get() {
        return CLIENT.get();
    }

    public static String getDeviceSerial() {
        return DEVICE_SERIAL.get();
    }

    public static boolean isActive() {
        return CLIENT.get() != null;
    }

    public static void clear() {
        CLIENT.remove();
        DEVICE_SERIAL.remove();
    }

    /** Extract the first accessibilityId value from step locators, or null. */
    public static String forgeTag(com.popclub.model.Step step) {
        if (step.locators == null) return null;
        for (com.popclub.core.Locator loc : step.locators) {
            if ("accessibilityId".equals(loc.type) && loc.value != null && !loc.value.isBlank()) {
                return loc.value;
            }
        }
        // fall back to first locator value regardless of type
        if (!step.locators.isEmpty()) return step.locators.get(0).value;
        return null;
    }
}
