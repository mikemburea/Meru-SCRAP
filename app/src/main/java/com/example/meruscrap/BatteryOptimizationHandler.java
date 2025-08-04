package com.example.meruscrap;


import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.RequiresApi;
import java.util.List;

// ============================================================================
// BATTERY OPTIMIZATION HANDLER
// ============================================================================

public class BatteryOptimizationHandler {
    private static final String TAG = "BatteryOptimizationHandler";

    private final Context context;
    private final PowerManager powerManager;

    public BatteryOptimizationHandler(Context context) {
        this.context = context.getApplicationContext();
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    /**
     * Check if the app is whitelisted from battery optimization
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean isIgnoringBatteryOptimization() {
        if (powerManager != null) {
            return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return true; // Assume optimized on older versions
    }

    /**
     * Get battery optimization status and recommendations
     */
    public BatteryOptimizationStatus getBatteryOptimizationStatus() {
        BatteryOptimizationStatus status = new BatteryOptimizationStatus();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            status.isWhitelisted = isIgnoringBatteryOptimization();
            status.canRequestWhitelist = true;
        } else {
            status.isWhitelisted = true; // No battery optimization on older versions
            status.canRequestWhitelist = false;
        }

        status.batteryLevel = getBatteryLevel();
        status.isCharging = isCharging();
        status.powerSaveMode = isPowerSaveMode();

        // Assess risk level
        if (!status.isWhitelisted && status.powerSaveMode) {
            status.riskLevel = BatteryOptimizationStatus.RiskLevel.HIGH;
            status.recommendation = "App may be killed by system. Please whitelist from battery optimization.";
        } else if (!status.isWhitelisted) {
            status.riskLevel = BatteryOptimizationStatus.RiskLevel.MEDIUM;
            status.recommendation = "Consider whitelisting from battery optimization for best performance.";
        } else {
            status.riskLevel = BatteryOptimizationStatus.RiskLevel.LOW;
            status.recommendation = "Battery optimization properly configured.";
        }

        return status;
    }

    /**
     * Create intent to request battery optimization whitelist
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public Intent createBatteryOptimizationIntent() {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        return intent;
    }

    private int getBatteryLevel() {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);

            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                return (int) ((level / (float) scale) * 100);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting battery level", e);
        }
        return -1;
    }

    private boolean isCharging() {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);

            if (batteryStatus != null) {
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking charging status", e);
        }
        return false;
    }

    private boolean isPowerSaveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && powerManager != null) {
            return powerManager.isPowerSaveMode();
        }
        return false;
    }

    public static class BatteryOptimizationStatus {
        public boolean isWhitelisted;
        public boolean canRequestWhitelist;
        public int batteryLevel;
        public boolean isCharging;
        public boolean powerSaveMode;
        public RiskLevel riskLevel;
        public String recommendation;

        public enum RiskLevel {
            LOW, MEDIUM, HIGH
        }

        @Override
        public String toString() {
            return String.format(
                    "Battery Optimization Status:\n" +
                            "Whitelisted: %s\n" +
                            "Battery Level: %d%%\n" +
                            "Charging: %s\n" +
                            "Power Save Mode: %s\n" +
                            "Risk Level: %s\n" +
                            "Recommendation: %s",
                    isWhitelisted ? "Yes" : "No",
                    batteryLevel,
                    isCharging ? "Yes" : "No",
                    powerSaveMode ? "Yes" : "No",
                    riskLevel,
                    recommendation
            );
        }
    }
}