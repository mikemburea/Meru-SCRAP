package com.example.meruscrap;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistent BLE Scale Service
 * Maintains connection across app lifecycle and provides reliable scale communication
 */
public class BleScaleService extends Service {
    private static final String TAG = "BleScaleService";

    // Service constants
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "ble_scale_service";
    private static final int FOREGROUND_SERVICE_TYPE = 0; // Connected device type

    // Connection management
    private static final int MAX_RECONNECTION_ATTEMPTS = 5;
    private static final long RECONNECTION_DELAY_MS = 3000; // 3 seconds
    private static final long CONNECTION_TIMEOUT_MS = 15000; // 15 seconds
    private static final long HEALTH_CHECK_INTERVAL_MS = 30000; // 30 seconds

    // BLE Scale Service UUIDs
    private static final UUID WEIGHT_SERVICE_UUID = UUID.fromString("0000ffc0-0000-1000-8000-00805f9b34fb");
    private static final UUID WEIGHT_MEASUREMENT_UUID = UUID.fromString("0000ffc2-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    // Service state
    private boolean isServiceRunning = false;
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private boolean shouldMaintainConnection = true;
    private int reconnectionAttempts = 0;

    // BLE components
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice connectedDevice;
    private BluetoothGattCharacteristic weightCharacteristic;

    // Data management
    private double currentWeight = 0.0;
    private boolean isWeightStable = false;
    private String connectedDeviceName = "";
    private long lastWeightUpdateTime = 0;

    // Handlers and runnables
    private Handler mainHandler;
    private Runnable reconnectionRunnable;
    private Runnable connectionTimeoutRunnable;
    private Runnable healthCheckRunnable;

    // Preferences for persistence
    private SharedPreferences servicePrefs;
    private static final String PREF_DEVICE_ADDRESS = "connected_device_address";
    private static final String PREF_DEVICE_NAME = "connected_device_name";

    // Listeners and callbacks
    private final List<BleScaleServiceListener> listeners = new CopyOnWriteArrayList<>();
    private NotificationManager notificationManager;

    // Service binder
    private final IBinder binder = new BleScaleServiceBinder();

    // Bluetooth state receiver
    private BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                handleBluetoothStateChange(state);
            }
        }
    };

    public interface BleScaleServiceListener {
        void onConnectionStateChanged(boolean isConnected, String deviceName);
        void onWeightReceived(double weight, boolean isStable);
        void onError(String error);
        void onServiceStatusChanged(String status);
    }

    public class BleScaleServiceBinder extends Binder {
        public BleScaleService getService() {
            return BleScaleService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BLE Scale Service Created");

        mainHandler = new Handler(Looper.getMainLooper());
        servicePrefs = getSharedPreferences("ble_scale_service", MODE_PRIVATE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        initializeBluetooth();
        createNotificationChannel();
        registerBluetoothStateReceiver();

        isServiceRunning = true;
        startHealthCheck();

        Log.d(TAG, "BLE Scale Service initialization completed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "BLE Scale Service Started");

        if (intent != null) {
            String action = intent.getAction();
            if ("CONNECT_TO_DEVICE".equals(action)) {
                String deviceAddress = intent.getStringExtra("device_address");
                String deviceName = intent.getStringExtra("device_name");
                if (deviceAddress != null) {
                    connectToDevice(deviceAddress, deviceName);
                }
            } else if ("DISCONNECT".equals(action)) {
                disconnect();
            }
        }

        // Start as foreground service
        Notification notification = createServiceNotification("BLE Scale Service Ready", "Waiting for connection...");
        startForeground(NOTIFICATION_ID, notification);

        // Return START_STICKY to restart service if killed
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "BLE Scale Service Destroyed");

        isServiceRunning = false;
        shouldMaintainConnection = false;

        // Cancel all pending operations
        cancelAllOperations();

        // Disconnect from device
        if (isConnected || isConnecting) {
            disconnectInternal();
        }

        // Unregister receivers
        try {
            unregisterReceiver(bluetoothStateReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Bluetooth receiver was not registered");
        }

        // Clear listeners
        listeners.clear();

        super.onDestroy();
    }

    // ============================================================================
    // BLUETOOTH INITIALIZATION AND MANAGEMENT
    // ============================================================================

    private void initializeBluetooth() {
        try {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                bluetoothAdapter = bluetoothManager.getAdapter();
                if (bluetoothAdapter == null) {
                    notifyError("Bluetooth not available on this device");
                    return;
                }

                if (!bluetoothAdapter.isEnabled()) {
                    notifyError("Bluetooth is not enabled");
                    return;
                }

                Log.d(TAG, "Bluetooth initialized successfully");
                notifyStatusChanged("Bluetooth initialized");

                // Try to auto-reconnect to last connected device
                autoReconnectToLastDevice();

            } else {
                notifyError("Unable to initialize Bluetooth Manager");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Bluetooth", e);
            notifyError("Bluetooth initialization failed: " + e.getMessage());
        }
    }

    private void registerBluetoothStateReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateReceiver, filter);
    }

    private void handleBluetoothStateChange(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_OFF:
                Log.w(TAG, "Bluetooth turned off");
                if (isConnected) {
                    disconnectInternal();
                }
                notifyError("Bluetooth turned off");
                updateNotification("Bluetooth Off", "Please enable Bluetooth");
                break;

            case BluetoothAdapter.STATE_ON:
                Log.d(TAG, "Bluetooth turned on");
                notifyStatusChanged("Bluetooth enabled");
                updateNotification("Bluetooth Enabled", "Ready for connection");

                // Try to reconnect if we were previously connected
                mainHandler.postDelayed(this::autoReconnectToLastDevice, 2000);
                break;

            case BluetoothAdapter.STATE_TURNING_OFF:
                Log.w(TAG, "Bluetooth turning off");
                shouldMaintainConnection = false;
                updateNotification("Bluetooth Turning Off", "Connection will be lost");
                break;

            case BluetoothAdapter.STATE_TURNING_ON:
                Log.d(TAG, "Bluetooth turning on");
                updateNotification("Bluetooth Starting", "Please wait...");
                break;
        }
    }

    // ============================================================================
    // CONNECTION MANAGEMENT
    // ============================================================================

    public void connectToDevice(String deviceAddress, String deviceName) {
        if (isConnecting || isConnected) {
            Log.w(TAG, "Already connecting or connected");
            return;
        }

        if (!checkBluetoothPermissions()) {
            notifyError("Bluetooth permissions required");
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            notifyError("Bluetooth not available or not enabled");
            return;
        }

        Log.d(TAG, "Connecting to device: " + deviceAddress);

        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            if (device == null) {
                notifyError("Unable to get remote device");
                return;
            }

            isConnecting = true;
            reconnectionAttempts = 0;
            connectedDeviceName = deviceName != null ? deviceName : "BLE Scale";

            notifyStatusChanged("Connecting to " + connectedDeviceName + "...");
            updateNotification("Connecting", "Connecting to " + connectedDeviceName);

            // Save device info for persistence
            saveConnectedDeviceInfo(deviceAddress, connectedDeviceName);

            // Start connection timeout
            startConnectionTimeout();

            // Connect to GATT server
            bluetoothGatt = device.connectGatt(this, false, gattCallback);

            if (bluetoothGatt == null) {
                handleConnectionFailure("Failed to create GATT connection");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error connecting to device", e);
            handleConnectionFailure("Connection error: " + e.getMessage());
        }
    }

    public void disconnect() {
        Log.d(TAG, "Manual disconnect requested");
        shouldMaintainConnection = false;
        clearSavedDeviceInfo();
        disconnectInternal();
    }

    private void disconnectInternal() {
        Log.d(TAG, "Disconnecting from device");

        // Cancel all pending operations
        cancelAllOperations();

        // Close GATT connection
        if (bluetoothGatt != null) {
            try {
                if (checkBluetoothPermissions()) {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during disconnection", e);
            }
            bluetoothGatt = null;
        }

        // Update state
        boolean wasConnected = isConnected;
        isConnected = false;
        isConnecting = false;
        connectedDevice = null;
        weightCharacteristic = null;
        currentWeight = 0.0;
        isWeightStable = false;

        // Notify listeners
        if (wasConnected) {
            notifyConnectionStateChanged(false, "");
        }

        updateNotification("Disconnected", "No device connected");
    }

    private void autoReconnectToLastDevice() {
        if (!shouldMaintainConnection || isConnected || isConnecting) {
            return;
        }

        String lastDeviceAddress = servicePrefs.getString(PREF_DEVICE_ADDRESS, null);
        String lastDeviceName = servicePrefs.getString(PREF_DEVICE_NAME, null);

        if (lastDeviceAddress != null) {
            Log.d(TAG, "Auto-reconnecting to last device: " + lastDeviceAddress);
            connectToDevice(lastDeviceAddress, lastDeviceName);
        }
    }

    private void scheduleReconnection() {
        if (!shouldMaintainConnection || reconnectionAttempts >= MAX_RECONNECTION_ATTEMPTS) {
            if (reconnectionAttempts >= MAX_RECONNECTION_ATTEMPTS) {
                notifyError("Max reconnection attempts reached");
                updateNotification("Connection Failed", "Max retry attempts reached");
            }
            return;
        }

        reconnectionAttempts++;
        long delay = RECONNECTION_DELAY_MS * reconnectionAttempts; // Exponential backoff

        Log.d(TAG, "Scheduling reconnection attempt " + reconnectionAttempts + " in " + delay + "ms");

        reconnectionRunnable = () -> {
            if (shouldMaintainConnection && !isConnected && !isConnecting) {
                notifyStatusChanged("Reconnection attempt " + reconnectionAttempts + "/" + MAX_RECONNECTION_ATTEMPTS);
                autoReconnectToLastDevice();
            }
        };

        mainHandler.postDelayed(reconnectionRunnable, delay);
    }

    // ============================================================================
    // GATT CALLBACKS
    // ============================================================================

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "Connection state changed: status=" + status + ", state=" + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server");

                cancelConnectionTimeout();
                isConnecting = false;
                isConnected = true;
                connectedDevice = gatt.getDevice();
                reconnectionAttempts = 0;

                // Get device name
                if (checkBluetoothPermissions()) {
                    try {
                        String deviceName = connectedDevice.getName();
                        connectedDeviceName = deviceName != null ? deviceName : connectedDeviceName;
                    } catch (SecurityException e) {
                        Log.w(TAG, "Cannot get device name due to permissions");
                    }
                }

                notifyConnectionStateChanged(true, connectedDeviceName);
                updateNotification("Connected", "Connected to " + connectedDeviceName);

                // Discover services
                if (checkBluetoothPermissions()) {
                    try {
                        boolean discoveryStarted = gatt.discoverServices();
                        Log.d(TAG, "Service discovery started: " + discoveryStarted);
                        if (!discoveryStarted) {
                            handleConnectionFailure("Failed to start service discovery");
                        }
                    } catch (SecurityException e) {
                        handleConnectionFailure("Permission error during service discovery");
                    }
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server");

                boolean wasConnected = isConnected;
                isConnected = false;
                isConnecting = false;
                connectedDevice = null;
                weightCharacteristic = null;

                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }

                if (wasConnected) {
                    notifyConnectionStateChanged(false, "");
                }

                // Schedule reconnection if we should maintain connection
                if (shouldMaintainConnection) {
                    notifyStatusChanged("Connection lost, attempting to reconnect...");
                    updateNotification("Reconnecting", "Attempting to reconnect...");
                    scheduleReconnection();
                } else {
                    updateNotification("Disconnected", "Manual disconnection");
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully");
                notifyStatusChanged("Services discovered, setting up notifications");

                // Find and setup weight characteristic
                BluetoothGattService weightService = gatt.getService(WEIGHT_SERVICE_UUID);
                if (weightService != null) {
                    weightCharacteristic = weightService.getCharacteristic(WEIGHT_MEASUREMENT_UUID);
                    if (weightCharacteristic != null) {
                        setupWeightNotifications(gatt, weightCharacteristic);
                    } else {
                        findAlternativeWeightCharacteristic(gatt);
                    }
                } else {
                    findAlternativeWeightCharacteristic(gatt);
                }

            } else {
                Log.e(TAG, "Service discovery failed with status: " + status);
                handleConnectionFailure("Service discovery failed");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                double weight = parseWeightData(data);
                if (weight >= 0) {
                    updateWeightData(weight);
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Notifications enabled successfully");
                notifyStatusChanged("Scale ready - notifications enabled");
                updateNotification("Scale Ready", connectedDeviceName + " is ready for weighing");

                // Try to activate the scale
                activateScale(gatt);
            } else {
                Log.e(TAG, "Failed to enable notifications: " + status);
                notifyError("Failed to enable weight notifications");
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful");
            } else {
                Log.w(TAG, "Characteristic write failed: " + status);
            }
        }
    };

    // ============================================================================
    // WEIGHT DATA PROCESSING
    // ============================================================================

    private void setupWeightNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        try {
            if (!checkBluetoothPermissions()) {
                notifyError("Missing Bluetooth permissions");
                return;
            }

            boolean notificationSet = gatt.setCharacteristicNotification(characteristic, true);
            Log.d(TAG, "Notification set: " + notificationSet);

            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                boolean writeResult = gatt.writeDescriptor(descriptor);
                Log.d(TAG, "Descriptor write initiated: " + writeResult);
            } else {
                Log.w(TAG, "Notification descriptor not found");
                notifyError("Scale does not support notifications");
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Permission error during notification setup", e);
            notifyError("Permission error setting up notifications");
        }
    }

    private void findAlternativeWeightCharacteristic(BluetoothGatt gatt) {
        // Implementation similar to your existing code
        // Try different service and characteristic UUIDs
        String[] serviceUUIDs = {
                "0000ff90-0000-1000-8000-00805f9b34fb",
                "0000ffe0-0000-1000-8000-00805f9b34fb"
        };

        String[] characteristicUUIDs = {
                "0000ffc1-0000-1000-8000-00805f9b34fb",
                "0000ff91-0000-1000-8000-00805f9b34fb",
                "0000ffe4-0000-1000-8000-00805f9b34fb"
        };

        for (String serviceUuid : serviceUUIDs) {
            BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
            if (service != null) {
                for (String charUuid : characteristicUUIDs) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuid));
                    if (characteristic != null) {
                        int properties = characteristic.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            weightCharacteristic = characteristic;
                            setupWeightNotifications(gatt, characteristic);
                            return;
                        }
                    }
                }
            }
        }

        notifyError("No compatible weight characteristic found");
    }

    private void activateScale(BluetoothGatt gatt) {
        // Send activation commands to wake up the scale
        new Thread(() -> {
            try {
                if (weightCharacteristic != null && checkBluetoothPermissions()) {
                    // Common activation commands
                    byte[][] commands = {
                            {(byte)0x05}, // Start command
                            {(byte)0x04}  // Request weight
                    };

                    for (byte[] command : commands) {
                        mainHandler.post(() -> {
                            try {
                                weightCharacteristic.setValue(command);
                                int properties = weightCharacteristic.getProperties();
                                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                                    weightCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                                }
                                boolean success = gatt.writeCharacteristic(weightCharacteristic);
                                Log.d(TAG, "Activation command sent: " + success);
                            } catch (SecurityException e) {
                                Log.e(TAG, "Permission error sending activation command", e);
                            }
                        });
                        Thread.sleep(1000); // Delay between commands
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private double parseWeightData(byte[] data) {
        // Enhanced weight parsing logic (from your existing code)
        try {
            // ASCII text parsing
            String asciiString = new String(data, StandardCharsets.UTF_8).trim();
            if (asciiString.length() > 3) {
                Pattern pattern = Pattern.compile("([+-]?\\s*\\d+\\.?\\d*)\\s*KG", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(asciiString);
                if (matcher.find()) {
                    String weightStr = matcher.group(1).replaceAll("\\s+", "");
                    float weight = Float.parseFloat(weightStr);
                    if (Math.abs(weight) < 1000) {
                        return Math.abs(weight);
                    }
                }
            }

            // Binary parsing
            if (data.length >= 3) {
                int flags = data[0] & 0xFF;
                boolean isImperial = (flags & 0x01) != 0;
                int offset = 1;
                if (data.length >= offset + 2) {
                    int weightRaw = ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
                    float weight = weightRaw * 0.005f;
                    if (isImperial) {
                        weight = weight * 0.453592f;
                    }
                    if (weight > 0.01 && weight < 1000) {
                        return weight;
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing weight data", e);
        }

        return -1;
    }

    private void updateWeightData(double weight) {
        currentWeight = weight;
        lastWeightUpdateTime = System.currentTimeMillis();

        // Simple stability check
        isWeightStable = true; // You can implement more sophisticated stability logic

        // Notify listeners
        notifyWeightReceived(weight, isWeightStable);

        Log.d(TAG, "Weight updated: " + weight + " kg (stable: " + isWeightStable + ")");
    }

    // ============================================================================
    // PUBLIC API METHODS
    // ============================================================================

    public void addListener(BleScaleServiceListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            Log.d(TAG, "Listener added. Total listeners: " + listeners.size());

            // Immediately notify new listener of current state
            listener.onConnectionStateChanged(isConnected, connectedDeviceName);
            if (isConnected) {
                listener.onWeightReceived(currentWeight, isWeightStable);
            }
        }
    }

    public void removeListener(BleScaleServiceListener listener) {
        if (listeners.remove(listener)) {
            Log.d(TAG, "Listener removed. Total listeners: " + listeners.size());
        }
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

    public void tare() {
        if (isConnected && bluetoothGatt != null && weightCharacteristic != null && checkBluetoothPermissions()) {
            try {
                byte[] tareCommand = {(byte)0x54}; // 'T' for tare
                weightCharacteristic.setValue(tareCommand);
                boolean success = bluetoothGatt.writeCharacteristic(weightCharacteristic);
                Log.d(TAG, "Tare command sent: " + success);
                if (success) {
                    notifyStatusChanged("Scale tared (zeroed)");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission error during tare", e);
                notifyError("Permission error during tare operation");
            }
        } else {
            notifyError("Cannot tare - scale not connected");
        }
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    private boolean checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void saveConnectedDeviceInfo(String deviceAddress, String deviceName) {
        SharedPreferences.Editor editor = servicePrefs.edit();
        editor.putString(PREF_DEVICE_ADDRESS, deviceAddress);
        editor.putString(PREF_DEVICE_NAME, deviceName);
        editor.apply();
    }

    private void clearSavedDeviceInfo() {
        SharedPreferences.Editor editor = servicePrefs.edit();
        editor.remove(PREF_DEVICE_ADDRESS);
        editor.remove(PREF_DEVICE_NAME);
        editor.apply();
    }

    // ============================================================================
    // LIFECYCLE MANAGEMENT
    // ============================================================================

    private void startHealthCheck() {
        healthCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (isServiceRunning) {
                    performHealthCheck();
                    mainHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS);
                }
            }
        };
        mainHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS);
    }

    private void performHealthCheck() {
        if (isConnected) {
            // Check if we're still receiving data
            long timeSinceLastUpdate = System.currentTimeMillis() - lastWeightUpdateTime;
            if (timeSinceLastUpdate > 60000) { // 1 minute
                Log.w(TAG, "No weight data received for " + timeSinceLastUpdate + "ms");
                // Could trigger a reconnection here if needed
            }

            // Try to read a characteristic to verify connection health
            if (weightCharacteristic != null && checkBluetoothPermissions()) {
                try {
                    boolean readResult = bluetoothGatt.readCharacteristic(weightCharacteristic);
                    Log.d(TAG, "Health check read: " + readResult);
                } catch (SecurityException e) {
                    Log.e(TAG, "Permission error during health check", e);
                }
            }
        }
    }

    private void startConnectionTimeout() {
        connectionTimeoutRunnable = () -> {
            if (isConnecting) {
                Log.w(TAG, "Connection timeout");
                handleConnectionFailure("Connection timeout");
            }
        };
        mainHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT_MS);
    }

    private void cancelConnectionTimeout() {
        if (connectionTimeoutRunnable != null) {
            mainHandler.removeCallbacks(connectionTimeoutRunnable);
            connectionTimeoutRunnable = null;
        }
    }

    private void cancelAllOperations() {
        if (reconnectionRunnable != null) {
            mainHandler.removeCallbacks(reconnectionRunnable);
            reconnectionRunnable = null;
        }
        cancelConnectionTimeout();
        if (healthCheckRunnable != null) {
            mainHandler.removeCallbacks(healthCheckRunnable);
            healthCheckRunnable = null;
        }
    }

    private void handleConnectionFailure(String error) {
        Log.e(TAG, "Connection failure: " + error);
        isConnecting = false;
        cancelConnectionTimeout();
        notifyError(error);

        if (shouldMaintainConnection) {
            scheduleReconnection();
        }
    }

    // ============================================================================
    // NOTIFICATION MANAGEMENT
    // ============================================================================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "BLE Scale Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Persistent BLE scale connection service");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createServiceNotification(String title, String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_bluetooth) // You'll need to add this icon
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void updateNotification(String title, String text) {
        Notification notification = createServiceNotification(title, text);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    // ============================================================================
    // LISTENER NOTIFICATIONS
    // ============================================================================

    private void notifyConnectionStateChanged(boolean connected, String deviceName) {
        for (BleScaleServiceListener listener : listeners) {
            try {
                listener.onConnectionStateChanged(connected, deviceName);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener of connection state change", e);
            }
        }
    }

    private void notifyWeightReceived(double weight, boolean stable) {
        for (BleScaleServiceListener listener : listeners) {
            try {
                listener.onWeightReceived(weight, stable);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener of weight data", e);
            }
        }
    }

    private void notifyError(String error) {
        for (BleScaleServiceListener listener : listeners) {
            try {
                listener.onError(error);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener of error", e);
            }
        }
    }

    private void notifyStatusChanged(String status) {
        for (BleScaleServiceListener listener : listeners) {
            try {
                listener.onServiceStatusChanged(status);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener of status change", e);
            }
        }
    }
}