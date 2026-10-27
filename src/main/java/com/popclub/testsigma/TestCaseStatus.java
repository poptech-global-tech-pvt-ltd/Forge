package com.popclub.testsigma;

public enum TestCaseStatus {

    PASSED("b3b7b0c9-4873-47ff-a8bc-2360c66b91ab"),
    FAILED("2651c1d0-c732-4e4c-a046-e58f3e58de27"),
    SKIPPED("e3634d42-a9b3-430d-a5f7-3741d2bfa15a"),
    BLOCKED("fb4c6254-deba-4c78-890f-2686a44935fe");

    private final String id;

    TestCaseStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}