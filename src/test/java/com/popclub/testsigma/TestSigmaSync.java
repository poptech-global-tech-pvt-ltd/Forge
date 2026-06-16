package com.popclub.testsigma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popclub.tests.CardOnboardingNegativeTest;
import com.popclub.tests.CardOnboardingTest;
import com.popclub.tests.FakePanTest;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public class TestSigmaSync {

    private static final List<Class<?>> TEST_CLASSES = List.of(
            CardOnboardingTest.class,
            CardOnboardingNegativeTest.class,
            FakePanTest.class
    );

    private static final String MAPPING_PATH = "src/test/resources/testsigma-mapping.json";

    @Test
    public void syncAll() throws Exception {
        run();
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    private static void run() throws Exception {
        String projectId = TestSigmaConfig.projectId();
        Map<String, Map<String, String>> mapping = new LinkedHashMap<>();

        int created = 0, updated = 0, updateFailed = 0, failed = 0;
        List<String> needsAnnotationUpdate = new ArrayList<>();

        for (Class<?> testClass : TEST_CLASSES) {
            System.out.println("\n[Sync] Scanning " + testClass.getSimpleName());

            for (Method method : testClass.getDeclaredMethods()) {
                TestSigmaId tsId = method.getAnnotation(TestSigmaId.class);
                if (tsId == null) continue;

                Test testAnn = method.getAnnotation(Test.class);
                String title = testAnn != null && !testAnn.description().isEmpty()
                        ? testAnn.description() : method.getName();

                String folderId = resolveFolderId(tsId.folder());
                List<String> labelIds = resolveLabelIds(tsId.labels());

                try {
                    if (tsId.value().isEmpty()) {
                        Map<String, String> result = TestSigmaClient.createTestCase(
                                projectId, title, title, labelIds, folderId,
                                tsId.preconditions(), tsId.steps(), tsId.expectedResults());

                        String newId = result.get("humanId");
                        if (newId == null) {
                            failed++;
                            System.out.println("  [FAIL] " + method.getName() + " — null ID returned");
                            continue;
                        }

                        mapping.put(method.getName(), entryOf(newId, result.get("uuid")));
                        needsAnnotationUpdate.add(testClass.getSimpleName() + "#"
                                + method.getName() + " -> value = \"" + newId + "\"");
                        created++;
                        System.out.println("  [CREATE] " + method.getName() + " -> " + newId);

                    } else {
                        Map<String, String> lookup = TestSigmaClient.lookupHumanId(projectId, tsId.value());
                        if (lookup == null) {
                            failed++;
                            System.out.println("  [FAIL] " + method.getName()
                                    + " — humanId not found in TestSigma: " + tsId.value());
                            continue;
                        }
                        mapping.put(method.getName(), entryOf(lookup.get("humanId"), lookup.get("uuid")));

                        boolean ok = TestSigmaClient.updateTestCase(
                                projectId, lookup.get("uuid"), tsId.value(),
                                title, title, labelIds, folderId,
                                tsId.preconditions(), tsId.steps(), tsId.expectedResults());

                        if (ok) {
                            updated++;
                            System.out.println("  [UPDATE] " + method.getName() + " -> " + tsId.value());
                        } else {
                            updateFailed++;
                            System.out.println("  [UPDATE FAILED] " + method.getName() + " -> " + tsId.value());
                        }
                    }
                    Thread.sleep(100);
                } catch (Exception e) {
                    failed++;
                    System.out.println("  [FAIL] " + method.getName() + ": " + e.getMessage());
                }
            }
        }

        File out = new File(MAPPING_PATH);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out, mapping);

        System.out.println(String.format(
                "\n[Sync] Done — created=%d updated=%d updateFailed=%d failed=%d total=%d",
                created, updated, updateFailed, failed, mapping.size()));
        System.out.println("[Sync] Mapping written to " + out.getAbsolutePath());

        if (!needsAnnotationUpdate.isEmpty()) {
            System.out.println("\n[Sync] ⚠ Add these humanIds back to your @TestSigmaId annotations:");
            needsAnnotationUpdate.forEach(s -> System.out.println("  " + s));
        }
    }

    private static String resolveFolderId(String folder) {
        return switch (folder) {
            case "happy" -> TestSigmaConfig.folderHappy();
            case "negative" -> TestSigmaConfig.folderNegative();
            default -> throw new IllegalArgumentException("Unknown folder: " + folder);
        };
    }

    private static List<String> resolveLabelIds(String[] labels) {
        List<String> ids = new ArrayList<>();
        for (String label : labels) {
            String id = switch (label) {
                case "sanity" -> TestSigmaConfig.labelSanity();
                case "regression" -> TestSigmaConfig.labelRegression();
                case "can_automate" -> TestSigmaConfig.labelCanAutomate();
                default -> null;
            };
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private static Map<String, String> entryOf(String humanId, String uuid) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("humanId", humanId);
        entry.put("uuid", uuid);
        return entry;
    }
}
