package com.example.meruscrap;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BleScaleViewModel extends ViewModel {
    private static final String TAG = "BleScaleViewModel";
    private static final long SCAN_PERIOD = 15000;
    private static final int CONNECTION_TIMEOUT = 10000;

    // Standard BLE Weight Scale Service UUIDs
    private static final UUID WEIGHT_SERVICE_UUID = UUID.fromString("0000ffc0-0000-1000-8000-00805f9b34fb");
    private static final UUID WEIGHT_MEASUREMENT_UUID = UUID.fromString("0000ffc2-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;
    private boolean isConnected = false;
    private boolean isConnecting = false;

    // Device Management
    private ArrayList<BleDevice> scannedDevices;
    private BleDevice selectedDevice;
    private BluetoothGattCharacteristic weightCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;

    // Weight tracking
    private double currentWeight = 0.0;
    private double lastStableWeight = 0.0;
    private boolean isStable = false;
    private float lastDisplayedWeight = -1;
    private long lastWeightUpdateTime = 0;
    private int consecutiveErrors = 0;
    private Handler stabilityHandler = new Handler(Looper.getMainLooper());
    private Runnable stabilityRunnable;
    private Runnable pollingRunnable;

    // LiveData for UI updates
    private MutableLiveData<List<BleDevice>> _scannedDevices = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<Boolean> _isScanning = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> _isConnected = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> _isConnecting = new MutableLiveData<>(false);
    private MutableLiveData<String> _connectionStatus = new MutableLiveData<>("Not connected");
    private MutableLiveData<Double> _currentWeight = new MutableLiveData<>(0.0);
    private MutableLiveData<Boolean> _weightStable = new MutableLiveData<>(false);
    private MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    private MutableLiveData<String> _connectedDeviceName = new MutableLiveData<>("");

    // Public LiveData getters
    public LiveData<List<BleDevice>> getScannedDevices() { return _scannedDevices; }
    public LiveData<Boolean> getIsScanning() { return _isScanning; }
    public LiveData<Boolean> getIsConnected() { return _isConnected; }
    public LiveData<Boolean> getIsConnecting() { return _isConnecting; }
    public LiveData<String> getConnectionStatus() { return _connectionStatus; }
    public LiveData<Double> getCurrentWeight() { return _currentWeight; }
    public LiveData<Boolean> getWeightStable() { return _weightStable; }
    public LiveData<String> getErrorMessage() { return _errorMessage; }
    public LiveData<String> getConnectedDeviceName() { return _connectedDeviceName; }

    // ✅ ADD THIS MISSING FIELD:
    private final MutableLiveData<String> _serviceStatus = new MutableLiveData<>("Initializing...");

    // ✅ ADD THIS MISSING GETTER:
    public LiveData<String> getServiceStatus() {
        return _serviceStatus;
    }

    // ✅ ADD THIS VALUE GETTER (for direct access):
    public String getServiceStatusValue() {
        return _serviceStatus.getValue();
    }

    public void initialize(Context context) {
        this.context = context.getApplicationContext();
        this.scannedDevices = new ArrayList<>();

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                Log.d(TAG, "Bluetooth initialized successfully");
            }
        }
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }



    public boolean checkPermissions() {
        if (context == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public void startScan() {
        if (!checkPermissions()) {
            _errorMessage.setValue("Missing Bluetooth permissions");
            return;
        }

        if (!isBluetoothEnabled()) {
            _errorMessage.setValue("Bluetooth is not enabled");
            return;
        }

        scannedDevices.clear();
        _scannedDevices.setValue(new ArrayList<>(scannedDevices));

        isScanning = true;
        _isScanning.setValue(true);
        _connectionStatus.setValue("Scanning for devices...");

        try {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            bluetoothLeScanner.startScan(scanCallback);
            Log.d(TAG, "Scan started");
        } catch (SecurityException e) {
            Log.e(TAG, "Permission error: " + e.getMessage());
            stopScan();
            _errorMessage.setValue("Permission error: " + e.getMessage());
        }

        handler.postDelayed(() -> {
            if (isScanning) stopScan();
        }, SCAN_PERIOD);
    }

    public void stopScan() {
        if (bluetoothLeScanner != null && isScanning) {
            try {
                if (checkPermissions()) {
                    bluetoothLeScanner.stopScan(scanCallback);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Error stopping scan: " + e.getMessage());
            }

            isScanning = false;
            _isScanning.setValue(false);
            _connectionStatus.setValue("Found " + scannedDevices.size() + " devices");
        }
    }

    // 1. CRITICAL FIX: Improved device filtering for industrial scales
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null) {
                try {
                    String deviceName = checkPermissions() ? device.getName() : "Unknown Device";
                    String deviceAddress = device.getAddress();
                    int rssi = result.getRssi();

                    // PRODUCTION FIX: Filter for scale devices more intelligently
                    if (isScaleDevice(deviceName, deviceAddress, result)) {
                        // Check if device already exists in list
                        boolean deviceExists = false;
                        for (BleDevice existingDevice : scannedDevices) {
                            if (existingDevice.getAddress().equals(deviceAddress)) {
                                deviceExists = true;
                                // Update RSSI if device already exists (closer/better signal)
                                if (rssi > existingDevice.getRssi()) {
                                    existingDevice.updateRssi(rssi);
                                    handler.post(() -> _scannedDevices.setValue(new ArrayList<>(scannedDevices)));
                                }
                                break;
                            }
                        }

                        if (!deviceExists) {
                            BleDevice scaleDevice = new BleDevice(deviceAddress, deviceName, rssi);
                            scannedDevices.add(scaleDevice);

                            Log.d(TAG, "Found potential scale device: " + deviceName + " (" + deviceAddress + ") RSSI: " + rssi);

                            // Update LiveData on main thread
                            handler.post(() -> _scannedDevices.setValue(new ArrayList<>(scannedDevices)));
                        }
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Permission error getting device info: " + e.getMessage());
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed: " + errorCode);
            handler.post(() -> {
                isScanning = false;
                _isScanning.setValue(false);
                String errorMsg = getScanErrorMessage(errorCode);
                _errorMessage.setValue("Scan failed: " + errorMsg);
            });
        }
    };

    public void connectToDevice(BleDevice device) {
        if (!checkPermissions()) {
            _errorMessage.setValue("Missing Bluetooth permissions");
            return;
        }

        // Find the actual BluetoothDevice
        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(device.getAddress());

        selectedDevice = device;
        isConnecting = true;
        _isConnecting.setValue(true);
        _connectionStatus.setValue("Connecting to " + device.getName() + "...");

        Log.d(TAG, "Connecting to device: " + device.getName());

        try {
            bluetoothGatt = bluetoothDevice.connectGatt(context, false, gattCallback);

            handler.postDelayed(() -> {
                if (isConnecting && !isConnected) {
                    Log.w(TAG, "Connection timeout");
                    disconnect();
                    handler.post(() -> {
                        _errorMessage.setValue("Connection timeout");
                        _isConnecting.setValue(false);
                    });
                }
            }, CONNECTION_TIMEOUT);
        } catch (SecurityException e) {
            Log.e(TAG, "Permission error during connection: " + e.getMessage());
            isConnecting = false;
            _isConnecting.setValue(false);
            _errorMessage.setValue("Permission error - cannot connect");
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during connection: " + e.getMessage());
            isConnecting = false;
            _isConnecting.setValue(false);
            _errorMessage.setValue("Connection failed");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            handler.post(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true;
                    isConnecting = false;
                    _isConnected.setValue(true);
                    _isConnecting.setValue(false);
                    _connectedDeviceName.setValue(selectedDevice != null ? selectedDevice.getName() : "Unknown Device");
                    Log.d(TAG, "Connected to device");
                    _connectionStatus.setValue("Connected - discovering services...");

                    try {
                        if (checkPermissions()) {
                            boolean discoveryStarted = gatt.discoverServices();
                            Log.d(TAG, "Service discovery started: " + discoveryStarted);
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "Permission error during service discovery: " + e.getMessage());
                        _connectionStatus.setValue("Connected - permission error");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected = false;
                    isConnecting = false;
                    weightCharacteristic = null;
                    writeCharacteristic = null;
                    _isConnected.setValue(false);
                    _isConnecting.setValue(false);
                    _connectedDeviceName.setValue("");
                    Log.d(TAG, "Disconnected from device");
                    _connectionStatus.setValue("Disconnected");
                    _currentWeight.setValue(0.0);
                    _weightStable.setValue(false);
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully");

                // Log all available services and characteristics
                logAllCharacteristics(gatt);

                BluetoothGattService weightService = gatt.getService(WEIGHT_SERVICE_UUID);
                if (weightService != null) {
                    Log.d(TAG, "Weight Scale Service found!");

                    // Find notification characteristic
                    weightCharacteristic = weightService.getCharacteristic(WEIGHT_MEASUREMENT_UUID);
                    if (weightCharacteristic != null) {
                        Log.d(TAG, "Weight Measurement Characteristic found!");
                        int properties = weightCharacteristic.getProperties();
                        Log.d(TAG, "Weight char properties: " + getPropertiesString(properties));

                        setupWeightNotifications(gatt, weightCharacteristic);
                    } else {
                        Log.w(TAG, "Weight Measurement Characteristic not found");
                    }

                    // Find write characteristic
                    findWriteCharacteristic(gatt, weightService);

                } else {
                    Log.w(TAG, "Weight Scale Service not found - searching alternatives");
                    findAlternativeWeightCharacteristic(gatt);
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: " + status);
                handler.post(() -> _connectionStatus.setValue("Service discovery failed"));
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                StringBuilder hex = new StringBuilder();
                for (byte b : data) {
                    hex.append(String.format("%02X ", b));
                }
                Log.d(TAG, "🎉 DATA RECEIVED from " + characteristic.getUuid());
                Log.d(TAG, "    Raw Hex: " + hex.toString());
                Log.d(TAG, "    Raw Decimal: " + Arrays.toString(data));

                float weight = parseWeightData(data);
                if (weight >= 0) {
                    handler.post(() -> updateWeightDisplay(weight));
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    StringBuilder hex = new StringBuilder();
                    for (byte b : data) {
                        hex.append(String.format("%02X ", b));
                    }
                    Log.d(TAG, "📖 DATA RECEIVED (read) from " + characteristic.getUuid());
                    Log.d(TAG, "    Raw Hex: " + hex.toString());
                    Log.d(TAG, "    Raw Decimal: " + Arrays.toString(data));

                    float weight = parseWeightData(data);
                    if (weight >= 0) {
                        handler.post(() -> updateWeightDisplay(weight));
                    }
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful - notifications enabled");
                handler.post(() -> _connectionStatus.setValue("Ready for weight readings"));

                // Subscribe to alternative notification characteristics
                subscribeToAlternativeCharacteristics(gatt);

                // Try to activate the scale
                if (weightCharacteristic != null || writeCharacteristic != null) {
                    activateScale(gatt);
                }
            } else {
                Log.e(TAG, "Descriptor write failed with status: " + status);
                handler.post(() -> _connectionStatus.setValue("Failed to enable notifications"));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String uuid = characteristic.getUuid().toString();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "✅ WRITE SUCCESS to " + uuid + " - scale accepted the data!");
                handler.post(() -> _connectionStatus.setValue("✅ Write successful"));
            } else {
                Log.e(TAG, "❌ WRITE FAILED to " + uuid + " - Status: " + status);
                handler.post(() -> _connectionStatus.setValue("❌ Write failed"));
            }
        }
    };

    private void updateWeightDisplay(float weight) {
        if (weight > 0) {
            // Check if this is a reasonable weight change or if it's stable
            if (Math.abs(weight - lastDisplayedWeight) < 0.1 &&
                    System.currentTimeMillis() - lastWeightUpdateTime < 1000) {
                // Skip very small changes that happen too quickly (noise)
                return;
            }

            lastDisplayedWeight = weight;
            lastWeightUpdateTime = System.currentTimeMillis();
            currentWeight = weight;

            _currentWeight.setValue((double) weight);
            checkWeightStability(weight);

            Log.d(TAG, "✓ Weight: " + String.format("%.2f kg", weight));
        } else {
            // Don't immediately show error - wait for multiple failures
            consecutiveErrors++;
            if (consecutiveErrors >= 3) {
                _errorMessage.setValue("⚠ Error reading weight");
                Log.d(TAG, "⚠ Error reading weight");
            }
        }
    }

    private void checkWeightStability(float weight) {
        if (stabilityRunnable != null) {
            stabilityHandler.removeCallbacks(stabilityRunnable);
        }

        _weightStable.setValue(false);
        isStable = false;

        stabilityRunnable = () -> {
            _weightStable.setValue(true);
            isStable = true;
            lastStableWeight = weight;
        };

        stabilityHandler.postDelayed(stabilityRunnable, 2000); // 2 seconds for stability
    }

    // Helper methods from original MainActivity
    private void logAllCharacteristics(BluetoothGatt gatt) {
        Log.d(TAG, "=== DISCOVERING ALL CHARACTERISTICS ===");
        for (BluetoothGattService service : gatt.getServices()) {
            Log.d(TAG, "Service: " + service.getUuid().toString());
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                int properties = characteristic.getProperties();
                Log.d(TAG, "  Char: " + characteristic.getUuid().toString() + " | " + getPropertiesString(properties));
            }
        }
        Log.d(TAG, "=== END CHARACTERISTIC DISCOVERY ===");
    }

    private String getPropertiesString(int properties) {
        StringBuilder props = new StringBuilder();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0) props.append("READ ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) props.append("WRITE ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) props.append("WRITE_NO_RESPONSE ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) props.append("NOTIFY ");
        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) props.append("INDICATE ");
        return props.toString().trim();
    }

    private void findWriteCharacteristic(BluetoothGatt gatt, BluetoothGattService service) {
        // Common write characteristic UUIDs for scales
        String[] writeCharUUIDs = {
                "0000ffc1-0000-1000-8000-00805f9b34fb",
                "0000ffc3-0000-1000-8000-00805f9b34fb",
                "0000ff91-0000-1000-8000-00805f9b34fb",
                "0000ffe1-0000-1000-8000-00805f9b34fb"
        };

        for (String charUuid : writeCharUUIDs) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuid));
            if (characteristic != null) {
                int properties = characteristic.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    writeCharacteristic = characteristic;
                    Log.d(TAG, "Found write characteristic: " + charUuid + " | " + getPropertiesString(properties));
                    return;
                }
            }
        }

        // If no specific write char found, look for any writable characteristic in the service
        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
            int properties = characteristic.getProperties();
            if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                writeCharacteristic = characteristic;
                Log.d(TAG, "Found generic write characteristic: " + characteristic.getUuid().toString() + " | " + getPropertiesString(properties));
                return;
            }
        }

        Log.w(TAG, "No write characteristic found in weight service");
    }

    private void setupWeightNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        try {
            if (!checkPermissions()) {
                Log.e(TAG, "Missing permissions for notifications");
                return;
            }

            boolean notificationSet = gatt.setCharacteristicNotification(characteristic, true);
            Log.d(TAG, "Notification set: " + notificationSet);

            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                boolean writeResult = gatt.writeDescriptor(descriptor);
                Log.d(TAG, "Descriptor write initiated: " + writeResult);
                handler.post(() -> _connectionStatus.setValue("Subscribed to weight notifications"));
            } else {
                Log.w(TAG, "Notification descriptor not found");
                handler.post(() -> _connectionStatus.setValue("Connected - notifications not available"));
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission error during notification setup: " + e.getMessage());
            handler.post(() -> _connectionStatus.setValue("Connected - permission error"));
        }
    }

    private void findAlternativeWeightCharacteristic(BluetoothGatt gatt) {
        try {
            if (!checkPermissions()) {
                Log.e(TAG, "Missing permissions for alternative search");
                return;
            }

            String[] scaleServiceUUIDs = {
                    "0000ffc0-0000-1000-8000-00805f9b34fb",
                    "0000ff90-0000-1000-8000-00805f9b34fb",
                    "0000ffe0-0000-1000-8000-00805f9b34fb"
            };

            String[] scaleCharUUIDs = {
                    "0000ffc1-0000-1000-8000-00805f9b34fb",
                    "0000ffc2-0000-1000-8000-00805f9b34fb",
                    "0000ff91-0000-1000-8000-00805f9b34fb",
                    "0000ffe4-0000-1000-8000-00805f9b34fb"
            };

            boolean foundWeightChar = false;

            for (String serviceUuid : scaleServiceUUIDs) {
                BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
                if (service != null) {
                    Log.d(TAG, "Found potential scale service: " + serviceUuid);
                    for (String charUuid : scaleCharUUIDs) {
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuid));
                        if (characteristic != null) {
                            Log.d(TAG, "Found scale characteristic: " + charUuid);
                            if (trySubscribeToCharacteristic(gatt, characteristic)) {
                                foundWeightChar = true;
                                break;
                            }
                        }
                    }
                    if (foundWeightChar) break;
                }
            }

            if (!foundWeightChar) {
                handler.post(() -> _connectionStatus.setValue("Connected - no weight characteristics found"));
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission error during alternative search: " + e.getMessage());
        }
    }

    private boolean trySubscribeToCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        try {
            int properties = characteristic.getProperties();
            if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                boolean notificationSet = gatt.setCharacteristicNotification(characteristic, true);
                if (notificationSet) {
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        boolean writeResult = gatt.writeDescriptor(descriptor);
                        Log.d(TAG, "Notification setup for " + characteristic.getUuid().toString() + ": " + writeResult);
                        if (writeResult) {
                            weightCharacteristic = characteristic;
                            handler.post(() -> _connectionStatus.setValue("Subscribed to: " + characteristic.getUuid().toString()));
                            return true;
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission error subscribing to characteristic: " + e.getMessage());
        }
        return false;
    }

    private void subscribeToAlternativeCharacteristics(BluetoothGatt gatt) {
        try {
            if (!checkPermissions()) {
                Log.w(TAG, "Cannot subscribe to alternative characteristics - missing permissions");
                return;
            }

            Log.d(TAG, "🔔 Subscribing to ALL notification characteristics for maximum coverage...");

            // Alternative notification characteristics your scale has
            String[] altNotificationUUIDs = {
                    "0000ffe4-0000-1000-8000-00805f9b34fb", // From ffe0 service
                    "5833ff03-9b8b-5191-6142-22a4536ef123"  // Custom service
            };

            for (String uuid : altNotificationUUIDs) {
                try {
                    for (BluetoothGattService service : gatt.getServices()) {
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(uuid));
                        if (characteristic != null) {
                            int properties = characteristic.getProperties();
                            if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                Log.d(TAG, "🔔 Subscribing to alternative notification: " + uuid);

                                boolean notificationSet = gatt.setCharacteristicNotification(characteristic, true);
                                if (notificationSet) {
                                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                                    if (descriptor != null) {
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                        boolean writeResult = gatt.writeDescriptor(descriptor);
                                        Log.d(TAG, "🔔 Alternative notification setup: " + writeResult + " for " + uuid);

                                        // Small delay before next subscription
                                        Thread.sleep(200);
                                    }
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    Log.e(TAG, "Error subscribing to " + uuid + ": " + e.getMessage());
                }
            }

            Log.d(TAG, "🔔 Alternative notification subscription completed");

        } catch (SecurityException e) {
            Log.e(TAG, "Permission error during alternative subscription: " + e.getMessage());
        }
    }

    // FIX 2: Add permission checks to writeCharacteristic calls in activateScale method
    private void activateScale(BluetoothGatt gatt) {
        if (!checkPermissions()) {
            Log.w(TAG, "Cannot activate scale - missing permissions");
            return;
        }

        try {
            Log.d(TAG, "🚀 Attempting to activate scale (optimized)...");

            BluetoothGattCharacteristic commandChar = writeCharacteristic != null ? writeCharacteristic : weightCharacteristic;

            if (commandChar == null) {
                Log.w(TAG, "No characteristic available for sending commands");
                handler.post(() -> _connectionStatus.setValue("No command characteristic found"));
                return;
            }

            // REDUCED activation commands - only the essential ones
            byte[][] activationCommands = {
                    {(byte)0x05},                    // Start command that works
                    {(byte)0x04}                     // Request weight that works
            };

            // Send commands with PROPER DELAYS on background thread
            new Thread(() -> {
                try {
                    for (int i = 0; i < activationCommands.length; i++) {
                        final byte[] command = activationCommands[i];
                        final int commandIndex = i + 1;

                        // Send command on main thread (required for BLE)
                        handler.post(() -> {
                            try {
                                // ✅ FIX: Add permission check before writeCharacteristic
                                if (!checkPermissions()) {
                                    Log.e(TAG, "Missing permissions for write command " + commandIndex);
                                    return;
                                }

                                commandChar.setValue(command);
                                int properties = commandChar.getProperties();
                                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                                    commandChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                                } else {
                                    commandChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                                }

                                // ✅ FIX: Permission-safe writeCharacteristic call
                                boolean success = gatt.writeCharacteristic(commandChar);

                                StringBuilder hex = new StringBuilder();
                                for (byte b : command) {
                                    hex.append(String.format("%02X ", b));
                                }
                                Log.d(TAG, "Activation command " + commandIndex + " sent: " + success + " (hex: " + hex.toString().trim() + ")");

                            } catch (SecurityException e) {
                                // ✅ FIX: Handle SecurityException properly
                                Log.e(TAG, "Permission denied for activation command " + commandIndex + ": " + e.getMessage());
                                _errorMessage.setValue("Bluetooth permission denied");
                            } catch (Exception e) {
                                Log.e(TAG, "Error sending activation command " + commandIndex + ": " + e.getMessage());
                            }
                        });

                        // Wait between commands (OFF main thread)
                        Thread.sleep(2000); // 2 seconds between commands
                    }

                    // Update UI after activation sequence
                    handler.post(() -> {
                        _connectionStatus.setValue("🎉 Scale activated! Step on scale for readings");

                        // Start gentle polling after activation
                        startOptimizedPolling(gatt);
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "Activation sequence interrupted");
                } catch (Exception e) {
                    Log.e(TAG, "Error in activation sequence: " + e.getMessage());
                    handler.post(() -> _connectionStatus.setValue("Error activating scale"));
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error in activateScale: " + e.getMessage());
            handler.post(() -> _connectionStatus.setValue("Error activating scale"));
        }
    }

    private void startOptimizedPolling(BluetoothGatt gatt) {
        // Cancel any existing polling
        handler.removeCallbacks(pollingRunnable);

        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isConnected && weightCharacteristic != null && checkPermissions()) {
                    try {
                        // Only poll occasionally - the scale sends data automatically
                        boolean readResult = gatt.readCharacteristic(weightCharacteristic);
                        Log.d(TAG, "Gentle polling: " + readResult);

                        if (isConnected) {
                            // Poll every 10 seconds instead of 3 seconds
                            handler.postDelayed(this, 10000);
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "Permission error during polling: " + e.getMessage());
                    } catch (Exception e) {
                        Log.e(TAG, "Error during polling: " + e.getMessage());
                    }
                }
            }
        };

        // Start polling after 5 seconds
        handler.postDelayed(pollingRunnable, 5000);
    }

    // 4. PRODUCTION FIX: Enhanced weight data parsing for industrial scales
    private float parseWeightData(byte[] data) {
        if (data == null || data.length < 1) {
            Log.w(TAG, "Invalid weight data received");
            return -1;
        }

        Log.d(TAG, "🔍 Parsing weight data: " + Arrays.toString(data));

        try {
            // Skip control characters and acknowledgments
            if (data.length <= 2) {
                boolean isControlChars = true;
                for (byte b : data) {
                    int value = b & 0xFF;
                    if (value != 0x0D && value != 0x0A && value != 0x00 && value > 5) {
                        isControlChars = false;
                        break;
                    }
                }
                if (isControlChars) {
                    Log.d(TAG, "📋 Skipping control characters");
                    return -1;
                }
            }

            // METHOD 1: ASCII text parsing (most common for industrial scales)
            String asciiString = new String(data, StandardCharsets.UTF_8).trim();
            Log.d(TAG, "🔍 ASCII interpretation: '" + asciiString + "'");

            if (asciiString.length() > 3) {
                // Pattern 1: "ST,GS,+   12.34KG" or similar Toledo/Mettler formats
                Pattern pattern1 = Pattern.compile("([+-]?\\s*\\d+\\.?\\d*)\\s*KG", Pattern.CASE_INSENSITIVE);
                Matcher matcher1 = pattern1.matcher(asciiString);

                if (matcher1.find()) {
                    String weightStr = matcher1.group(1).replaceAll("\\s+", "");
                    try {
                        float weight = Float.parseFloat(weightStr);
                        if (Math.abs(weight) < 1000) { // Reasonable range
                            Log.d(TAG, "✅ Parsed weight (Toledo/Mettler format): " + weight + " kg");
                            consecutiveErrors = 0;
                            return Math.abs(weight); // Always return positive weight
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Failed to parse weight: " + weightStr);
                    }
                }

                // Pattern 2: Simple decimal with units "12.34 kg" or "12.34kg"
                Pattern pattern2 = Pattern.compile("([+-]?\\d+\\.?\\d*)\\s*(?:kg|g|lb)", Pattern.CASE_INSENSITIVE);
                Matcher matcher2 = pattern2.matcher(asciiString);

                if (matcher2.find()) {
                    try {
                        float weight = Float.parseFloat(matcher2.group(1));
                        String unit = matcher2.group(0).toLowerCase();

                        // Convert to kg if needed
                        if (unit.contains("g") && !unit.contains("kg")) {
                            weight = weight / 1000f; // grams to kg
                        } else if (unit.contains("lb")) {
                            weight = weight * 0.453592f; // pounds to kg
                        }

                        if (weight > 0.01 && weight < 1000) { // Reasonable range
                            Log.d(TAG, "✅ Parsed weight (simple format): " + weight + " kg");
                            consecutiveErrors = 0;
                            return weight;
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Failed to parse simple weight format");
                    }
                }

                // Pattern 3: Just numbers "  12.34  " (some basic scales)
                if (asciiString.matches("\\s*\\d+\\.?\\d*\\s*")) {
                    try {
                        float weight = Float.parseFloat(asciiString.trim());
                        if (weight > 0.01 && weight < 1000) {
                            Log.d(TAG, "✅ Parsed weight (numeric only): " + weight + " kg");
                            consecutiveErrors = 0;
                            return weight;
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Failed to parse numeric weight");
                    }
                }
            }

            // METHOD 2: Binary data parsing (BLE Weight Scale Service standard)
            if (data.length >= 3) {
                // Standard BLE Weight Scale format
                int flags = data[0] & 0xFF;
                boolean isImperial = (flags & 0x01) != 0;
                boolean timestampPresent = (flags & 0x02) != 0;
                boolean userIdPresent = (flags & 0x04) != 0;
                boolean bmiPresent = (flags & 0x08) != 0;

                int offset = 1;
                if (data.length >= offset + 2) {
                    int weightRaw = ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
                    float weight = weightRaw * 0.005f; // Standard resolution

                    if (isImperial) {
                        weight = weight * 0.453592f; // Convert pounds to kg
                    }

                    if (weight > 0.01 && weight < 1000) {
                        Log.d(TAG, "✅ Parsed weight (BLE standard): " + weight + " kg");
                        consecutiveErrors = 0;
                        return weight;
                    }
                }
            }

            // METHOD 3: Custom binary formats (for specific scale brands)
            if (data.length >= 4) {
                // Some scales send weight as 4-byte float
                ByteBuffer buffer = ByteBuffer.wrap(data);
                try {
                    // Try little-endian float
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    float weight = buffer.getFloat();

                    if (!Float.isNaN(weight) && !Float.isInfinite(weight) &&
                            weight > 0.01 && weight < 1000) {
                        Log.d(TAG, "✅ Parsed weight (float LE): " + weight + " kg");
                        consecutiveErrors = 0;
                        return weight;
                    }

                    // Try big-endian float
                    buffer.rewind();
                    buffer.order(ByteOrder.BIG_ENDIAN);
                    weight = buffer.getFloat();

                    if (!Float.isNaN(weight) && !Float.isInfinite(weight) &&
                            weight > 0.01 && weight < 1000) {
                        Log.d(TAG, "✅ Parsed weight (float BE): " + weight + " kg");
                        consecutiveErrors = 0;
                        return weight;
                    }
                } catch (Exception e) {
                    // Continue to other methods
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing weight data: " + e.getMessage());
        }

        Log.d(TAG, "⚪ Could not parse weight from data");
        return -1; // Could not parse
    }

    public void tare() {
        currentWeight = 0.0;
        lastStableWeight = 0.0;
        isStable = false;
        _currentWeight.setValue(0.0);
        _weightStable.setValue(false);

        // In real implementation, send tare command to scale
        if (bluetoothGatt != null && writeCharacteristic != null && checkPermissions()) {
            try {
                byte[] tareCommand = {(byte)0x54}; // 'T' for tare
                writeCharacteristic.setValue(tareCommand);
                bluetoothGatt.writeCharacteristic(writeCharacteristic);
                Log.d(TAG, "Tare command sent");
            } catch (SecurityException e) {
                Log.e(TAG, "Permission error during tare: " + e.getMessage());
            }
        }
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            try {
                if (checkPermissions()) {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission error during disconnect: " + e.getMessage());
            }
            bluetoothGatt = null;
        }
        isConnected = false;
        isConnecting = false;
        _isConnected.setValue(false);
        _isConnecting.setValue(false);
        _connectedDeviceName.setValue("");
        _connectionStatus.setValue("Disconnected");
    }

    // Public getters for current values
    public double getCurrentWeightValue() { return currentWeight; }
    public double getLastStableWeightValue() { return lastStableWeight; }
    public boolean isWeightStable() { return isStable; }
    public boolean isConnectedValue() { return isConnected; }
    public boolean isConnectingValue() { return isConnecting; }
    public boolean isScanningValue() { return isScanning; }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Clean up resources
        if (handler != null) {
            handler.removeCallbacks(pollingRunnable);
            handler.removeCallbacksAndMessages(null);
        }
        if (stabilityHandler != null) {
            stabilityHandler.removeCallbacks(stabilityRunnable);
            stabilityHandler.removeCallbacksAndMessages(null);
        }
        disconnect();
    }
    // 2. PRODUCTION FIX: Better scale device detection
    private boolean isScaleDevice(String deviceName, String deviceAddress, ScanResult result) {
        // Check device name patterns for common industrial scale manufacturers
        if (deviceName != null) {
            String name = deviceName.toLowerCase();

            // Common scale device name patterns
            if (name.contains("scale") || name.contains("weight") || name.contains("balance") ||
                    name.contains("kern") || name.contains("mettler") || name.contains("ohaus") ||
                    name.contains("sartorius") || name.contains("adam") || name.contains("and") ||
                    name.contains("cas") || name.contains("digi") || name.startsWith("ws") ||
                    name.startsWith("lb") || name.contains("precision")) {
                return true;
            }
        }

        // Check for scale service UUIDs in advertisement data
        if (result.getScanRecord() != null) {
            List<ParcelUuid> serviceUuids = result.getScanRecord().getServiceUuids();
            if (serviceUuids != null) {
                for (ParcelUuid serviceUuid : serviceUuids) {
                    String uuidString = serviceUuid.toString().toLowerCase();
                    // Common scale service UUIDs
                    if (uuidString.contains("ffc0") || uuidString.contains("ff90") ||
                            uuidString.contains("ffe0") || uuidString.contains("181d")) {
                        return true;
                    }
                }
            }
        }

        // If no clear identification, include devices with strong signal (likely nearby scales)
        return result.getRssi() > -70;
    }

    // 3. PRODUCTION FIX: Better error messages
    private String getScanErrorMessage(int errorCode) {
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                return "Already scanning";
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                return "App registration failed";
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                return "Bluetooth LE not supported";
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                return "Internal bluetooth error";
            case ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES:
                return "Hardware resources unavailable";
            case ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY:
                return "Scanning too frequently - please wait";
            default:
                return "Unknown error (" + errorCode + ")";
        }
    }

    // 5. PRODUCTION FIX: Connection persistence across app lifecycle
    public void maintainConnection() {
        if (isConnected && bluetoothGatt != null) {
            // Periodically check connection health
            handler.postDelayed(() -> {
                if (isConnected && bluetoothGatt != null) {
                    // Try to read a characteristic to verify connection
                    if (weightCharacteristic != null && checkPermissions()) {
                        try {
                            boolean readResult = bluetoothGatt.readCharacteristic(weightCharacteristic);
                            Log.d(TAG, "Connection health check: " + readResult);
                        } catch (SecurityException e) {
                            Log.e(TAG, "Permission error during health check: " + e.getMessage());
                        }
                    }

                    // Schedule next check
                    if (isConnected) {
                        maintainConnection();
                    }
                }
            }, 30000); // Check every 30 seconds
        }
    }
    // 7. PRODUCTION FIX: Better error handling and recovery
    private void handleConnectionError() {
        consecutiveErrors++;

        if (consecutiveErrors > 5) {
            Log.w(TAG, "Multiple consecutive errors - attempting reconnection");

            // Attempt to reconnect
            if (selectedDevice != null) {
                handler.postDelayed(() -> {
                    if (!isConnected && !isConnecting) {
                        Log.d(TAG, "Auto-reconnecting to " + selectedDevice.getName());
                        connectToDevice(selectedDevice);
                    }
                }, 5000);
            }
        }
    }

    // Add this method to start connection health monitoring
    public void startConnectionHealthMonitoring() {
        if (isConnected) {
            maintainConnection();
        }
    }
}