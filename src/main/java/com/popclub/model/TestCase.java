package com.popclub.model;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TestCase {
    public String testName;
    public String platform;
    public List<String> features;
    public List<Step> steps;
    public List<String> tags;   // ✅ ADD THIS
    public int retry;


    @JsonProperty("testCaseIds")   // 🔥 ADD THIS
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<String> testCaseIds;

    public boolean noReset = false;

    public String mapping = "TEST";


}