package com.example.meruscrap;

public class TestResults {
    private final java.util.List<TestCase> testCases = new java.util.ArrayList<>();
    private final long timestamp = System.currentTimeMillis();

    public void addTestCase(TestCase testCase) {
        testCases.add(testCase);
    }

    public boolean allTestsPassed() {
        return testCases.stream().allMatch(tc -> tc.overallResult);
    }

    public int getTotalTests() {
        return testCases.stream().mapToInt(tc -> tc.results.size()).sum();
    }

    public int getPassedTests() {
        return testCases.stream().mapToInt(tc -> (int) tc.results.stream().filter(r -> r.passed).count()).sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== BLE SERVICE TEST RESULTS ===\n");
        sb.append("Timestamp: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(timestamp))).append("\n");
        sb.append("Overall Status: ").append(allTestsPassed() ? "PASSED" : "FAILED").append("\n");
        sb.append("Tests Passed: ").append(getPassedTests()).append("/").append(getTotalTests()).append("\n\n");

        for (TestCase testCase : testCases) {
            sb.append(testCase.toString()).append("\n");
        }

        return sb.toString();
    }
}

