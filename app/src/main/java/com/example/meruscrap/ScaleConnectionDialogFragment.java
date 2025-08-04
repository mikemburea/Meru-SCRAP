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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Production Scale Connection Dialog for BleScaleManager
 * - Uses BleScaleManager instead of BleScaleViewModel
 * - Uses listener pattern instead of LiveData observers
 * - Real BLE scale integration
 * - FIXED: Null pointer exception handling
 */
public class ScaleConnectionDialogFragment extends DialogFragment implements BleScaleManager.BleScaleListener {
    private static final String TAG = "ScaleConnectionDialog";

    // UI Components
    private LinearLayout deviceContainer;
    private LinearLayout emptyStateContainer;
    private ProgressBar scanProgress;
    private TextView statusMessage;
    private TextView noDevicesText;
    private MaterialButton scanButton;
    private MaterialButton btnCancel;

    // BLE Integration - CHANGED TO BleScaleManager
    private BleScaleManager bleScaleManager;
    private BleScaleManager.BleScaleListener mainFragmentListener; // Store main fragment listener

    // Handler for safe UI updates
    private Handler uiHandler;

    // Listener interface
    public interface ScaleConnectionListener {
        void onScaleConnected();
        void onConnectionCancelled();
    }

    private ScaleConnectionListener listener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uiHandler = new Handler(Looper.getMainLooper());
    }

    // ✅ FIXED: Method name and parameter type
    public void setBleScaleManager(BleScaleManager manager) {
        this.bleScaleManager = manager;
        if (bleScaleManager != null) {
            bleScaleManager.setListener(this);
        }
    }

    // NEW: Set the main fragment listener so we can restore it later
    public void setMainFragmentListener(BleScaleManager.BleScaleListener mainListener) {
        this.mainFragmentListener = mainListener;
    }

    public void setScaleConnectionListener(ScaleConnectionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_scale_connection, null);

        initializeViews(view);
        setupEventHandlers();

        return new MaterialAlertDialogBuilder(getContext())
                .setTitle("Connect to BLE Scale")
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

        // Initial state - show empty state
        updateDeviceVisibility(false);
        safeSetText(statusMessage, "Ready to scan for BLE scales");
    }

    private void setupEventHandlers() {
        scanButton.setOnClickListener(v -> startScan());
        btnCancel.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConnectionCancelled();
            }
            dismiss();
        });
    }

    private void startScan() {
        if (bleScaleManager == null) {
            safeSetText(statusMessage, "❌ Scale service not available");
            return;
        }

        // Clear previous results
        if (deviceContainer != null) {
            deviceContainer.removeAllViews();
        }
        updateDeviceVisibility(false);

        // Start scanning
        bleScaleManager.startScan();

        Log.d(TAG, "Starting BLE scan for scales...");
    }

    private void addDeviceView(BleDevice device) {
        if (deviceContainer == null || getContext() == null) {
            Log.w(TAG, "Cannot add device view - container or context is null");
            return;
        }

        View deviceView = LayoutInflater.from(getContext()).inflate(R.layout.item_ble_device, deviceContainer, false);

        TextView tvName = deviceView.findViewById(R.id.tv_device_name);
        TextView tvAddress = deviceView.findViewById(R.id.tv_device_address);
        TextView tvRssi = deviceView.findViewById(R.id.tv_device_rssi);
        MaterialButton btnConnect = deviceView.findViewById(R.id.btn_connect_device);

        // Set device info safely
        safeSetText(tvName, device.getName());
        safeSetText(tvAddress, device.getAddress());
        safeSetText(tvRssi, device.getRssi() + " dBm (" + device.getSignalQuality() + ")");

        // ✅ IMPROVED: Style based on signal quality using BleDevice method
        if (tvRssi != null && getResources() != null) {
            String signalQuality = device.getSignalQuality();
            try {
                if (signalQuality.equals("Excellent") || signalQuality.equals("Good")) {
                    tvRssi.setTextColor(getResources().getColor(R.color.success));
                } else if (signalQuality.equals("Fair")) {
                    tvRssi.setTextColor(getResources().getColor(R.color.warning));
                } else { // Poor
                    tvRssi.setTextColor(getResources().getColor(R.color.error));
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not set signal color: " + e.getMessage());
            }
        }

        // Connect button
        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> {
                Log.d(TAG, "Attempting to connect to: " + device.getName());
                safeSetText(statusMessage, "Connecting to " + device.getName() + "...");

                if (bleScaleManager != null) {
                    bleScaleManager.connectToDevice(device);
                }

                // Disable all connect buttons during connection attempt
                disableAllConnectButtons();
            });
        }

        deviceContainer.addView(deviceView);
    }

    private void disableAllConnectButtons() {
        if (deviceContainer == null) return;

        for (int i = 0; i < deviceContainer.getChildCount(); i++) {
            View deviceView = deviceContainer.getChildAt(i);
            MaterialButton btnConnect = deviceView.findViewById(R.id.btn_connect_device);
            if (btnConnect != null) {
                btnConnect.setEnabled(false);
                btnConnect.setText("Connecting...");
            }
        }
    }

    private void enableAllConnectButtons() {
        if (deviceContainer == null) return;

        for (int i = 0; i < deviceContainer.getChildCount(); i++) {
            View deviceView = deviceContainer.getChildAt(i);
            MaterialButton btnConnect = deviceView.findViewById(R.id.btn_connect_device);
            if (btnConnect != null) {
                btnConnect.setEnabled(true);
                btnConnect.setText("Connect");
            }
        }
    }

    /**
     * Updated visibility management - CRITICAL FIX
     */
    private void updateDeviceVisibility(boolean hasDevices) {
        Log.d(TAG, "updateDeviceVisibility: hasDevices = " + hasDevices);

        if (hasDevices) {
            // Show device list, hide empty state
            safeSetVisibility(emptyStateContainer, View.GONE);
            safeSetVisibility(deviceContainer, View.VISIBLE);
            Log.d(TAG, "Showing device list");
        } else {
            // Show empty state, hide device list
            safeSetVisibility(emptyStateContainer, View.VISIBLE);
            safeSetVisibility(deviceContainer, View.GONE);
            Log.d(TAG, "Showing empty state");
        }
    }

    // ✅ IMPLEMENT BleScaleManager.BleScaleListener INTERFACE METHODS

    @Override
    public void onScanStarted() {
        runSafeUIUpdate(() -> {
            safeSetVisibility(scanProgress, View.VISIBLE);
            if (scanButton != null) {
                scanButton.setEnabled(false);
                scanButton.setText("Scanning...");
            }
            safeSetText(statusMessage, "Scanning for BLE scales...");
        });
    }

    @Override
    public void onScanStopped() {
        runSafeUIUpdate(() -> {
            safeSetVisibility(scanProgress, View.GONE);
            if (scanButton != null) {
                scanButton.setEnabled(true);
                scanButton.setText("Scan Again");
            }
        });
    }

    @Override
    public void onDeviceFound(BleDevice device) {
        runSafeUIUpdate(() -> {
            Log.d(TAG, "Device found: " + device.getName());

            // Add device to the list
            addDeviceView(device);

            // Update visibility to show device list
            updateDeviceVisibility(true);

            // Update status message
            int deviceCount = deviceContainer != null ? deviceContainer.getChildCount() : 0;
            safeSetText(statusMessage, "Found " + deviceCount + " BLE scale(s)");
        });
    }

    @Override
    public void onDeviceConnected(String deviceName) {
        runSafeUIUpdate(() -> {
            safeSetText(statusMessage, "✅ Connected to " + deviceName + "!");
            safeSetVisibility(scanProgress, View.GONE);

            // Notify listener
            if (listener != null) {
                listener.onScaleConnected();
            }

            // Auto-dismiss after showing success message - FIXED: Use Handler instead of getView()
            uiHandler.postDelayed(() -> {
                if (isAdded() && getDialog() != null && getDialog().isShowing()) {
                    dismiss();

                    // CRITICAL FIX: Restore listener to main fragment after successful connection
                    if (bleScaleManager != null && mainFragmentListener != null) {
                        bleScaleManager.setListener(mainFragmentListener);
                    }
                }
            }, 1500);
        });
    }

    @Override
    public void onDeviceDisconnected() {
        runSafeUIUpdate(() -> {
            safeSetText(statusMessage, "Device disconnected");
            enableAllConnectButtons();
        });
    }

    @Override
    public void onWeightReceived(double weight, boolean stable) {
        // Not needed in connection dialog
    }

    @Override
    public void onError(String error) {
        runSafeUIUpdate(() -> {
            Log.e(TAG, "BLE Error: " + error);
            safeSetText(statusMessage, "❌ " + error);
            safeSetVisibility(scanProgress, View.GONE);
            if (scanButton != null) {
                scanButton.setEnabled(true);
                scanButton.setText("Retry");
            }
            enableAllConnectButtons();
        });
    }

    // ✅ SAFE UI UPDATE HELPER METHODS

    /**
     * Safely run UI updates only if fragment is still attached
     */
    private void runSafeUIUpdate(Runnable uiUpdate) {
        if (getActivity() == null || !isAdded()) {
            Log.w(TAG, "Fragment not attached, skipping UI update");
            return;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Already on main thread
            uiUpdate.run();
        } else {
            // Post to main thread
            getActivity().runOnUiThread(uiUpdate);
        }
    }

    /**
     * Safely set text on TextView, checking for null
     */
    private void safeSetText(TextView textView, String text) {
        if (textView != null && text != null) {
            textView.setText(text);
        }
    }

    /**
     * Safely set visibility on View, checking for null
     */
    private void safeSetVisibility(View view, int visibility) {
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Clean up handler
        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }

        // Stop scanning when dialog is destroyed
        if (bleScaleManager != null && bleScaleManager.isScanning()) {
            bleScaleManager.stopScan();
        }

        // FIXED: Don't set listener to null, restore to main fragment if we have it
        if (bleScaleManager != null && mainFragmentListener != null) {
            bleScaleManager.setListener(mainFragmentListener);
        }
    }

    @Override
    public void onCancel(@NonNull android.content.DialogInterface dialog) {
        super.onCancel(dialog);
        if (listener != null) {
            listener.onConnectionCancelled();
        }
    }
}