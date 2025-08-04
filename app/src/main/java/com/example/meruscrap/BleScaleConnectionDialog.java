package com.example.meruscrap;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Observer;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Production-ready BLE Scale Connection Dialog
 * - Uses standalone BleDevice class ONLY
 * - No references to BleScaleViewModel.BleDevice
 * - Real BLE scale integration
 * - FIXED: Only the selected device shows connecting state
 */
public class BleScaleConnectionDialog extends DialogFragment {
    private static final String TAG = "BleScaleConnectionDialog";

    // UI Components
    private LinearLayout deviceContainer;
    private LinearLayout emptyStateContainer;
    private ProgressBar scanProgress;
    private TextView statusMessage;
    private TextView noDevicesText;
    private MaterialButton scanButton;
    private MaterialButton btnCancel;

    // BLE Integration
    private BleScaleViewModel bleScaleViewModel;

    // Handler for delayed operations
    private Handler handler = new Handler(Looper.getMainLooper());

    // Track device views and their connect buttons
    private Map<String, View> deviceViewMap = new HashMap<>();
    private Map<String, MaterialButton> connectButtonMap = new HashMap<>();
    private String connectingDeviceAddress = null;

    // Button states with visual feedback
    private enum ButtonState {
        NORMAL,      // Blue - ready to connect
        CONNECTING,  // Orange with progress - actively connecting
        DISABLED,    // Gray - disabled while another device connects
        SUCCESS,     // Green - successfully connected
        ERROR        // Red - connection failed
    }

    // Listener interface
    public interface ScaleConnectionListener {
        void onScaleConnected();
        void onConnectionCancelled();
    }

    private ScaleConnectionListener scaleConnectionListener;

    public void setBleScaleViewModel(BleScaleViewModel viewModel) {
        this.bleScaleViewModel = viewModel;
    }

    public void setScaleConnectionListener(ScaleConnectionListener listener) {
        this.scaleConnectionListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_scale_connection, null);

        initializeViews(view);
        setupEventHandlers();
        setupObservers();

        return new MaterialAlertDialogBuilder(getContext())
                .setTitle("Connect to Scale")
                .setView(view)
                .setCancelable(true)
                .create();
    }

    private void initializeViews(View view) {
        deviceContainer = view.findViewById(R.id.device_container);
        emptyStateContainer = view.findViewById(R.id.empty_state_container);
        scanProgress = view.findViewById(R.id.scan_progress);
        statusMessage = view.findViewById(R.id.status_message);
        noDevicesText = view.findViewById(R.id.no_devices_text);
        scanButton = view.findViewById(R.id.scan_button);
        btnCancel = view.findViewById(R.id.btn_cancel);

        // Verify all views are found
        if (deviceContainer == null) Log.e(TAG, "deviceContainer not found in layout!");
        if (emptyStateContainer == null) Log.e(TAG, "emptyStateContainer not found in layout!");

        // Initial state
        updateDeviceVisibility(false);
        statusMessage.setText("Ready to scan for BLE scales");
    }

    private void setupEventHandlers() {
        scanButton.setOnClickListener(v -> {
            // Clear previous results and start fresh scan
            clearDeviceList();
            startScan();
        });

        btnCancel.setOnClickListener(v -> {
            if (scaleConnectionListener != null) {
                scaleConnectionListener.onConnectionCancelled();
            }
            dismiss();
        });
    }

    private void setupObservers() {
        if (bleScaleViewModel == null) {
            Log.e(TAG, "BleScaleViewModel is null! Cannot setup observers.");
            statusMessage.setText("Error: Scale service not available");
            return;
        }

        // Observe scanning state
        bleScaleViewModel.getIsScanning().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isScanning) {
                if (!isAdded() || getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    Log.d(TAG, "Scanning state changed: " + isScanning);
                    updateScanningUI(isScanning);
                });
            }
        });

        // ✅ FIXED: Observe found devices using standalone BleDevice class
        bleScaleViewModel.getScannedDevices().observe(this, new Observer<List<BleDevice>>() {
            @Override
            public void onChanged(List<BleDevice> devices) {
                if (!isAdded() || getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    Log.d(TAG, "Devices updated: " + (devices != null ? devices.size() : 0));
                    updateDevicesList(devices);
                });
            }
        });

        // Observe connection state
        bleScaleViewModel.getIsConnected().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isConnected) {
                if (!isAdded() || getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    if (isConnected != null && isConnected) {
                        Log.d(TAG, "Scale connected successfully!");
                        handleSuccessfulConnection();
                    }
                });
            }
        });

        // Observe connection status messages
        bleScaleViewModel.getConnectionStatus().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String status) {
                if (!isAdded() || getActivity() == null || status == null) return;

                getActivity().runOnUiThread(() -> {
                    statusMessage.setText(status);
                });
            }
        });

        // Observe errors
        bleScaleViewModel.getErrorMessage().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String error) {
                if (!isAdded() || getActivity() == null || error == null || error.isEmpty()) return;

                getActivity().runOnUiThread(() -> {
                    Log.e(TAG, "BLE Error: " + error);
                    handleError(error);
                });
            }
        });

        // Observe connecting state
        bleScaleViewModel.getIsConnecting().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isConnecting) {
                if (!isAdded() || getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    if (isConnecting != null && isConnecting) {
                        updateConnectingUI();
                    } else {
                        // Connection attempt ended (either success or failure)
                        resetConnectionUI();
                    }
                });
            }
        });
    }

    private void startScan() {
        if (bleScaleViewModel == null) {
            statusMessage.setText("❌ Scale service not available");
            Log.e(TAG, "Cannot start scan - BleScaleViewModel is null");
            return;
        }

        // Reset connection tracking
        connectingDeviceAddress = null;

        Log.d(TAG, "Starting BLE scan for scales...");
        bleScaleViewModel.startScan();
    }

    private void updateScanningUI(boolean isScanning) {
        if (isScanning) {
            scanProgress.setVisibility(View.VISIBLE);
            scanButton.setEnabled(false);
            scanButton.setText("Scanning...");
            statusMessage.setText("Scanning for BLE scales...");
        } else {
            scanProgress.setVisibility(View.GONE);
            scanButton.setEnabled(true);
            scanButton.setText("Scan Again");
        }
    }

    // ✅ FIXED: Uses standalone BleDevice class
    private void updateDevicesList(List<BleDevice> devices) {
        // Clear existing device views
        clearDeviceList();

        if (devices == null || devices.isEmpty()) {
            // No devices found
            updateDeviceVisibility(false);
            if (!bleScaleViewModel.isScanningValue()) {
                statusMessage.setText("No scales found. Make sure your scale is powered on and nearby.");
            }
            Log.d(TAG, "No devices to display");
        } else {
            // Devices found
            updateDeviceVisibility(true);
            statusMessage.setText("Found " + devices.size() + " scale(s) - Select one to connect");

            // ✅ FIXED: Loop uses standalone BleDevice class
            for (BleDevice device : devices) {
                addDeviceView(device);
            }

            Log.d(TAG, "Displayed " + devices.size() + " devices");
        }
    }

    private void clearDeviceList() {
        if (deviceContainer != null) {
            deviceContainer.removeAllViews();
        }
        // Clear tracking maps
        deviceViewMap.clear();
        connectButtonMap.clear();
    }

    /**
     * Set button visual state with appropriate colors and text
     */
    private void setButtonState(MaterialButton button, ButtonState state) {
        if (button == null || getContext() == null || !isAdded()) return;

        try {
            switch (state) {
                case NORMAL:
                    button.setEnabled(true);
                    button.setText("Connect");
                    button.setBackgroundTintList(getResources().getColorStateList(R.color.primary));
                    button.setTextColor(getResources().getColor(R.color.white));
                    button.setIconResource(0); // Remove icon
                    break;

                case CONNECTING:
                    button.setEnabled(false);
                    button.setText("Connecting...");
                    button.setBackgroundTintList(getResources().getColorStateList(R.color.warning));
                    button.setTextColor(getResources().getColor(R.color.white));
                    // Add a loading icon if you have one
                    // button.setIconResource(R.drawable.ic_loading);
                    break;

                case DISABLED:
                    button.setEnabled(false);
                    button.setText("Connect");
                    button.setBackgroundTintList(getResources().getColorStateList(android.R.color.darker_gray));
                    button.setTextColor(getResources().getColor(R.color.white));
                    button.setIconResource(0);
                    break;

                case SUCCESS:
                    button.setEnabled(false);
                    button.setText("Connected ✓");
                    button.setBackgroundTintList(getResources().getColorStateList(R.color.success));
                    button.setTextColor(getResources().getColor(R.color.white));
                    break;

                case ERROR:
                    button.setEnabled(true);
                    button.setText("Retry");
                    button.setBackgroundTintList(getResources().getColorStateList(R.color.error));
                    button.setTextColor(getResources().getColor(R.color.white));
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting button state: " + e.getMessage());
        }
    }

    /**
     * Set device item background to indicate connection state
     */
    private void setDeviceItemState(View deviceView, boolean isConnecting, boolean isConnected) {
        if (deviceView == null || getContext() == null || !isAdded()) return;

        try {
            if (isConnected) {
                // Light green background for connected device
                deviceView.setBackgroundColor(getResources().getColor(R.color.success_light));
            } else if (isConnecting) {
                // Light orange background for connecting device
                deviceView.setBackgroundColor(getResources().getColor(R.color.warning_light));
            } else {
                // Default background
                deviceView.setBackgroundColor(getResources().getColor(android.R.color.white));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting device item state: " + e.getMessage());
        }
    }

    // ✅ FIXED: Parameter uses standalone BleDevice class
    private void addDeviceView(BleDevice device) {
        if (deviceContainer == null) {
            Log.e(TAG, "Cannot add device view - deviceContainer is null");
            return;
        }

        View deviceView = LayoutInflater.from(getContext()).inflate(R.layout.item_ble_device, deviceContainer, false);

        TextView tvName = deviceView.findViewById(R.id.tv_device_name);
        TextView tvAddress = deviceView.findViewById(R.id.tv_device_address);
        TextView tvRssi = deviceView.findViewById(R.id.tv_device_rssi);
        MaterialButton btnConnect = deviceView.findViewById(R.id.btn_connect_device);

        // Set device information
        tvName.setText(device.getName());
        if (tvAddress != null) {
            tvAddress.setText(device.getAddress());
        }

        // ✅ ENHANCED: Shows both dBm and signal quality using standalone BleDevice
        tvRssi.setText(device.getRssi() + " dBm (" + device.getSignalQuality() + ")");

        // ✅ IMPROVED: Color code using signal quality from standalone BleDevice
        String signalQuality = device.getSignalQuality();
        int signalColor;
        try {
            if (signalQuality.equals("Excellent") || signalQuality.equals("Good")) {
                signalColor = getResources().getColor(R.color.success);
            } else if (signalQuality.equals("Fair")) {
                signalColor = getResources().getColor(R.color.warning);
            } else { // Poor
                signalColor = getResources().getColor(R.color.error);
            }
            tvRssi.setTextColor(signalColor);
        } catch (Exception e) {
            Log.e(TAG, "Error setting signal color: " + e.getMessage());
        }

        // Store references for tracking
        deviceViewMap.put(device.getAddress(), deviceView);
        connectButtonMap.put(device.getAddress(), btnConnect);

        // Setup connect button with default state
        setButtonState(btnConnect, ButtonState.NORMAL);

        // Setup connect button click
        btnConnect.setOnClickListener(v -> {
            Log.d(TAG, "User selected device: " + device.getName() + " (" + device.getAddress() + ")");
            connectToDevice(device);
        });

        // Restore connecting state if this device is currently connecting
        if (connectingDeviceAddress != null && connectingDeviceAddress.equals(device.getAddress())) {
            setButtonState(btnConnect, ButtonState.CONNECTING);
        }

        deviceContainer.addView(deviceView);
        Log.d(TAG, "Added device view for: " + device.getName());
    }

    // ✅ FIXED: Parameter uses standalone BleDevice class
    private void connectToDevice(BleDevice device) {
        statusMessage.setText("Connecting to " + device.getName() + "...");

        // Track which device is connecting
        connectingDeviceAddress = device.getAddress();

        // Update visual states for all devices
        for (Map.Entry<String, MaterialButton> entry : connectButtonMap.entrySet()) {
            String address = entry.getKey();
            MaterialButton button = entry.getValue();
            View deviceView = deviceViewMap.get(address);

            if (address.equals(device.getAddress())) {
                // This is the connecting device
                setButtonState(button, ButtonState.CONNECTING);
                setDeviceItemState(deviceView, true, false);
            } else {
                // Other devices are disabled
                setButtonState(button, ButtonState.DISABLED);
                setDeviceItemState(deviceView, false, false);
            }
        }

        // Initiate connection
        bleScaleViewModel.connectToDevice(device);
    }

    private void updateConnectingUI() {
        scanProgress.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
    }

    private void resetConnectionUI() {
        // Reset all buttons to normal state
        for (Map.Entry<String, MaterialButton> entry : connectButtonMap.entrySet()) {
            setButtonState(entry.getValue(), ButtonState.NORMAL);

            // Reset device item background
            View deviceView = deviceViewMap.get(entry.getKey());
            setDeviceItemState(deviceView, false, false);
        }

        // Clear the connecting device
        connectingDeviceAddress = null;

        scanProgress.setVisibility(View.GONE);
        scanButton.setEnabled(true);
    }

    private void handleSuccessfulConnection() {
        // Check if views are still available
        if (!isAdded() || getView() == null) {
            Log.w(TAG, "handleSuccessfulConnection called but dialog views not available");
            // Still notify listener even if UI update fails
            if (scaleConnectionListener != null) {
                scaleConnectionListener.onScaleConnected();
            }
            return;
        }

        String deviceName = bleScaleViewModel.getConnectedDeviceName().getValue();
        if (statusMessage != null) {
            statusMessage.setText("✅ Connected to " + (deviceName != null ? deviceName : "scale") + "!");
        }

        if (scanProgress != null) {
            scanProgress.setVisibility(View.GONE);
        }

        // Show success state for connected device
        if (connectingDeviceAddress != null) {
            MaterialButton connectedButton = connectButtonMap.get(connectingDeviceAddress);
            View connectedDeviceView = deviceViewMap.get(connectingDeviceAddress);

            if (connectedButton != null) {
                setButtonState(connectedButton, ButtonState.SUCCESS);
            }
            if (connectedDeviceView != null) {
                setDeviceItemState(connectedDeviceView, false, true);
            }
        }

        // Notify the listener
        if (scaleConnectionListener != null) {
            scaleConnectionListener.onScaleConnected();
        }

        // Auto-dismiss after showing success using handler
        handler.postDelayed(() -> {
            if (isAdded()) {
                dismiss();
            }
        }, 1500);
    }

    private void handleError(String error) {
        // Check if views are still available
        if (!isAdded() || getView() == null) {
            Log.w(TAG, "handleError called but dialog views not available");
            return;
        }

        if (statusMessage != null) {
            statusMessage.setText("❌ " + error);
        }
        if (scanProgress != null) {
            scanProgress.setVisibility(View.GONE);
        }
        if (scanButton != null) {
            scanButton.setEnabled(true);
            scanButton.setText("Retry");
        }

        // Show error state for the device that failed to connect
        if (connectingDeviceAddress != null) {
            MaterialButton errorButton = connectButtonMap.get(connectingDeviceAddress);
            if (errorButton != null) {
                setButtonState(errorButton, ButtonState.ERROR);
            }

            // Reset other buttons to normal
            for (Map.Entry<String, MaterialButton> entry : connectButtonMap.entrySet()) {
                if (!entry.getKey().equals(connectingDeviceAddress)) {
                    setButtonState(entry.getValue(), ButtonState.NORMAL);
                }
            }

            // Reset all device backgrounds
            for (Map.Entry<String, View> entry : deviceViewMap.entrySet()) {
                setDeviceItemState(entry.getValue(), false, false);
            }
        } else {
            // If no specific device, reset all
            resetConnectionUI();
        }

        // Clear connecting device after a delay to show error state
        // Use handler instead of getView().postDelayed to avoid null reference
        handler.postDelayed(() -> {
            connectingDeviceAddress = null;
        }, 2000);
    }

    /**
     * CRITICAL FIX: Proper visibility management
     */
    private void updateDeviceVisibility(boolean hasDevices) {
        Log.d(TAG, "updateDeviceVisibility: hasDevices = " + hasDevices);

        if (hasDevices) {
            // Show device list, hide empty state
            if (emptyStateContainer != null) {
                emptyStateContainer.setVisibility(View.GONE);
                Log.d(TAG, "Hiding empty state container");
            }
            if (deviceContainer != null) {
                deviceContainer.setVisibility(View.VISIBLE);
                Log.d(TAG, "Showing device container");
            }
        } else {
            // Show empty state, hide device list
            if (emptyStateContainer != null) {
                emptyStateContainer.setVisibility(View.VISIBLE);
                Log.d(TAG, "Showing empty state container");
            }
            if (deviceContainer != null) {
                deviceContainer.setVisibility(View.GONE);
                Log.d(TAG, "Hiding device container");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Clean up handler callbacks
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        // Stop scanning when dialog is destroyed to save battery
        if (bleScaleViewModel != null && bleScaleViewModel.isScanningValue()) {
            bleScaleViewModel.stopScan();
            Log.d(TAG, "Stopped scanning on dialog destroy");
        }

        // Clear tracking maps
        deviceViewMap.clear();
        connectButtonMap.clear();
    }

    @Override
    public void onCancel(@NonNull android.content.DialogInterface dialog) {
        super.onCancel(dialog);
        if (scaleConnectionListener != null) {
            scaleConnectionListener.onConnectionCancelled();
        }
    }
}