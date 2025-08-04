package com.example.meruscrap;

public  class TestCase {
    private final String name;
    final java.util.List<TestResult> results = new java.util.ArrayList<>();
    boolean overallResult = true;

    public TestCase(String name) {
        this.name = name;
    }

    public void addResult(String testName, boolean passed, String details) {
        results.add(new TestResult(testName, passed, details));
    }

    public void calculateOverallResult() {
        overallResult = results.stream().allMatch(r -> r.passed);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Test Case: ").append(name).append(" [").append(overallResult ? "PASSED" : "FAILED").append("]\n");
        for (TestResult result : results) {
            sb.append("  ").append(result.toString()).append("\n");
        }
        return sb.toString();
    }
}
