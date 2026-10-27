package com.popclub.core;

public class LoggerUtil {

    public static void step(String message) {
        System.out.println("[STEP] " + message);
    }

    public static void pass(String message) {
        System.out.println("[PASS] " + message);
    }

    public static void fail(String message) {
        System.out.println("[FAIL] " + message);
    }
}