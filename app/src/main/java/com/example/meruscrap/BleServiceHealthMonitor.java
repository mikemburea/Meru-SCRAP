package com.example.meruscrap;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility classes for BLE service management and monitoring
 */

// ============================================================================
// BLE SERVICE HEALTH MONITOR
// ============================================================================

public class BleServiceHealthMonitor {
    private static final String TAG = "BleServiceHealthMonitor";

    private final Context context;
    private final SharedPreferences healthPrefs;
    private final ConcurrentHashMap<String, AtomicLong> metrics;

    // Health thresholds
    private static final long MAX_CONNECTION_TIME_MS = 30000; // 30 seconds
    private static final long MAX_DATA_GAP_MS = 120000; // 2 minutes
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    // Metrics keys
    private static final String METRIC_CONNECTION_ATTEMPTS = "connection_attempts";
    private static final String METRIC_SUCCESSFUL_CONNECTIONS = "successful_connections";
    private static final String METRIC_CONNECTION_FAILURES = "connection_failures";
    private static final String METRIC_DISCONNECTIONS = "disconnections";
    private static final String METRIC_WEIGHT_READINGS = "weight_readings";
    private static final String METRIC_ERRORS = "errors";

    public BleServiceHealthMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.healthPrefs = context.getSharedPreferences("ble_service_health", Context.MODE_PRIVATE);
        this.metrics = new ConcurrentHashMap<>();

        initializeMetrics();
    }

    private void initializeMetrics() {
        metrics.put(METRIC_CONNECTION_ATTEMPTS, new AtomicLong(0));
        metrics.put(METRIC_SUCCESSFUL_CONNECTIONS, new AtomicLong(0));
        metrics.put(METRIC_CONNECTION_FAILURES, new AtomicLong(0));
        metrics.put(METRIC_DISCONNECTIONS, new AtomicLong(0));
        metrics.put(METRIC_WEIGHT_READINGS, new AtomicLong(0));
        metrics.put(METRIC_ERRORS, new AtomicLong(0));

        loadMetricsFromPreferences();
    }

    // Metric recording methods
    public void recordConnectionAttempt() {
        incrementMetric(METRIC_CONNECTION_ATTEMPTS);
        saveMetricsToPreferences();
    }

    public void recordSuccessfulConnection() {
        incrementMetric(METRIC_SUCCESSFUL_CONNECTIONS);
        healthPrefs.edit().putLong("last_successful_connection", System.currentTimeMillis()).apply();
        saveMetricsToPreferences();
    }

    public void recordConnectionFailure() {
        incrementMetric(METRIC_CONNECTION_FAILURES);
        saveMetricsToPreferences();
    }

    public void recordDisconnection() {
        incrementMetric(METRIC_DISCONNECTIONS);
        saveMetricsToPreferences();
    }

    public void recordWeightReading() {
        incrementMetric(METRIC_WEIGHT_READINGS);
        healthPrefs.edit().putLong("last_weight_reading", System.currentTimeMillis()).apply();

        // Save periodically (every 10 readings)
        if (getMetric(METRIC_WEIGHT_READINGS) % 10 == 0) {
            saveMetricsToPreferences();
        }
    }

    public void recordError() {
        incrementMetric(METRIC_ERRORS);
        saveMetricsToPreferences();
    }

    // Health assessment methods
    public boolean isServiceHealthy() {
        long consecutiveErrors = getConsecutiveErrors();
        long timeSinceLastReading = getTimeSinceLastWeightReading();
        double connectionSuccessRate = getConnectionSuccessRate();

        return consecutiveErrors < MAX_CONSECUTIVE_ERRORS &&
                timeSinceLastReading < MAX_DATA_GAP_MS &&
                connectionSuccessRate > 0.5; // At least 50% success rate
    }

    public HealthReport generateHealthReport() {
        HealthReport report = new HealthReport();

        report.totalConnectionAttempts = getMetric(METRIC_CONNECTION_ATTEMPTS);
        report.successfulConnections = getMetric(METRIC_SUCCESSFUL_CONNECTIONS);
        report.connectionFailures = getMetric(METRIC_CONNECTION_FAILURES);
        report.disconnections = getMetric(METRIC_DISCONNECTIONS);
        report.weightReadings = getMetric(METRIC_WEIGHT_READINGS);
        report.errors = getMetric(METRIC_ERRORS);

        report.connectionSuccessRate = getConnectionSuccessRate();
        report.timeSinceLastConnection = getTimeSinceLastSuccessfulConnection();
        report.timeSinceLastReading = getTimeSinceLastWeightReading();
        report.consecutiveErrors = getConsecutiveErrors();

        report.isHealthy = isServiceHealthy();
        report.healthScore = calculateHealthScore();

        return report;
    }

    // Utility methods
    private void incrementMetric(String key) {
        AtomicLong metric = metrics.get(key);
        if (metric != null) {
            metric.incrementAndGet();
        }
    }

    private long getMetric(String key) {
        AtomicLong metric = metrics.get(key);
        return metric != null ? metric.get() : 0;
    }

    private double getConnectionSuccessRate() {
        long attempts = getMetric(METRIC_CONNECTION_ATTEMPTS);
        long successes = getMetric(METRIC_SUCCESSFUL_CONNECTIONS);
        return attempts > 0 ? (double) successes / attempts : 0.0;
    }

    private long getTimeSinceLastSuccessfulConnection() {
        long lastConnection = healthPrefs.getLong("last_successful_connection", 0);
        return lastConnection > 0 ? System.currentTimeMillis() - lastConnection : Long.MAX_VALUE;
    }

    private long getTimeSinceLastWeightReading() {
        long lastReading = healthPrefs.getLong("last_weight_reading", 0);
        return lastReading > 0 ? System.currentTimeMillis() - lastReading : Long.MAX_VALUE;
    }

    private long getConsecutiveErrors() {
        return healthPrefs.getLong("consecutive_errors", 0);
    }

    private double calculateHealthScore() {
        double connectionScore = Math.min(getConnectionSuccessRate() * 100, 100);
        double dataScore = getTimeSinceLastWeightReading() < MAX_DATA_GAP_MS ? 100 : 0;
        double errorScore = Math.max(100 - (getConsecutiveErrors() * 20), 0);

        return (connectionScore + dataScore + errorScore) / 3.0;
    }

    private void loadMetricsFromPreferences() {
        for (String key : metrics.keySet()) {
            long value = healthPrefs.getLong(key, 0);
            AtomicLong metric = metrics.get(key);
            if (metric != null) {
                metric.set(value);
            }
        }
    }

    private void saveMetricsToPreferences() {
        SharedPreferences.Editor editor = healthPrefs.edit();
        for (String key : metrics.keySet()) {
            AtomicLong metric = metrics.get(key);
            if (metric != null) {
                editor.putLong(key, metric.get());
            }
        }
        editor.apply();
    }

    public static class HealthReport {
        public long totalConnectionAttempts;
        public long successfulConnections;
        public long connectionFailures;
        public long disconnections;
        public long weightReadings;
        public long errors;

        public double connectionSuccessRate;
        public long timeSinceLastConnection;
        public long timeSinceLastReading;
        public long consecutiveErrors;

        public boolean isHealthy;
        public double healthScore;

        @Override
        public String toString() {
            return String.format(
                    "BLE Service Health Report:\n" +
                            "Health Score: %.1f%%\n" +
                            "Status: %s\n\n" +
                            "Connection Stats:\n" +
                            "- Attempts: %d\n" +
                            "- Successes: %d\n" +
                            "- Failures: %d\n" +
                            "- Success Rate: %.1f%%\n\n" +
                            "Data Stats:\n" +
                            "- Weight Readings: %d\n" +
                            "- Time Since Last Reading: %d ms\n" +
                            "- Consecutive Errors: %d\n\n" +
                            "Performance:\n" +
                            "- Disconnections: %d\n" +
                            "- Total Errors: %d",
                    healthScore,
                    isHealthy ? "Healthy" : "Unhealthy",
                    totalConnectionAttempts,
                    successfulConnections,
                    connectionFailures,
                    connectionSuccessRate * 100,
                    weightReadings,
                    timeSinceLastReading,
                    consecutiveErrors,
                    disconnections,
                    errors
            );
        }
    }

}