package com.example.meruscrap;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class BleServicePerformanceMonitor {
    private static final String TAG = "BleServicePerformanceMonitor";

    private final Context context;
    private final SharedPreferences perfPrefs;

    // Performance metrics
    private long startTime;
    private long totalDataReceived;
    private long totalConnections;
    private long totalReconnections;
    private long averageConnectionTime;

    public BleServicePerformanceMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.perfPrefs = context.getSharedPreferences("ble_service_performance", Context.MODE_PRIVATE);
        this.startTime = System.currentTimeMillis();

        loadPerformanceData();
    }

    public void recordConnectionStart() {
        perfPrefs.edit()
                .putLong("last_connection_start", System.currentTimeMillis())
                .apply();
    }

    public void recordConnectionSuccess() {
        long connectionStart = perfPrefs.getLong("last_connection_start", 0);
        if (connectionStart > 0) {
            long connectionTime = System.currentTimeMillis() - connectionStart;

            totalConnections++;
            averageConnectionTime = (averageConnectionTime + connectionTime) / 2;

            perfPrefs.edit()
                    .putLong("total_connections", totalConnections)
                    .putLong("average_connection_time", averageConnectionTime)
                    .apply();

            Log.d(TAG, "Connection time: " + connectionTime + "ms");
        }
    }

    public void recordReconnection() {
        totalReconnections++;
        perfPrefs.edit()
                .putLong("total_reconnections", totalReconnections)
                .apply();
    }

    public void recordDataReceived(int bytes) {
        totalDataReceived += bytes;

        // Save every 1KB
        if (totalDataReceived % 1024 == 0) {
            perfPrefs.edit()
                    .putLong("total_data_received", totalDataReceived)
                    .apply();
        }
    }

    public PerformanceReport generatePerformanceReport() {
        PerformanceReport report = new PerformanceReport();

        report.uptimeMs = System.currentTimeMillis() - startTime;
        report.totalConnections = totalConnections;
        report.totalReconnections = totalReconnections;
        report.averageConnectionTimeMs = averageConnectionTime;
        report.totalDataReceivedBytes = totalDataReceived;

        // Calculate rates
        double uptimeHours = report.uptimeMs / (1000.0 * 60.0 * 60.0);
        report.connectionsPerHour = uptimeHours > 0 ? totalConnections / uptimeHours : 0;
        report.dataRateBytesPerSecond = (report.uptimeMs / 1000.0) > 0 ? totalDataReceived / (report.uptimeMs / 1000.0) : 0;

        return report;
    }

    private void loadPerformanceData() {
        totalConnections = perfPrefs.getLong("total_connections", 0);
        totalReconnections = perfPrefs.getLong("total_reconnections", 0);
        averageConnectionTime = perfPrefs.getLong("average_connection_time", 0);
        totalDataReceived = perfPrefs.getLong("total_data_received", 0);
    }

    public static class PerformanceReport {
        public long uptimeMs;
        public long totalConnections;
        public long totalReconnections;
        public long averageConnectionTimeMs;
        public long totalDataReceivedBytes;
        public double connectionsPerHour;
        public double dataRateBytesPerSecond;

        @Override
        public String toString() {
            return String.format(
                    "BLE Service Performance Report:\n" +
                            "Uptime: %.1f hours\n" +
                            "Total Connections: %d\n" +
                            "Reconnections: %d\n" +
                            "Avg Connection Time: %d ms\n" +
                            "Data Received: %.2f KB\n" +
                            "Connection Rate: %.2f/hour\n" +
                            "Data Rate: %.2f bytes/sec",
                    uptimeMs / (1000.0 * 60.0 * 60.0),
                    totalConnections,
                    totalReconnections,
                    averageConnectionTimeMs,
                    totalDataReceivedBytes / 1024.0,
                    connectionsPerHour,
                    dataRateBytesPerSecond
            );
        }
    }
}
