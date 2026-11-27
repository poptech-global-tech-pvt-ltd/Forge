package com.popclub.android.actions;

import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.model.Step;

public class ClearLogcatAction implements Action {

    @Override
    public void perform(Step step) {
        String udid = AppiumDriverManager.deviceInfo.get() != null
            ? AppiumDriverManager.deviceInfo.get().udid
            : System.getProperty("deviceSerial", null);
        try {
            String[] cmd;
            if (udid != null && !udid.isBlank()) {
                cmd = new String[]{"adb", "-s", udid, "logcat", "-c"};
            } else {
                cmd = new String[]{"adb", "logcat", "-c"};
            }
            Process proc = Runtime.getRuntime().exec(cmd);
            proc.waitFor();
            System.out.println("[clearLogcat] Logcat buffer cleared");
        } catch (Exception e) {
            System.out.println("[clearLogcat] Failed: " + e.getMessage());
        }
    }
}
