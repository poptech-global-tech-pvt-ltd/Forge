package com.popclub.model;

import com.popclub.core.Locator;
import java.util.List;

public class Step {
    public String action;
    public String locator;
    public String element;
    public String value;
    public List<Locator> locators;
    public int retry;
    public String testCaseId;
}
