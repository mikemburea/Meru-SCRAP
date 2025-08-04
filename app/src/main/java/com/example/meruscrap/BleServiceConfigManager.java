package com.example.meruscrap;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class BleServiceConfigManager {
    private static final String TAG = "BleServiceConfigManager";
    private static final String PREFS_NAME = "ble_service_config";

    private final SharedPreferences configPrefs;

    // Default configuration values
    public static final int DEFAULT_MAX_RECONNECTION_ATTEMPTS = 5;
    public static final long DEFAULT_RECONNECTION_DELAY_MS = 3000;
    public static final long DEFAULT_CONNECTION_TIMEOUT_MS = 15000;
    public static final long DEFAULT_HEALTH_CHECK_INTERVAL_MS = 30000;
    public static final boolean DEFAULT_AUTO_RECONNECT_ENABLED = true;
    public static final boolean DEFAULT_PERSISTENT_NOTIFICATION = true;

    public BleServiceConfigManager(Context context) {
        this.configPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getMaxReconnectionAttempts() {
        return configPrefs.getInt("max_reconnection_attempts", DEFAULT_MAX_RECONNECTION_ATTEMPTS);
    }

    public void setMaxReconnectionAttempts(int attempts) {
        configPrefs.edit().putInt("max_reconnection_attempts", attempts).apply();
    }

    public long getReconnectionDelayMs() {
        return configPrefs.getLong("reconnection_delay_ms", DEFAULT_RECONNECTION_DELAY_MS);
    }

    public void setReconnectionDelayMs(long delayMs) {
        configPrefs.edit().putLong("reconnection_delay_ms", delayMs).apply();
    }

    public long getConnectionTimeoutMs() {
        return configPrefs.getLong("connection_timeout_ms", DEFAULT_CONNECTION_TIMEOUT_MS);
    }

    public void setConnectionTimeoutMs(long timeoutMs) {
        configPrefs.edit().putLong("connection_timeout_ms", timeoutMs).apply();
    }

    public long getHealthCheckIntervalMs() {
        return configPrefs.getLong("health_check_interval_ms", DEFAULT_HEALTH_CHECK_INTERVAL_MS);
    }

    public void setHealthCheckIntervalMs(long intervalMs) {
        configPrefs.edit().putLong("health_check_interval_ms", intervalMs).apply();
    }

    public boolean isAutoReconnectEnabled() {
        return configPrefs.getBoolean("auto_reconnect_enabled", DEFAULT_AUTO_RECONNECT_ENABLED);
    }

    public void setAutoReconnectEnabled(boolean enabled) {
        configPrefs.edit().putBoolean("auto_reconnect_enabled", enabled).apply();
    }

    public boolean isPersistentNotificationEnabled() {
        return configPrefs.getBoolean("persistent_notification", DEFAULT_PERSISTENT_NOTIFICATION);
    }

    public void setPersistentNotificationEnabled(boolean enabled) {
        configPrefs.edit().putBoolean("persistent_notification", enabled).apply();
    }

    public void resetToDefaults() {
        configPrefs.edit().clear().apply();
        Log.d(TAG, "Configuration reset to defaults");
    }

    public String getConfigurationSummary() {
        return String.format(
                "BLE Service Configuration:\n" +
                        "Max Reconnection Attempts: %d\n" +
                        "Reconnection Delay: %d ms\n" +
                        "Connection Timeout: %d ms\n" +
                        "Health Check Interval: %d ms\n" +
                        "Auto Reconnect: %s\n" +
                        "Persistent Notification: %s",
                getMaxReconnectionAttempts(),
                getReconnectionDelayMs(),
                getConnectionTimeoutMs(),
                getHealthCheckIntervalMs(),
                isAutoReconnectEnabled() ? "Enabled" : "Disabled",
                isPersistentNotificationEnabled() ? "Enabled" : "Disabled"
        );
    }
}
