package com.example.meruscrap;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton class that manages the connection to BleScaleService
 * and provides a unified interface for UI components
 */
public class BleConnectionManager {
    private static final String TAG = "BleConnectionManager";
    private static BleConnectionManager instance;

    private Context applicationContext;
    private BleScaleService bleScaleService;
    private boolean isServiceBound = false;
    private boolean isServiceStarted = false;

    // Listeners for connection manager events
    private final CopyOnWriteArrayList<ConnectionManagerListener> listeners = new CopyOnWriteArrayList<>();

    // Service connection
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");
            BleScaleService.BleScaleServiceBinder binder = (BleScaleService.BleScaleServiceBinder) service;
            bleScaleService = binder.getService();
            isServiceBound = true;

            // Add this manager as a service listener
            bleScaleService.addListener(serviceListener);

            // Notify listeners that manager is ready
            notifyManagerReady();

            Log.d(TAG, "BLE Connection Manager is now connected to service");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "Service disconnected unexpectedly");

            if (bleScaleService != null) {
                bleScaleService.removeListener(serviceListener);
            }

            bleScaleService = null;
            isServiceBound = false;

            // Notify listeners that manager is no longer available
            notifyManagerDisconnected();

            // Try to reconnect to service
            startAndBindService();
        }
    };

    // Service listener that forwards events to connection manager listeners
    private final BleScaleService.BleScaleServiceListener serviceListener = new BleScaleService.BleScaleServiceListener() {
        @Override
        public void onConnectionStateChanged(boolean isConnected, String deviceName) {
            Log.d(TAG, "Service connection state changed: " + isConnected + ", device: " + deviceName);
            for (ConnectionManagerListener listener : listeners) {
                try {
                    listener.onConnectionStateChanged(isConnected, deviceName);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying listener of connection state change", e);
                }
            }
        }

        @Override
        public void onWeightReceived(double weight, boolean isStable) {
            for (ConnectionManagerListener listener : listeners) {
                try {
                    listener.onWeightReceived(weight, isStable);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying listener of weight data", e);
                }
            }
        }

        @Override
        public void onError(String error) {
            Log.e(TAG, "Service error: " + error);
            for (ConnectionManagerListener listener : listeners) {
                try {
                    listener.onError(error);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying listener of error", e);
                }
            }
        }

        @Override
        public void onServiceStatusChanged(String status) {
            Log.d(TAG, "Service status: " + status);
            for (ConnectionManagerListener listener : listeners) {
                try {
                    listener.onServiceStatusChanged(status);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying listener of status change", e);
                }
            }
        }
    };

    public interface ConnectionManagerListener {
        void onManagerReady();
        void onManagerDisconnected();
        void onConnectionStateChanged(boolean isConnected, String deviceName);
        void onWeightReceived(double weight, boolean isStable);
        void onError(String error);
        void onServiceStatusChanged(String status);
    }

    // Singleton pattern
    public static synchronized BleConnectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new BleConnectionManager(context.getApplicationContext());
        }
        return instance;
    }

    public static BleConnectionManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BleConnectionManager must be initialized with context first");
        }
        return instance;
    }

    private BleConnectionManager(Context applicationContext) {
        this.applicationContext = applicationContext;
        Log.d(TAG, "BleConnectionManager created");
    }

    // ============================================================================
    // LIFECYCLE MANAGEMENT
    // ============================================================================

    public void initialize() {
        Log.d(TAG, "Initializing BLE Connection Manager");
        startAndBindService();
    }

    public void shutdown() {
        Log.d(TAG, "Shutting down BLE Connection Manager");

        // Remove all listeners
        listeners.clear();

        // Unbind from service
        unbindService();

        // Stop service
        stopService();

        instance = null;
    }

    private void startAndBindService() {
        try {
            // Start the service first
            Intent serviceIntent = new Intent(applicationContext, BleScaleService.class);

            if (!isServiceStarted) {
                applicationContext.startForegroundService(serviceIntent);
                isServiceStarted = true;
                Log.d(TAG, "BLE Scale Service started");
            }

            // Bind to the service
            if (!isServiceBound) {
                boolean bindResult = applicationContext.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
                Log.d(TAG, "Service bind attempt: " + bindResult);

                if (!bindResult) {
                    Log.e(TAG, "Failed to bind to BLE Scale Service");
                    notifyError("Failed to bind to BLE service");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error starting/binding service", e);
            notifyError("Error starting BLE service: " + e.getMessage());
        }
    }

    private void unbindService() {
        if (isServiceBound) {
            try {
                if (bleScaleService != null) {
                    bleScaleService.removeListener(serviceListener);
                }
                applicationContext.unbindService(serviceConnection);
                isServiceBound = false;
                bleScaleService = null;
                Log.d(TAG, "Service unbound");
            } catch (Exception e) {
                Log.e(TAG, "Error unbinding service", e);
            }
        }
    }

    private void stopService() {
        if (isServiceStarted) {
            try {
                Intent serviceIntent = new Intent(applicationContext, BleScaleService.class);
                applicationContext.stopService(serviceIntent);
                isServiceStarted = false;
                Log.d(TAG, "Service stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping service", e);
            }
        }
    }

    // ============================================================================
    // LISTENER MANAGEMENT
    // ============================================================================

    public void addListener(ConnectionManagerListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            Log.d(TAG, "Listener added. Total: " + listeners.size());

            // If manager is ready, immediately notify the new listener
            if (isServiceBound && bleScaleService != null) {
                try {
                    listener.onManagerReady();
                    // Also send current connection state
                    listener.onConnectionStateChanged(
                            bleScaleService.isConnected(),
                            bleScaleService.getConnectedDeviceName()
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying new listener", e);
                }
            }
        }
    }

    public void removeListener(ConnectionManagerListener listener) {
        if (listeners.remove(listener)) {
            Log.d(TAG, "Listener removed. Total: " + listeners.size());
        }
    }

    // ============================================================================
    // PUBLIC API - Delegate to service
    // ============================================================================

    public void connectToDevice(String deviceAddress, String deviceName) {
        if (isServiceReady()) {
            bleScaleService.connectToDevice(deviceAddress, deviceName);
        } else {
            Log.w(TAG, "Service not ready, cannot connect to device");
            notifyError("BLE service not ready");
        }
    }

    public void disconnect() {
        if (isServiceReady()) {
            bleScaleService.disconnect();
        } else {
            Log.w(TAG, "Service not ready, cannot disconnect");
        }
    }

    public void tare() {
        if (isServiceReady()) {
            bleScaleService.tare();
        } else {
            Log.w(TAG, "Service not ready, cannot tare");
            notifyError("BLE service not ready for tare operation");
        }
    }

    public boolean isConnected() {
        return isServiceReady() && bleScaleService.isConnected();
    }

    public boolean isConnecting() {
        return isServiceReady() && bleScaleService.isConnecting();
    }

    public String getConnectedDeviceName() {
        return isServiceReady() ? bleScaleService.getConnectedDeviceName() : "";
    }

    public double getCurrentWeight() {
        return isServiceReady() ? bleScaleService.getCurrentWeight() : 0.0;
    }

    public boolean isWeightStable() {
        return isServiceReady() && bleScaleService.isWeightStable();
    }

    public boolean isServiceReady() {
        return isServiceBound && bleScaleService != null;
    }

    // ============================================================================
    // CONVENIENCE METHODS
    // ============================================================================

    public void connectToDevice(BleDevice device) {
        connectToDevice(device.getAddress(), device.getName());
    }

    public String getConnectionStatus() {
        if (!isServiceReady()) {
            return "Service not available";
        }

        if (bleScaleService.isConnecting()) {
            return "Connecting...";
        }

        if (bleScaleService.isConnected()) {
            return "Connected to " + bleScaleService.getConnectedDeviceName();
        }

        return "Not connected";
    }

    public String getDetailedStatus() {
        StringBuilder status = new StringBuilder();

        status.append("Service: ").append(isServiceBound ? "Connected" : "Disconnected").append("\n");

        if (isServiceReady()) {
            status.append("Scale: ");
            if (bleScaleService.isConnecting()) {
                status.append("Connecting...");
            } else if (bleScaleService.isConnected()) {
                status.append("Connected to ").append(bleScaleService.getConnectedDeviceName());
                status.append("\nWeight: ").append(bleScaleService.getCurrentWeight()).append(" kg");
                status.append(" (").append(bleScaleService.isWeightStable() ? "Stable" : "Unstable").append(")");
            } else {
                status.append("Not connected");
            }
        } else {
            status.append("Scale: Service unavailable");
        }

        return status.toString();
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    /**
     * Force restart the service connection
     * Useful for recovery from error states
     */
    public void restartService() {
        Log.d(TAG, "Restarting service connection");

        // Unbind and stop current service
        unbindService();
        stopService();

        // Wait a moment then restart
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            startAndBindService();
        }, 1000);
    }

    /**
     * Check if the manager is in a healthy state
     */
    public boolean isHealthy() {
        return isServiceBound && bleScaleService != null;
    }

    /**
     * Get diagnostic information for debugging
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== BLE Connection Manager Diagnostics ===\n");
        info.append("Service Started: ").append(isServiceStarted).append("\n");
        info.append("Service Bound: ").append(isServiceBound).append("\n");
        info.append("Service Instance: ").append(bleScaleService != null ? "Available" : "Null").append("\n");
        info.append("Listeners: ").append(listeners.size()).append("\n");

        if (isServiceReady()) {
            info.append("Scale Connected: ").append(bleScaleService.isConnected()).append("\n");
            info.append("Scale Connecting: ").append(bleScaleService.isConnecting()).append("\n");
            info.append("Device Name: ").append(bleScaleService.getConnectedDeviceName()).append("\n");
            info.append("Current Weight: ").append(bleScaleService.getCurrentWeight()).append(" kg\n");
            info.append("Weight Stable: ").append(bleScaleService.isWeightStable()).append("\n");
        }

        return info.toString();
    }

    // ============================================================================
    // PRIVATE NOTIFICATION METHODS
    // ============================================================================

    private void notifyManagerReady() {
        Log.d(TAG, "Notifying listeners that manager is ready");
        for (ConnectionManagerListener listener : listeners) {
            try {
                listener.onManagerReady();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener that manager is ready", e);
            }
        }
    }

    private void notifyManagerDisconnected() {
        Log.d(TAG, "Notifying listeners that manager is disconnected");
        for (ConnectionManagerListener listener : listeners) {
            try {
                listener.onManagerDisconnected();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener that manager is disconnected", e);
            }
        }
    }

    private void notifyError(String error) {
        Log.e(TAG, "Manager error: " + error);
        for (ConnectionManagerListener listener : listeners) {
            try {
                listener.onError(error);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener of error", e);
            }
        }
    }

    // ============================================================================
    // STATIC UTILITY METHODS
    // ============================================================================

    /**
     * Initialize the connection manager in Application class
     */
    public static void initializeInApplication(Context applicationContext) {
        Log.d(TAG, "Initializing BLE Connection Manager in Application");
        BleConnectionManager manager = getInstance(applicationContext);
        manager.initialize();
    }

    /**
     * Check if the manager has been initialized
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Get manager status without requiring initialization
     */
    public static String getManagerStatus() {
        if (instance == null) {
            return "Not initialized";
        }
        return instance.getConnectionStatus();
    }
}