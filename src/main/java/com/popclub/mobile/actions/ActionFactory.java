package com.popclub.mobile.actions;

public class ActionFactory {

    public static Action get(String action) {
        switch (action) {
            case "tap": return new TapAction();
            case "enterText": return new EnterTextAction();
            case "launchApp": return new LaunchAppAction();
            case "verifyElement": return new VerifyElementAction();
            default: throw new RuntimeException("Unknown action: " + action);
        }
    }
}
