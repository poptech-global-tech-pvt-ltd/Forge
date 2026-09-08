package com.popclub.testsigma;

public enum RunStatus {
    ACTIVE,
    RUNNING,
    CLOSED,
    ABORTED;

    public String value() {
        return this.name();
    }
}