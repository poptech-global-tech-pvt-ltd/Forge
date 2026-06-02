package com.popclub.model;

import com.popclub.core.Locator;
import java.util.List;

public class Step {
    public String action;
    public String locator;      // accessibilityId (qaTestTag) — first priority
    public String element;      // ElementRepository key
    public String text;         // visible text fallback
    public String resourceId;   // resource-id fallback
    public String bounds;       // "[x1,y1][x2,y2]" fallback
    public int    x;            // coordinate tap fallback
    public int    y;
    public String value;
    public String direction;    // for scroll/swipe
    public List<Locator> locators;
    public int retry;
    public String testCaseId;
}
