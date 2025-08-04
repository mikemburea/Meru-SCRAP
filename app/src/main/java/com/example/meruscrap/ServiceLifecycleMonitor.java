package com.example.meruscrap;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import java.util.List;

public class ServiceLifecycleMonitor {
    private static final String TAG = "ServiceLifecycleMonitor";

    private final Context context;
    private long serviceStartTime;
    private int serviceRestarts;
    private long lastRestartTime;

    public ServiceLifecycleMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.serviceStartTime = System.currentTimeMillis();
    }

    public void recordServiceStart() {
        serviceStartTime = System.currentTimeMillis();
        Log.d(TAG, "Service started at: " + serviceStartTime);
    }

    public void recordServiceRestart() {
        serviceRestarts++;
        lastRestartTime = System.currentTimeMillis();
        Log.w(TAG, "Service restart #" + serviceRestarts + " at: " + lastRestartTime);
    }

    public boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
            for (ActivityManager.RunningServiceInfo service : services) {
                if (BleScaleService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public ServiceLifecycleReport generateLifecycleReport() {
        ServiceLifecycleReport report = new ServiceLifecycleReport();

        report.isRunning = isServiceRunning();
        report.uptimeMs = System.currentTimeMillis() - serviceStartTime;
        report.totalRestarts = serviceRestarts;
        report.timeSinceLastRestart = lastRestartTime > 0 ?
                System.currentTimeMillis() - lastRestartTime : -1;

        // Calculate stability metrics
        double uptimeHours = report.uptimeMs / (1000.0 * 60.0 * 60.0);
        report.restartsPerHour = uptimeHours > 0 ? serviceRestarts / uptimeHours : 0;

        if (serviceRestarts == 0) {
            report.stability = ServiceLifecycleReport.Stability.EXCELLENT;
        } else if (report.restartsPerHour < 0.1) {
            report.stability = ServiceLifecycleReport.Stability.GOOD;
        } else if (report.restartsPerHour < 1.0) {
            report.stability = ServiceLifecycleReport.Stability.FAIR;
        } else {
            report.stability = ServiceLifecycleReport.Stability.POOR;
        }

        return report;
    }

    public static class ServiceLifecycleReport {
        public boolean isRunning;
        public long uptimeMs;
        public int totalRestarts;
        public long timeSinceLastRestart;
        public double restartsPerHour;
        public Stability stability;

        public enum Stability {
            EXCELLENT, GOOD, FAIR, POOR
        }

        @Override
        public String toString() {
            return String.format(
                    "Service Lifecycle Report:\n" +
                            "Running: %s\n" +
                            "Uptime: %.1f hours\n" +
                            "Total Restarts: %d\n" +
                            "Restarts/Hour: %.2f\n" +
                            "Stability: %s\n" +
                            "Time Since Last Restart: %s",
                    isRunning ? "Yes" : "No",
                    uptimeMs / (1000.0 * 60.0 * 60.0),
                    totalRestarts,
                    restartsPerHour,
                    stability,
                    timeSinceLastRestart > 0 ?
                            String.format("%.1f minutes", timeSinceLastRestart / (1000.0 * 60.0)) : "N/A"
            );
        }
    }
}