package com.example.meruscrap;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enhanced BLE Scale Manager with support for multiple listeners
 * Manages connection to BLE-enabled scales and weight data reception
 */
public class BleScaleManager {
    private static final String TAG = "BleScaleManager";

    // BLE Constants
    private static final long SCAN_TIMEOUT = 10000; // 10 seconds
    private static final int RSSI_UPDATE_INTERVAL = 2000; // 2 seconds

    // Scale Service UUIDs (example - adjust for your specific scale)
    private static final UUID SCALE_SERVICE_UUID = UUID.fromString("0000181d-0000-1000-8000-00805f9b34fb");
    private static final UUID WEIGHT_CHARACTERISTIC_UUID = UUID.fromString("00002a9d-0000-1000-8000-00805f9b34fb");

    // Context and BLE components
    private Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Handler handler;

    // State tracking
    private boolean isScanning = false;
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private String connectedDeviceName = null;
    private double currentWeight = 0.0;
    private boolean isWeightStable = false;

    // Device tracking
    private List<BleDevice> scannedDevices = new ArrayList<>();

    // Listener support - primary and secondary listeners
    private BleScaleListener primaryListener;
    private List<BleScaleListener> secondaryListeners = new ArrayList<>();

    // Scan callback
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            handleScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                handleScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed with error: " + errorCode);
            isScanning = false;
            notifyError("Scan failed with error code: " + errorCode);
        }
    };

    // GATT callback
    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            handleConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            handleServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            handleWeightData(characteristic);
        }
    };

    // =================================================================
    // CONSTRUCTOR AND INITIALIZATION
    // =================================================================

    public BleScaleManager(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());

        initializeBluetooth();
    }

    private void initializeBluetooth() {
        // Check if BLE is supported
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "BLE not supported on this device");
            notifyError("BLE not supported on this device");
            return;
        }

        // Get Bluetooth manager and adapter
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e(TAG, "Unable to initialize BluetoothManager");
            notifyError("Unable to initialize Bluetooth");
            return;
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter");
            notifyError("Bluetooth not available");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth is not enabled");
            notifyError("Bluetooth is not enabled");
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "Unable to obtain BluetoothLeScanner");
            notifyError("BLE scanner not available");
            return;
        }

        Log.d(TAG, "BLE Scale Manager initialized successfully");
    }

    // =================================================================
    // LISTENER MANAGEMENT
    // =================================================================

    public void setListener(BleScaleListener listener) {
        this.primaryListener = listener;
    }

    public void addSecondaryListener(BleScaleListener listener) {
        if (listener != null && !secondaryListeners.contains(listener)) {
            secondaryListeners.add(listener);
        }
    }

    public void removeSecondaryListener(BleScaleListener listener) {
        secondaryListeners.remove(listener);
    }

    private void notifyAllListeners(ListenerAction action) {
        // Notify primary listener
        if (primaryListener != null) {
            action.execute(primaryListener);
        }

        // Notify secondary listeners
        for (BleScaleListener listener : secondaryListeners) {
            if (listener != null) {
                action.execute(listener);
            }
        }
    }

    // Functional interface for listener actions
    private interface ListenerAction {
        void execute(BleScaleListener listener);
    }

    // =================================================================
    // SCANNING METHODS
    // =================================================================

    public void startScan() {
        Log.d(TAG, "startScan() called");

        // Debug permissions first
        debugPermissionStatus();

        if (isScanning) {
            Log.w(TAG, "Already scanning");
            return;
        }

        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner not available");
            notifyError("Bluetooth scanner not available");
            return;
        }

        // Enhanced permission checking based on Android version
        boolean hasRequiredPermissions = false;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12+ permissions
            boolean hasScan = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
            boolean hasLocation = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;

            hasRequiredPermissions = hasScan && hasLocation;

            Log.d(TAG, "Android 12+ permission check - SCAN: " + hasScan + ", LOCATION: " + hasLocation);

            if (!hasRequiredPermissions) {
                String missingPerms = "";
                if (!hasScan) missingPerms += "BLUETOOTH_SCAN ";
                if (!hasLocation) missingPerms += "ACCESS_FINE_LOCATION ";
                Log.e(TAG, "Missing permissions: " + missingPerms);
                notifyError("Missing permissions: " + missingPerms);
                return;
            }
        } else {
            // Legacy permissions (Android 11 and below)
            boolean hasBluetooth = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH)
                    == PackageManager.PERMISSION_GRANTED;
            boolean hasBluetoothAdmin = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADMIN)
                    == PackageManager.PERMISSION_GRANTED;
            boolean hasLocation = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;

            hasRequiredPermissions = hasBluetooth && hasBluetoothAdmin && hasLocation;

            Log.d(TAG, "Legacy permission check - BLUETOOTH: " + hasBluetooth +
                    ", ADMIN: " + hasBluetoothAdmin + ", LOCATION: " + hasLocation);

            if (!hasRequiredPermissions) {
                String missingPerms = "";
                if (!hasBluetooth) missingPerms += "BLUETOOTH ";
                if (!hasBluetoothAdmin) missingPerms += "BLUETOOTH_ADMIN ";
                if (!hasLocation) missingPerms += "ACCESS_FINE_LOCATION ";
                Log.e(TAG, "Missing permissions: " + missingPerms);
                notifyError("Missing permissions: " + missingPerms);
                return;
            }
        }

        // Clear previous results
        scannedDevices.clear();

        // Start scanning
        try {
            bluetoothLeScanner.startScan(scanCallback);
            isScanning = true;

            Log.d(TAG, "Started BLE scan successfully");
            notifyAllListeners(BleScaleListener::onScanStarted);

            // Stop scan after timeout
            handler.postDelayed(this::stopScan, SCAN_TIMEOUT);

        } catch (Exception e) {
            Log.e(TAG, "Error starting BLE scan", e);
            isScanning = false;
            notifyError("Error starting scan: " + e.getMessage());
        }
    }

    public void stopScan() {
        if (!isScanning) {
            return;
        }

        if (bluetoothLeScanner != null) {
            try {
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN)
                        == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeScanner.stopScan(scanCallback);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping BLE scan", e);
            }
        }

        isScanning = false;
        Log.d(TAG, "Stopped BLE scan");
        notifyAllListeners(BleScaleListener::onScanStopped);
    }

    private void handleScanResult(ScanResult result) {
        BluetoothDevice device = result.getDevice();

        // Check permissions
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String deviceName = device.getName();
        String deviceAddress = device.getAddress();
        int rssi = result.getRssi();

        // Filter for scale devices (adjust criteria as needed)
        if (deviceName == null || deviceName.isEmpty()) {
            return; // Skip devices without names
        }

        // Check if this looks like a scale device
        String nameLower = deviceName.toLowerCase();
        if (!nameLower.contains("scale") && !nameLower.contains("weight") && !nameLower.contains("balance")) {
            // You might want to adjust this filtering logic based on your scale's advertising name
            // For testing, you might want to comment this out to see all devices
            return;
        }

        // Create or update BLE device
        BleDevice bleDevice = new BleDevice(deviceName, deviceAddress, rssi);

        // Check if we already have this device
        boolean deviceExists = false;
        for (int i = 0; i < scannedDevices.size(); i++) {
            if (scannedDevices.get(i).getAddress().equals(deviceAddress)) {
                // Update existing device
                scannedDevices.set(i, bleDevice);
                deviceExists = true;
                break;
            }
        }

        if (!deviceExists) {
            scannedDevices.add(bleDevice);
            Log.d(TAG, "Found new scale device: " + deviceName + " (" + deviceAddress + ") RSSI: " + rssi);
        }

        // Notify listeners
        final BleDevice finalDevice = bleDevice;
        notifyAllListeners(listener -> listener.onDeviceFound(finalDevice));
    }

    // =================================================================
    // CONNECTION METHODS
    // =================================================================

    public void connectToDevice(BleDevice device) {
        if (isConnecting || isConnected) {
            Log.w(TAG, "Already connecting or connected");
            return;
        }

        // Stop scanning if active
        if (isScanning) {
            stopScan();
        }

        // Check permissions
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted");
            notifyError("Bluetooth connect permission required");
            return;
        }

        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(device.getAddress());
        if (bluetoothDevice == null) {
            Log.e(TAG, "Unable to get remote device");
            notifyError("Unable to get device");
            return;
        }

        isConnecting = true;
        Log.d(TAG, "Connecting to device: " + device.getName());

        try {
            bluetoothGatt = bluetoothDevice.connectGatt(context, false, gattCallback);
            if (bluetoothGatt == null) {
                isConnecting = false;
                notifyError("Failed to create GATT connection");
            }
        } catch (Exception e) {
            isConnecting = false;
            Log.e(TAG, "Error connecting to device", e);
            notifyError("Connection error: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }

        isConnected = false;
        isConnecting = false;
        connectedDeviceName = null;
        currentWeight = 0.0;
        isWeightStable = false;
    }

    private void handleConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            Log.d(TAG, "Connected to GATT server");
            isConnecting = false;
            isConnected = true;

            // Get device name
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                connectedDeviceName = gatt.getDevice().getName();
                if (connectedDeviceName == null || connectedDeviceName.isEmpty()) {
                    connectedDeviceName = gatt.getDevice().getAddress();
                }
            }

            // Discover services
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                gatt.discoverServices();
            }

            // Notify listeners
            final String deviceName = connectedDeviceName;
            notifyAllListeners(listener -> listener.onDeviceConnected(deviceName));

        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
            Log.d(TAG, "Disconnected from GATT server");

            boolean wasConnected = isConnected;
            isConnected = false;
            isConnecting = false;
            connectedDeviceName = null;
            currentWeight = 0.0;
            isWeightStable = false;

            if (bluetoothGatt != null) {
                bluetoothGatt.close();
                bluetoothGatt = null;
            }

            if (wasConnected) {
                notifyAllListeners(BleScaleListener::onDeviceDisconnected);
            }
        }
    }

    private void handleServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Services discovered");
            enableWeightNotifications(gatt);
        } else {
            Log.w(TAG, "Service discovery failed with status: " + status);
            notifyError("Service discovery failed");
        }
    }

    private void enableWeightNotifications(BluetoothGatt gatt) {
        // This is a simplified implementation
        // You'll need to adjust this based on your specific scale's characteristics

        BluetoothGattService service = gatt.getService(SCALE_SERVICE_UUID);
        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(WEIGHT_CHARACTERISTIC_UUID);
            if (characteristic != null) {
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                    gatt.setCharacteristicNotification(characteristic, true);
                    Log.d(TAG, "Enabled weight notifications");
                }
            }
        }
    }

    // =================================================================
    // WEIGHT DATA HANDLING
    // =================================================================

    private void handleWeightData(BluetoothGattCharacteristic characteristic) {
        // Parse weight data from characteristic
        // This is a simplified implementation - adjust based on your scale's data format

        byte[] data = characteristic.getValue();
        if (data != null && data.length >= 4) {
            // Example: assume weight is sent as 4-byte float
            // You'll need to adjust this based on your scale's actual data format
            try {
                // Simple parsing example - adjust as needed
                int rawWeight = ((data[1] & 0xFF) << 8) | (data[0] & 0xFF);
                currentWeight = rawWeight / 100.0; // Assuming 2 decimal places

                // Determine stability (example logic)
                isWeightStable = (data[2] & 0x01) == 0x01;

                Log.d(TAG, "Weight received: " + currentWeight + " kg, stable: " + isWeightStable);

                // Notify listeners
                final double weight = currentWeight;
                final boolean stable = isWeightStable;
                notifyAllListeners(listener -> listener.onWeightReceived(weight, stable));

            } catch (Exception e) {
                Log.e(TAG, "Error parsing weight data", e);
            }
        }
    }

    public void tare() {
        // Send tare command to scale
        // This is a placeholder - implement based on your scale's protocol
        if (isConnected && bluetoothGatt != null) {
            Log.d(TAG, "Tare command sent (placeholder implementation)");
            // You would send the actual tare command here
        }
    }

    // =================================================================
    // UTILITY METHODS
    // =================================================================

    private void notifyError(String error) {
        Log.e(TAG, "Error: " + error);
        notifyAllListeners(listener -> listener.onError(error));
    }

    // =================================================================
    // PUBLIC GETTERS
    // =================================================================

    public boolean isScanning() {
        return isScanning;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isConnecting() {
        return isConnecting;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    public double getCurrentWeight() {
        return currentWeight;
    }

    public boolean isWeightStable() {
        return isWeightStable;
    }

    public List<BleDevice> getScannedDevices() {
        return new ArrayList<>(scannedDevices);
    }

    // =================================================================
    // CLEANUP
    // =================================================================

    public void cleanup() {
        Log.d(TAG, "Cleaning up BLE Scale Manager");

        // Stop scanning
        if (isScanning) {
            stopScan();
        }

        // Disconnect if connected
        if (isConnected || isConnecting) {
            disconnect();
        }

        // Remove handler callbacks
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        // Clear listeners
        primaryListener = null;
        secondaryListeners.clear();

        // Clear device list
        scannedDevices.clear();
    }
    private void debugPermissionStatus() {
        Log.d(TAG, "=== BleScaleManager PERMISSION DEBUG ===");
        Log.d(TAG, "Android SDK: " + android.os.Build.VERSION.SDK_INT);

        if (context == null) {
            Log.e(TAG, "Context is null!");
            return;
        }

        // Check all permissions that might be relevant
        String[] permissions = {
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
        };

        // Add Android 12+ permissions if available
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions = new String[] {
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE
            };
        }

        for (String permission : permissions) {
            int status = ActivityCompat.checkSelfPermission(context, permission);
            Log.d(TAG, permission + ": " +
                    (status == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        }

        Log.d(TAG, "Bluetooth adapter enabled: " + (bluetoothAdapter != null && bluetoothAdapter.isEnabled()));
        Log.d(TAG, "=== END BleScaleManager DEBUG ===");
    }
    // =================================================================
    // LISTENER INTERFACE
    // =================================================================

    public interface BleScaleListener {
        void onScanStarted();
        void onScanStopped();
        void onDeviceFound(BleDevice device);
        void onDeviceConnected(String deviceName);
        void onDeviceDisconnected();
        void onWeightReceived(double weight, boolean stable);
        void onError(String error);
    }
}