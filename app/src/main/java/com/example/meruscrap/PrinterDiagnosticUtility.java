package com.example.meruscrap;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrinterDiagnosticUtility {
    private static final String TAG = "PrinterDiagnostic";

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private ExecutorService executor;
    private Handler mainHandler;
    private DiagnosticListener listener;

    public interface DiagnosticListener {
        void onDiagnosticStarted();
        void onDiagnosticStep(String step, DiagnosticResult result);
        void onDiagnosticCompleted(List<DiagnosticResult> results, List<String> recommendations);
    }

    public PrinterDiagnosticUtility(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setDiagnosticListener(DiagnosticListener listener) {
        this.listener = listener;
    }

    public void runFullDiagnostic(BluetoothDevice targetDevice) {
        if (listener != null) {
            mainHandler.post(() -> listener.onDiagnosticStarted());
        }

        executor.execute(() -> {
            List<DiagnosticResult> results = new ArrayList<>();
            List<String> recommendations = new ArrayList<>();

            // Step 1: Check Bluetooth availability
            DiagnosticResult bluetoothCheck = checkBluetoothAvailability();
            results.add(bluetoothCheck);
            notifyStep("Bluetooth Availability", bluetoothCheck);

            // Step 2: Check permissions
            DiagnosticResult permissionCheck = checkPermissions();
            results.add(permissionCheck);
            notifyStep("Permissions", permissionCheck);

            // Step 3: Check device pairing
            DiagnosticResult pairingCheck = checkDevicePairing(targetDevice);
            results.add(pairingCheck);
            notifyStep("Device Pairing", pairingCheck);

            // Step 4: Test connection
            DiagnosticResult connectionCheck = testConnection(targetDevice);
            results.add(connectionCheck);
            notifyStep("Connection Test", connectionCheck);

            // Step 5: Test communication
            DiagnosticResult communicationCheck = testCommunication(targetDevice);
            results.add(communicationCheck);
            notifyStep("Communication Test", communicationCheck);

            // Step 6: Check printer status
            DiagnosticResult statusCheck = checkPrinterStatus(targetDevice);
            results.add(statusCheck);
            notifyStep("Printer Status", statusCheck);

            // Generate recommendations
            recommendations = generateRecommendations(results);

            // Notify completion
            final List<DiagnosticResult> finalResults = results;
            final List<String> finalRecommendations = recommendations;
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onDiagnosticCompleted(finalResults, finalRecommendations);
                }
            });
        });
    }

    private DiagnosticResult checkBluetoothAvailability() {
        DiagnosticResult result = new DiagnosticResult();
        result.testName = "Bluetooth Availability";

        if (bluetoothAdapter == null) {
            result.status = DiagnosticStatus.FAILED;
            result.message = "Bluetooth not supported on this device";
            result.details = "This device does not have Bluetooth capability";
        } else if (!bluetoothAdapter.isEnabled()) {
            result.status = DiagnosticStatus.WARNING;
            result.message = "Bluetooth is disabled";
            result.details = "Bluetooth needs to be enabled to connect to printers";
        } else {
            result.status = DiagnosticStatus.PASSED;
            result.message = "Bluetooth is available and enabled";
            result.details = "Bluetooth adapter is ready for printer connections";
        }

        return result;
    }

    private DiagnosticResult checkPermissions() {
        DiagnosticResult result = new DiagnosticResult();
        result.testName = "Bluetooth Permissions";

        String[] requiredPermissions = {
                "android.permission.BLUETOOTH",
                "android.permission.BLUETOOTH_ADMIN",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.ACCESS_FINE_LOCATION"
        };

        List<String> missingPermissions = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (missingPermissions.isEmpty()) {
            result.status = DiagnosticStatus.PASSED;
            result.message = "All required permissions granted";
            result.details = "App has all necessary Bluetooth permissions";
        } else {
            result.status = DiagnosticStatus.FAILED;
            result.message = "Missing required permissions";
            result.details = "Missing: " + String.join(", ", missingPermissions);
        }

        return result;
    }

    private DiagnosticResult checkDevicePairing(BluetoothDevice device) {
        DiagnosticResult result = new DiagnosticResult();
        result.testName = "Device Pairing";

        if (device == null) {
            result.status = DiagnosticStatus.FAILED;
            result.message = "No target device specified";
            result.details = "A printer device must be selected for testing";
            return result;
        }

        try {
            int bondState = device.getBondState();
            switch (bondState) {
                case BluetoothDevice.BOND_BONDED:
                    result.status = DiagnosticStatus.PASSED;
                    result.message = "Device is paired";
                    result.details = "Device is properly paired and ready for connection";
                    break;
                case BluetoothDevice.BOND_BONDING:
                    result.status = DiagnosticStatus.WARNING;
                    result.message = "Device is currently bonding";
                    result.details = "Pairing process is in progress";
                    break;
                default:
                    result.status = DiagnosticStatus.FAILED;
                    result.message = "Device is not paired";
                    result.details = "Device needs to be paired before connection";
                    break;
            }
        } catch (SecurityException e) {
            result.status = DiagnosticStatus.FAILED;
            result.message = "Cannot check pairing status";
            result.details = "Insufficient permissions to check device pairing";
        }

        return result;
    }

    private DiagnosticResult testConnection(BluetoothDevice device) {
        DiagnosticResult result = new DiagnosticResult();
        result.testName = "Connection Test";

        if (device == null) {
            result.status = DiagnosticStatus.FAILED;
            result.message = "No device to test";
            return result;
        }

        try {
            // Attempt to create socket (doesn't actually connect)
            android.bluetooth.BluetoothSocket socket = device.createRfcommSocketToServiceRecord(
                    java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

            // Try to connect with timeout
            socket.connect();

            if (socket.isConnected()) {
                result.status = DiagnosticStatus.PASSED;
                result.message = "Connection successful";
                result.details = "Successfully connected to printer";
                socket.close();
            } else {
                result.status = DiagnosticStatus.FAILED;
                result.message = "Connection failed";
                result.details = "Could not establish connection to printer";
            }

        } catch (Exception e) {
            result.status = DiagnosticStatus.FAILED;
            result.message = "Connection failed";
            result.details = "Error: " + e.getMessage();

            // Analyze specific error types
            if (e.getMessage().contains("Device or resource busy")) {
                result.details += "\nPrinter may be connected to another device";
            } else if (e.getMessage().contains("Connection refused")) {
                result.details += "\nPrinter may not be accepting connections";
            } else if (e.getMessage().contains("timeout")) {
                result.details += "\nConnection timed out - printer may be out of range";
            }
        }

        return result;
    }

    private DiagnosticResult testCommunication(BluetoothDevice device) {
        DiagnosticResult result = new DiagnosticResult();
        result.testName = "Communication Test";

        try {
            android.bluetooth.BluetoothSocket socket = device.createRfcommSocketToServiceRecord(
                    java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

            socket.connect();

            if (socket.isConnected()) {
                java.io.OutputStream outputStream = socket.getOutputStream();

                // Send initialization command
                byte[] initCommand = {0x1B, 0x40}; // ESC @
                outputStream.write(initCommand);
                outputStream.flush();

                Thread.sleep(500);

                // Send status query
                byte[] statusQuery = {0x10, 0x04, 0x01}; // DLE EOT
                outputStream.write(statusQuery);
                outputStream.flush();

                Thread.sleep(500);

                result.status = DiagnosticStatus.PASSED;
                result.message = "Communication successful";
                result.details = "Successfully sent commands to printer";

                socket.close();
            } else {
                result.status = DiagnosticStatus.FAILED;
                result.message = "Could not establish communication";
            }

        } catch (Exception e) {
            result.status = DiagnosticStatus.FAILED;
            result.message = "Communication failed";
            result.details = "Error: " + e.getMessage();
        }

        return result;
    }

    private DiagnosticResult checkPrinterStatus(BluetoothDevice device) {
        DiagnosticResult result = new DiagnosticResult();
        result.testName = "Printer Status Check";

        try {
            android.bluetooth.BluetoothSocket socket = device.createRfcommSocketToServiceRecord(
                    java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

            socket.connect();

            if (socket.isConnected()) {
                java.io.OutputStream outputStream = socket.getOutputStream();
                java.io.InputStream inputStream = socket.getInputStream();

                // Query printer status
                byte[] statusQuery = {0x10, 0x04, 0x01};
                outputStream.write(statusQuery);
                outputStream.flush();

                Thread.sleep(1000);

                // Read response
                byte[] buffer = new byte[16];
                int bytesRead = 0;

                if (inputStream.available() > 0) {
                    bytesRead = inputStream.read(buffer);
                }

                if (bytesRead > 0) {
                    String statusInfo = interpretPrinterStatus(buffer[0]);
                    result.status = DiagnosticStatus.PASSED;
                    result.message = "Status retrieved successfully";
                    result.details = statusInfo;
                } else {
                    result.status = DiagnosticStatus.WARNING;
                    result.message = "No status response";
                    result.details = "Printer did not respond to status query";
                }

                socket.close();
            }

        } catch (Exception e) {
            result.status = DiagnosticStatus.FAILED;
            result.message = "Status check failed";
            result.details = "Error: " + e.getMessage();
        }

        return result;
    }

    private String interpretPrinterStatus(byte statusByte) {
        List<String> statusMessages = new ArrayList<>();

        if ((statusByte & 0x01) != 0) statusMessages.add("Drawer open");
        if ((statusByte & 0x02) != 0) statusMessages.add("Offline");
        if ((statusByte & 0x04) != 0) statusMessages.add("No paper");
        if ((statusByte & 0x08) != 0) statusMessages.add("Cover open");
        if ((statusByte & 0x20) != 0) statusMessages.add("Paper jam");
        if ((statusByte & 0x40) != 0) statusMessages.add("Error");
        if ((statusByte & 0x80) != 0) statusMessages.add("Auto cutter error");

        if (statusMessages.isEmpty()) {
            return "Printer is online and ready";
        } else {
            return "Issues detected: " + String.join(", ", statusMessages);
        }
    }

    private List<String> generateRecommendations(List<DiagnosticResult> results) {
        List<String> recommendations = new ArrayList<>();

        for (DiagnosticResult result : results) {
            switch (result.testName) {
                case "Bluetooth Availability":
                    if (result.status == DiagnosticStatus.FAILED) {
                        recommendations.add("• This device does not support Bluetooth printing");
                    } else if (result.status == DiagnosticStatus.WARNING) {
                        recommendations.add("• Enable Bluetooth in device settings");
                    }
                    break;

                case "Bluetooth Permissions":
                    if (result.status == DiagnosticStatus.FAILED) {
                        recommendations.add("• Grant all required Bluetooth permissions in app settings");
                    }
                    break;

                case "Device Pairing":
                    if (result.status == DiagnosticStatus.FAILED) {
                        recommendations.add("• Pair the printer in Bluetooth settings first");
                        recommendations.add("• Make sure printer is in pairing mode");
                    }
                    break;

                case "Connection Test":
                    if (result.status == DiagnosticStatus.FAILED) {
                        recommendations.add("• Check if printer is turned on and in range");
                        recommendations.add("• Ensure printer is not connected to another device");
                        recommendations.add("• Try turning printer off and on again");
                    }
                    break;

                case "Communication Test":
                    if (result.status == DiagnosticStatus.FAILED) {
                        recommendations.add("• Verify printer supports ESC/POS commands");
                        recommendations.add("• Check printer documentation for command compatibility");
                    }
                    break;

                case "Printer Status Check":
                    if (result.status == DiagnosticStatus.FAILED) {
                        recommendations.add("• Check printer for paper jams or cover issues");
                        recommendations.add("• Ensure printer has paper and is ready");
                    } else if (result.details.contains("No paper")) {
                        recommendations.add("• Load paper into the printer");
                    } else if (result.details.contains("Cover open")) {
                        recommendations.add("• Close the printer cover");
                    } else if (result.details.contains("Paper jam")) {
                        recommendations.add("• Clear paper jam and reset printer");
                    }
                    break;
            }
        }

        // General recommendations
        if (hasFailures(results)) {
            recommendations.add("• Restart the app and try again");
            recommendations.add("• Check printer manual for troubleshooting steps");
            recommendations.add("• Contact support if issues persist");
        }

        return recommendations;
    }

    private boolean hasFailures(List<DiagnosticResult> results) {
        return results.stream().anyMatch(r -> r.status == DiagnosticStatus.FAILED);
    }

    private void notifyStep(String step, DiagnosticResult result) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onDiagnosticStep(step, result);
            }
        });
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    // Quick diagnostic methods for common issues
    public boolean isBluetoothReady() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean hasRequiredPermissions() {
        String[] permissions = {
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_SCAN"
        };

        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public String getDeviceSignalStrength(BluetoothDevice device) {
        // This would require additional BLE scanning for RSSI
        // For classic Bluetooth, signal strength is not easily available
        return "Signal strength not available for Classic Bluetooth";
    }

    // Data classes
    public static class DiagnosticResult {
        public String testName;
        public DiagnosticStatus status;
        public String message;
        public String details;
        public long timestamp = System.currentTimeMillis();
    }

    public enum DiagnosticStatus {
        PASSED,
        WARNING,
        FAILED
    }
}