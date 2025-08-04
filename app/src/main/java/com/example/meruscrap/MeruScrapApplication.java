package com.example.meruscrap;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Application class for MeruScrap
 * Initializes persistent BLE service and connection manager
 */
public class MeruScrapApplication extends Application {
    private static final String TAG = "MeruScrapApplication";

    private BleConnectionManager connectionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MeruScrap Application starting");

        // Initialize the BLE connection manager
        initializeBleConnectionManager();

        Log.d(TAG, "MeruScrap Application initialization completed");
    }

    private void initializeBleConnectionManager() {
        try {
            Log.d(TAG, "Initializing BLE Connection Manager");

            // Initialize the connection manager
            BleConnectionManager.initializeInApplication(this);
            connectionManager = BleConnectionManager.getInstance();

            Log.d(TAG, "BLE Connection Manager initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize BLE Connection Manager", e);
            // App can still function without BLE, so don't crash
        }
    }

    @Override
    public void onTerminate() {
        Log.d(TAG, "Application terminating");

        // Shutdown the connection manager
        if (connectionManager != null) {
            try {
                connectionManager.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down connection manager", e);
            }
        }

        super.onTerminate();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Application low memory warning");
        // The service should continue running even under memory pressure
        // but we can log this for monitoring
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        switch (level) {
            case TRIM_MEMORY_RUNNING_MODERATE:
            case TRIM_MEMORY_RUNNING_LOW:
            case TRIM_MEMORY_RUNNING_CRITICAL:
                Log.w(TAG, "Memory trim level: " + level + " (app running)");
                break;

            case TRIM_MEMORY_UI_HIDDEN:
                Log.d(TAG, "UI hidden - app in background");
                break;

            case TRIM_MEMORY_BACKGROUND:
            case TRIM_MEMORY_MODERATE:
            case TRIM_MEMORY_COMPLETE:
                Log.w(TAG, "Memory trim level: " + level + " (app in background)");
                break;
        }
    }

    /**
     * Get the BLE connection manager instance
     * This provides a convenient way for activities/fragments to access the manager
     */
    public BleConnectionManager getBleConnectionManager() {
        return connectionManager;
    }

    /**
     * Check if BLE service is available
     */
    public boolean isBleServiceAvailable() {
        return connectionManager != null && connectionManager.isServiceReady();
    }

    /**
     * Get BLE service status for debugging
     */
    public String getBleServiceStatus() {
        if (connectionManager == null) {
            return "Connection manager not initialized";
        }
        return connectionManager.getConnectionStatus();
    }
}