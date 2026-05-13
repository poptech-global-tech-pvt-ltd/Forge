package com.popclub;

import org.testng.TestNG;

import java.util.List;
import java.util.Map;

/**
 * Entry point for running test suites via JAR.
 * Usage: java -DMODE=api -DENV=qa -jar forge.jar
 *
 * MODE values:
 *   api        → testng-api.xml
 *   mobile     → testng.xml
 *   web        → testng-web.xml
 */
public class Invoke {

    private static final Map<String, String> MODE_TO_SUITE = Map.of(
            "api",    "src/test/resources/testNg/testng-api.xml",
            "mobile", "src/test/resources/testNg/testng.xml",
            "web",    "src/test/resources/testNg/testng-web.xml"
    );

    public static void main(String[] args) {
        String mode = System.getProperty("MODE", "api");
        String suiteFile = MODE_TO_SUITE.getOrDefault(mode, MODE_TO_SUITE.get("api"));

        System.out.println("[Invoke] MODE=" + mode + " SUITE=" + suiteFile
                + " ENV=" + System.getProperty("ENV", "local"));

        TestNG testng = new TestNG();
        testng.setTestSuites(List.of(suiteFile));
        testng.run();

        System.exit(testng.hasFailure() ? 1 : 0);
    }
}
