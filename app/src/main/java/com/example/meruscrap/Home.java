package com.example.meruscrap;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Home extends Fragment implements
        BleScaleConnectionDialog.ScaleConnectionListener,
        BlePermissionHandler.PermissionCallback {

    private static final String TAG = "HomeFragment";

    // UI Components
    private CardView btnNewTransaction, btnBluetooth, btnMaterials;
    private ImageView ivBluetoothStatus, ivBluetoothIndicator;
    private TextView tvBluetoothStatus, tvBluetoothIndicator;
    private TextView tvCurrentDate;

    // Stats TextViews
    private TextView tvTodaySales, tvGrowthPercentage;
    private TextView tvTransactionCount, tvLastTransaction;

    // Database helper
    private TransactionsDBHelper transactionsDBHelper;

    // BLE Scale Integration
    private BleScaleViewModel bleScaleViewModel;
    private BlePermissionHandler permissionHandler;
    private boolean isScaleConnected = false;

    // Handler for periodic updates
    private Handler updateHandler;
    private Runnable updateRunnable;

    // MainActivity reference
    private MainActivity mainActivity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            mainActivity = (MainActivity) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        initializeViews(view);
        initializeDatabase();
        initializeBleComponents();
        setClickListeners();
        updateCurrentDate();

        // Observe BLE connection state
        observeBleConnectionState();

        // Update bluetooth status based on actual connection
        updateBluetoothStatus();

        // Load transaction stats
        loadTodayStats();

        // Set up periodic updates (every 30 seconds)
        setupPeriodicUpdates();

        return view;
    }

    private void initializeViews(View view) {
        // Quick Action Buttons
        btnNewTransaction = view.findViewById(R.id.btn_new_transaction);
        btnBluetooth = view.findViewById(R.id.btn_bluetooth);
        btnMaterials = view.findViewById(R.id.btn_materials);

        // Bluetooth Status Views
        ivBluetoothStatus = view.findViewById(R.id.iv_bluetooth_status);
        tvBluetoothStatus = view.findViewById(R.id.tv_bluetooth_status);
        ivBluetoothIndicator = view.findViewById(R.id.iv_bluetooth_indicator);
        tvBluetoothIndicator = view.findViewById(R.id.tv_bluetooth_indicator);

        // Date TextView
        tvCurrentDate = view.findViewById(R.id.tv_current_date);

        // Stats TextViews
        tvTodaySales = view.findViewById(R.id.tv_today_sales);
        tvGrowthPercentage = view.findViewById(R.id.tv_growth_percentage);
        tvTransactionCount = view.findViewById(R.id.tv_transaction_count);
        tvLastTransaction = view.findViewById(R.id.tv_last_transaction);
    }

    // Also improve the initializeDatabase method:
    private void initializeDatabase() {
        try {
            if (getContext() != null) {
                // Add retry logic
                int retryCount = 0;
                while (transactionsDBHelper == null && retryCount < 3) {
                    try {
                        transactionsDBHelper = TransactionsDBHelper.getInstance(getContext());
                        Log.d(TAG, "Database helper initialized successfully");
                        break;
                    } catch (Exception e) {
                        retryCount++;
                        Log.w(TAG, "Database initialization attempt " + retryCount + " failed", e);
                        if (retryCount < 3) {
                            try {
                                Thread.sleep(100); // Wait 100ms before retry
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }

                if (transactionsDBHelper == null) {
                    Log.e(TAG, "Failed to initialize database helper after 3 attempts");
                }
            } else {
                Log.e(TAG, "Context is null, cannot initialize database helper");
            }
        } catch (Exception e) {
            Log.e(TAG, "Fatal error initializing database helper", e);
        }
    }


    private void initializeBleComponents() {
        try {
            // ✅ SAME AS BEFORE - Get shared ViewModel instance
            bleScaleViewModel = new ViewModelProvider(requireActivity()).get(BleScaleViewModel.class);
            if (getContext() != null) {
                bleScaleViewModel.initialize(getContext());
            }

            // Initialize permission handler
            permissionHandler = new BlePermissionHandler(this, this);

            Log.d(TAG, "BLE components initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize BLE components", e);
        }
    }

    private void observeBleConnectionState() {
        if (bleScaleViewModel == null) return;

        // ✅ SAME AS BEFORE - Observe connection state
        bleScaleViewModel.getIsConnected().observe(getViewLifecycleOwner(), isConnected -> {
            if (isConnected != null) {
                isScaleConnected = isConnected;
                updateBluetoothStatus();

                // The connection now persists across fragment lifecycle!
                if (mainActivity != null) {
                    mainActivity.onScaleConnectionChanged(isScaleConnected);
                }
            }
        });

        // ✅ NEW - Observe service status for better user feedback
        bleScaleViewModel.getServiceStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null) {
                Log.d(TAG, "Service status: " + status);
                // Optionally update UI with service status
                if (tvBluetoothIndicator != null && status.contains("ready")) {
                    // Service is ready, can show positive indicators
                }
            }
        });

        // Observe device name
        bleScaleViewModel.getConnectedDeviceName().observe(getViewLifecycleOwner(), deviceName -> {
            if (deviceName != null && !deviceName.isEmpty() && isScaleConnected) {
                updateBluetoothStatusWithDeviceName(deviceName);
            }
        });

        // Observe errors
        bleScaleViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showSnackbar("Scale Error: " + error, Snackbar.LENGTH_LONG);
            }
        });
    }

    private void setClickListeners() {
        btnNewTransaction.setOnClickListener(v -> handleNewTransactionClick());
        btnBluetooth.setOnClickListener(v -> handleBluetoothClick());
        btnMaterials.setOnClickListener(v -> handleMaterialsClick());
    }

    private void updateCurrentDate() {
        if (tvCurrentDate == null) return;

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM dd", Locale.getDefault());
            Date currentDate = new Date();
            String formattedDate = dateFormat.format(currentDate);
            tvCurrentDate.setText(formattedDate);
        } catch (Exception e) {
            tvCurrentDate.setText("Today");
        }
    }

    private void loadTodayStats() {
        if (transactionsDBHelper == null) {
            Log.w(TAG, "Database helper not initialized");
            // Try to reinitialize
            initializeDatabase();
            if (transactionsDBHelper == null) {
                showDefaultStats();
                return;
            }
        }

        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "Fragment not attached or context is null");
            return;
        }

        // Use ExecutorService for better thread management
        new Thread(() -> {
            try {
                // Add a small delay to ensure fragment is properly attached
                Thread.sleep(100);

                if (!isAdded() || getContext() == null) {
                    Log.w(TAG, "Fragment detached during database operation");
                    return;
                }

                TransactionsDBHelper.TodayStats stats = transactionsDBHelper.getTodayTransactionStats();

                // Check again before updating UI
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded() && getContext() != null) {
                            updateStatsUI(stats);
                            Log.d(TAG, "Stats updated - Sales: " + stats.getFormattedTotalValue() +
                                    ", Count: " + stats.transactionCount);
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error loading today's stats", e);

                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded() && getContext() != null) {
                            showDefaultStats();
                        }
                    });
                }
            }
        }).start();
    }

    private void updateStatsUI(TransactionsDBHelper.TodayStats stats) {
        if (!isAdded() || getContext() == null) return;
// Check if license is valid for display
        OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(getContext());
        if (tvTodaySales != null) {
            tvTodaySales.setText(stats.getFormattedTotalValue());

            // Add license indicator if trial is expiring soon
            int daysRemaining = licenseManager.getDaysRemaining();
            if (daysRemaining > 0 && daysRemaining <= 7) {
                tvTodaySales.append("\n⚠️ License expires in " + daysRemaining + " days");
            }
        }
        if (tvGrowthPercentage != null) {
            tvGrowthPercentage.setText(stats.getGrowthPercentage());

            try {
                int color = android.graphics.Color.parseColor(stats.getGrowthColor());
                tvGrowthPercentage.setTextColor(color);

                if (tvTodaySales != null) {
                    tvTodaySales.setTextColor(color);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error setting growth color", e);
            }
        }

        if (tvTransactionCount != null) {
            tvTransactionCount.setText(stats.getFormattedTransactionCount());
        }

        if (tvLastTransaction != null) {
            tvLastTransaction.setText(stats.getTimeSinceLastTransaction());
        }

        Log.d(TAG, "Stats updated - Sales: " + stats.getFormattedTotalValue() +
                ", Count: " + stats.transactionCount);
    }

    private void showDefaultStats() {
        if (!isAdded() || getContext() == null) return;

        if (tvTodaySales != null) {
            tvTodaySales.setText("KSh 0");
            tvTodaySales.setTextColor(ContextCompat.getColor(getContext(), R.color.text_disabled));
        }

        if (tvGrowthPercentage != null) {
            tvGrowthPercentage.setText("No sales yet");
            tvGrowthPercentage.setTextColor(ContextCompat.getColor(getContext(), R.color.text_disabled));
        }

        if (tvTransactionCount != null) {
            tvTransactionCount.setText("0 Today");
        }

        if (tvLastTransaction != null) {
            tvLastTransaction.setText("No transactions yet");
        }
    }

    private void setupPeriodicUpdates() {
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                loadTodayStats();
                updateCurrentDate();
                updateHandler.postDelayed(this, 30000); // 30 seconds
            }
        };
    }

    private void handleNewTransactionClick() {
        // CHECK LICENSE before allowing new transaction
        if (!LicenseChecker.checkLicense(getContext(), "create new transactions")) {
            return;
        }

        showToast("Opening New Transaction...");
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).switchToTab(R.id.nav_transactions);
        }
    }

    private void handleBluetoothClick() {
        if (bleScaleViewModel != null && bleScaleViewModel.isConnectedValue()) {
            // Already connected - show connection info
            String deviceName = bleScaleViewModel.getConnectedDeviceName().getValue();
            showToast("Scale connected: " + (deviceName != null ? deviceName : "BLE Scale"));

            // Optionally show disconnect option
            showDisconnectDialog();
        } else {
            // Not connected - show connection dialog
            showBleScanDialog();
        }
    }

    private void showBleScanDialog() {
        if (permissionHandler != null && !permissionHandler.hasAllPermissions()) {
            permissionHandler.requestPermissions();
            return;
        }

        showScaleConnectionDialog();
    }

    // ✅ CONNECTION PERSISTS AUTOMATICALLY
    private void showScaleConnectionDialog() {
        // Same code as before - but now connection persists!
        if (getContext() == null || !isAdded()) {
            Log.w(TAG, "Cannot show scale connection dialog - fragment not attached");
            return;
        }

        if (bleScaleViewModel == null) {
            Log.e(TAG, "BleScaleViewModel is null, cannot show connection dialog");
            showSnackbar("Scale service not available", Snackbar.LENGTH_SHORT);
            return;
        }

        try {
            BleScaleConnectionDialog dialog = new BleScaleConnectionDialog();
            dialog.setBleScaleViewModel(bleScaleViewModel);
            dialog.setScaleConnectionListener(this);
            dialog.show(getParentFragmentManager(), "scale_connection");
            Log.d(TAG, "Scale connection dialog shown");
        } catch (Exception e) {
            Log.e(TAG, "Error showing scale connection dialog", e);
            showSnackbar("Error showing connection dialog: " + e.getMessage(), Snackbar.LENGTH_LONG);
        }
    }
    private void showDisconnectDialog() {
        if (getContext() == null || !isAdded()) return;

        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("Disconnect Scale")
                .setMessage("Are you sure you want to disconnect the scale?")
                .setPositiveButton("Disconnect", (dialog, which) -> {
                    if (bleScaleViewModel != null) {
                        bleScaleViewModel.disconnect();
                        showToast("Scale disconnected");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleMaterialsClick() {
        showToast("Opening Materials...");
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).switchToTab(R.id.nav_materials);
        }
    }

    private void updateBluetoothStatus() {
        if (getContext() == null) return;

        if (isScaleConnected) {
            updateBluetoothUI("Connected", R.color.secondary_blue, R.color.secondary_blue);
        } else {
            updateBluetoothUI("Disconnected", R.color.text_disabled, R.color.text_disabled);
        }
    }

    private void updateBluetoothStatusWithDeviceName(String deviceName) {
        if (getContext() == null) return;

        String statusText = "Connected" + (deviceName != null && !deviceName.isEmpty() ?
                " - " + deviceName : "");

        // Update status button
        if (tvBluetoothStatus != null) {
            tvBluetoothStatus.setText("Connected");
            tvBluetoothStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.secondary_blue));
        }

        // Update header indicator with device name
        if (tvBluetoothIndicator != null) {
            tvBluetoothIndicator.setText(statusText);
            tvBluetoothIndicator.setTextColor(ContextCompat.getColor(getContext(), R.color.secondary_blue));
        }

        if (ivBluetoothStatus != null) {
            ivBluetoothStatus.setColorFilter(ContextCompat.getColor(getContext(), android.R.color.white));
        }

        if (ivBluetoothIndicator != null) {
            ivBluetoothIndicator.setColorFilter(ContextCompat.getColor(getContext(), R.color.secondary_blue));
        }
    }

    private void updateBluetoothUI(String statusText, int statusColorRes, int indicatorColorRes) {
        if (getContext() == null) return;

        // Update status button
        if (tvBluetoothStatus != null) {
            tvBluetoothStatus.setText(statusText);
            tvBluetoothStatus.setTextColor(ContextCompat.getColor(getContext(), statusColorRes));
        }

        if (ivBluetoothStatus != null) {
            ivBluetoothStatus.setColorFilter(ContextCompat.getColor(getContext(), android.R.color.white));
        }

        // Update header indicator
        if (tvBluetoothIndicator != null) {
            tvBluetoothIndicator.setText(statusText);
            tvBluetoothIndicator.setTextColor(ContextCompat.getColor(getContext(), indicatorColorRes));
        }

        if (ivBluetoothIndicator != null) {
            ivBluetoothIndicator.setColorFilter(ContextCompat.getColor(getContext(), indicatorColorRes));
        }
    }

    // BleScaleConnectionDialog.ScaleConnectionListener implementations
    // ✅ ENHANCED - Now provides more reliable feedback
    @Override
    public void onScaleConnected() {
        Log.d(TAG, "Scale connected - connection will persist across app lifecycle!");

        if (getContext() != null && isAdded()) {
            showSnackbar("Scale connected and ready!", Snackbar.LENGTH_SHORT);
            Toast.makeText(getContext(), "✅ Scale Connected (Persistent)", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConnectionCancelled() {
        Log.d(TAG, "Scale connection cancelled by user");

        if (getContext() != null && isAdded()) {
            showSnackbar("Scale connection cancelled", Snackbar.LENGTH_SHORT);
        }
    }

    // BlePermissionHandler.PermissionCallback implementations
    @Override
    public void onPermissionsGranted() {
        if (getContext() != null && isAdded()) {
            showScaleConnectionDialog();
        }
    }

    @Override
    public void onPermissionsDenied(String[] deniedPermissions) {
        if (getContext() != null && isAdded()) {
            StringBuilder message = new StringBuilder("The following permissions are required for BLE scanning:\n");
            for (String permission : deniedPermissions) {
                message.append("• ").append(BlePermissionHandler.getPermissionDisplayName(permission)).append("\n");
            }
            message.append("\nPlease grant these permissions in Settings to use scale features.");

            Toast.makeText(getContext(), message.toString(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onPermissionsExplanationNeeded(String[] permissions) {
        if (getContext() != null && isAdded()) {
            new androidx.appcompat.app.AlertDialog.Builder(getContext())
                    .setTitle("Permissions Required")
                    .setMessage(BlePermissionHandler.getPermissionExplanation())
                    .setPositiveButton("Grant Permissions", (dialog, which) -> {
                        if (permissionHandler != null) {
                            permissionHandler.requestPermissions();
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Permissions are required to connect to scales", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionHandler != null) {
            permissionHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // Method to refresh all data
    public void refreshData() {
        updateCurrentDate();
        updateBluetoothStatus();
        loadTodayStats();
    }

    // Public method for external components to check scale connection
    public boolean isScaleConnected() {
        return isScaleConnected;
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSnackbar(String message, int duration) {
        if (getView() != null && getContext() != null && isAdded()) {
            try {
                Snackbar.make(getView(), message, duration).show();
            } catch (Exception e) {
                Log.w(TAG, "Could not show snackbar: " + e.getMessage());
                if (getContext() != null) {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Log.w(TAG, "Cannot show snackbar - fragment not properly attached. Message: " + message);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();

        if (updateHandler != null && updateRunnable != null) {
            updateHandler.postDelayed(updateRunnable, 30000);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mainActivity = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
        updateHandler = null;
        updateRunnable = null;
        transactionsDBHelper = null;
    }

}