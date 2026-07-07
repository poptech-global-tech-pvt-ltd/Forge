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

        // Machine-local, git-ignored overrides (e.g. session cookie) — optional, so a
        // missing file is not an error.
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

    /**
     * Browser session cookie (X-TMS-SESSION-ID) used ONLY for attachment upload,
     * which requires a session-cookie-based endpoint (/private/graphql) that the
     * Bearer API token cannot authenticate against. This value expires and must
     * be refreshed manually — pass it via -Dtestsigma.session.cookie=... rather
     * than committing it to testsigma.properties.
     */
    public static String sessionCookie()    { return get("testsigma.session.cookie"); }

    /**
     * The TestSigma user id that OWNS the session cookie above (i.e. whoever is
     * logged into the browser). The private GraphQL attachment mutation silently
     * returns {"data":{"updateTestRunCaseStatus":null}} — no error — if the
     * "userId" field doesn't match the session's actual owner, so this must NOT
     * be testsigma.user.id (that's a separate service/API account). Pass via
     * -Dtestsigma.session.user.id=... alongside the session cookie.
     */
    public static String sessionUserId()    { return get("testsigma.session.user.id"); }

    /**
     * TestSigma login credentials used ONLY by TestSigmaSessionManager to
     * automatically refresh the session cookie above at the start of a run.
     * Local-only — never put these in testsigma.properties. Set them in
     * config/testsigma-local.properties or via -Dtestsigma.login.email=...
     * -Dtestsigma.login.password=... . If unset, auto-login is skipped and
     * whichever session cookie is already saved (if any) is used as-is.
     */
    public static String loginEmail()       { return get("testsigma.login.email"); }
    public static String loginPassword()    { return get("testsigma.login.password"); }
}
