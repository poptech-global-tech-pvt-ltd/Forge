package com.popclub.testsigma;

public enum RunStatus {
    ACTIVE,
    RUNNING,
    FINISHED,
    ABORTED;

    public String value() {
        return this.name();
    }
}