package com.example.meruscrap;

public class TestResult {
    private final String name;
    final boolean passed;
    private final String details;

    public TestResult(String name, boolean passed, String details) {
        this.name = name;
        this.passed = passed;
        this.details = details;
    }

    @Override
    public String toString() {
        return String.format("%-30s [%s] %s", name, passed ? "PASS" : "FAIL", details);
    }
}
