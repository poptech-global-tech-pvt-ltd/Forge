package com.popclub.testsigma;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class TestSigmaConfig {

    private static final Properties props = new Properties();

    static {
        try (InputStream in = TestSigmaConfig.class.getClassLoader()
                .getResourceAsStream("config/testsigma.properties")) {
            if (in == null) throw new RuntimeException("testsigma.properties not found on classpath");
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load testsigma.properties", e);
        }
    }

    private static String get(String key) {
        String sysProp = System.getProperty(key);
        if (sysProp != null) return sysProp;
        return props.getProperty(key);
    }

    public static String baseUrl()          { return get("testsigma.base.url"); }
    public static String token()            { return get("testsigma.token"); }
    public static String userId()           { return get("testsigma.user.id"); }
    public static String projectId()        { return get("testsigma.project.id"); }

    public static String folderNegative()   { return get("testsigma.folder.negative"); }
    public static String folderHappy()      { return get("testsigma.folder.happy"); }

    public static String labelSanity()      { return get("testsigma.label.sanity"); }
    public static String labelRegression()  { return get("testsigma.label.regression"); }
    public static String labelCanAutomate() { return get("testsigma.label.can.automate"); }

    public static String typeId()           { return get("testsigma.type.id"); }
    public static String statusId()         { return get("testsigma.status.id"); }
    public static String automationTypeId() { return get("testsigma.automation.type.id"); }
    public static String priorityId()       { return get("testsigma.priority.id"); }

    public static String runTitlePrefix()   { return get("testsigma.run.title.prefix"); }
    public static String runTags()          { return get("testsigma.run.tags"); }
}
