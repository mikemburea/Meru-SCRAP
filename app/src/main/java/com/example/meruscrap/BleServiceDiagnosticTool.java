package com.example.meruscrap;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class BleServiceDiagnosticTool {
    private static final String TAG = "BleServiceDiagnosticTool";

    private final Context context;
    private final BleConnectionManager connectionManager;
    private final BleServiceHealthMonitor healthMonitor;
    private final BatteryOptimizationHandler batteryHandler;
    private final ServiceLifecycleMonitor lifecycleMonitor;

    public BleServiceDiagnosticTool(Context context, BleConnectionManager connectionManager) {
        this.context = context.getApplicationContext();
        this.connectionManager = connectionManager;
        this.healthMonitor = new BleServiceHealthMonitor(context);
        this.batteryHandler = new BatteryOptimizationHandler(context);
        this.lifecycleMonitor = new ServiceLifecycleMonitor(context);
    }

    public ComprehensiveDiagnosticReport generateComprehensiveReport() {
        ComprehensiveDiagnosticReport report = new ComprehensiveDiagnosticReport();

        // Basic service info
        report.timestamp = System.currentTimeMillis();
        report.serviceRunning = lifecycleMonitor.isServiceRunning();
        report.managerReady = connectionManager != null && connectionManager.isServiceReady();

        // Connection info
        if (connectionManager != null) {
            report.scaleConnected = connectionManager.isConnected();
            report.scaleConnecting = connectionManager.isConnecting();
            report.connectedDeviceName = connectionManager.getConnectedDeviceName();
            report.currentWeight = connectionManager.getCurrentWeight();
            report.weightStable = connectionManager.isWeightStable();
        }

        // Health metrics
        report.healthReport = healthMonitor.generateHealthReport();

        // Battery optimization
        report.batteryStatus = batteryHandler.getBatteryOptimizationStatus();

        // Service lifecycle
        report.lifecycleReport = lifecycleMonitor.generateLifecycleReport();

        // Overall assessment
        report.overallHealth = assessOverallHealth(report);
        report.recommendations = generateRecommendations(report);

        return report;
    }

    private OverallHealth assessOverallHealth(ComprehensiveDiagnosticReport report) {
        int healthScore = 0;
        int maxScore = 0;

        // Service running (25 points)
        maxScore += 25;
        if (report.serviceRunning) healthScore += 25;

        // Manager ready (25 points)
        maxScore += 25;
        if (report.managerReady) healthScore += 25;

        // Health metrics (25 points)
        maxScore += 25;
        if (report.healthReport.isHealthy) healthScore += 25;

        // Battery optimization (25 points)
        maxScore += 25;
        if (report.batteryStatus.riskLevel == BatteryOptimizationHandler.BatteryOptimizationStatus.RiskLevel.LOW) {
            healthScore += 25;
        } else if (report.batteryStatus.riskLevel == BatteryOptimizationHandler.BatteryOptimizationStatus.RiskLevel.MEDIUM) {
            healthScore += 15;
        }

        double healthPercentage = (double) healthScore / maxScore * 100;

        if (healthPercentage >= 90) {
            return OverallHealth.EXCELLENT;
        } else if (healthPercentage >= 75) {
            return OverallHealth.GOOD;
        } else if (healthPercentage >= 50) {
            return OverallHealth.FAIR;
        } else {
            return OverallHealth.POOR;
        }
    }

    private List<String> generateRecommendations(ComprehensiveDiagnosticReport report) {
        List<String> recommendations = new ArrayList<>();

        if (!report.serviceRunning) {
            recommendations.add("Critical: BLE service is not running. Restart the app or check permissions.");
        }

        if (!report.managerReady) {
            recommendations.add("Warning: Connection manager is not ready. Check Bluetooth permissions.");
        }

        if (!report.healthReport.isHealthy) {
            recommendations.add("Health issue detected: " + report.healthReport.toString());
        }

        if (report.batteryStatus.riskLevel != BatteryOptimizationHandler.BatteryOptimizationStatus.RiskLevel.LOW) {
            recommendations.add("Battery optimization: " + report.batteryStatus.recommendation);
        }

        if (report.lifecycleReport.stability == ServiceLifecycleMonitor.ServiceLifecycleReport.Stability.POOR) {
            recommendations.add("Service stability: Consider investigating frequent restarts.");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("All systems operating normally.");
        }

        return recommendations;
    }

    public enum OverallHealth {
        EXCELLENT, GOOD, FAIR, POOR
    }

    public static class ComprehensiveDiagnosticReport {
        public long timestamp;
        public boolean serviceRunning;
        public boolean managerReady;
        public boolean scaleConnected;
        public boolean scaleConnecting;
        public String connectedDeviceName;
        public double currentWeight;
        public boolean weightStable;

        public BleServiceHealthMonitor.HealthReport healthReport;
        public BatteryOptimizationHandler.BatteryOptimizationStatus batteryStatus;
        public ServiceLifecycleMonitor.ServiceLifecycleReport lifecycleReport;

        public OverallHealth overallHealth;
        public List<String> recommendations;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== COMPREHENSIVE BLE SERVICE DIAGNOSTIC REPORT ===\n");
            sb.append("Generated: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(timestamp))).append("\n");
            sb.append("Overall Health: ").append(overallHealth).append("\n\n");

            sb.append("SERVICE STATUS:\n");
            sb.append("- Service Running: ").append(serviceRunning ? "Yes" : "No").append("\n");
            sb.append("- Manager Ready: ").append(managerReady ? "Yes" : "No").append("\n");
            sb.append("- Scale Connected: ").append(scaleConnected ? "Yes" : "No").append("\n");
            if (scaleConnected) {
                sb.append("- Device: ").append(connectedDeviceName).append("\n");
                sb.append("- Weight: ").append(String.format("%.2f kg", currentWeight)).append(weightStable ? " (Stable)" : " (Unstable)").append("\n");
            }
            sb.append("\n");

            sb.append(healthReport.toString()).append("\n\n");
            sb.append(batteryStatus.toString()).append("\n\n");
            sb.append(lifecycleReport.toString()).append("\n\n");

            sb.append("RECOMMENDATIONS:\n");
            for (int i = 0; i < recommendations.size(); i++) {
                sb.append((i + 1)).append(". ").append(recommendations.get(i)).append("\n");
            }

            return sb.toString();
        }
    }
}