package com.popclub.testsigma;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class TestSigmaConfig {

    private static final Properties props = new Properties();
    private static final Properties localProps = new Properties();

    static {
        try (InputStream in = TestSigmaConfig.class.getClassLoader()
                .getResourceAsStream("config/testsigma.properties")) {
            if (in == null) throw new RuntimeException("testsigma.properties not found on classpath");
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load testsigma.properties", e);
        }

        try (InputStream in = TestSigmaConfig.class.getClassLoader()
                .getResourceAsStream("config/testsigma-local.properties")) {
            if (in != null) localProps.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load testsigma-local.properties", e);
        }
    }

    private static String get(String key) {
        String sysProp = System.getProperty(key);
        if (sysProp != null) return sysProp;
        String localProp = localProps.getProperty(key);
        if (localProp != null && !localProp.isBlank()) return localProp;
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

    public static String sessionCookie()    { return get("testsigma.session.cookie"); }
    public static String sessionUserId()    { return get("testsigma.session.user.id"); }
    public static String loginEmail()       { return get("testsigma.login.email"); }
    public static String loginPassword()    { return get("testsigma.login.password"); }
}
