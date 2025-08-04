package com.example.meruscrap;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements
        BottomNavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";

    private BottomNavigationView bottomNavigationView;
    private MaterialToolbar toolbar;

    // Permission Management
    private boolean permissionsRequested = false;
    private boolean appInitialized = false;

    // Bluetooth Printer Management
    public BluetoothPrintManager printManager; // Made public for Settings fragment access
    private BluetoothDevice connectedPrinter = null;
    private boolean printerConnected = false;

    // Bluetooth related (enhanced for printer discovery)
    private BluetoothAdapter bluetoothAdapter;
    private List<BluetoothDevice> discoveredDevices;
    private BluetoothPrinterAdapter printerAdapter;
    private boolean isScanning = false;
    private Handler mainHandler;
    private ExecutorService bluetoothExecutor;

    // Enhanced scanning parameters
    private static final int SCAN_DURATION_MS = 15000; // 15 seconds scan
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private int currentRetryAttempt = 0;
    private Runnable scanTimeoutRunnable;
    private AlertDialog currentScanDialog;

    // Preferences for storing printer info
    private SharedPreferences printerPrefs;
    private static final String PREF_PRINTER_ADDRESS = "saved_printer_address";
    private static final String PREF_PRINTER_NAME = "saved_printer_name";

    // Permissions - Version-aware Bluetooth permissions
    private static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 1001;

    // Legacy permissions for Android 11 and below
    private static final String[] BLUETOOTH_PERMISSIONS_LEGACY = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    // New permissions for Android 12+
    private static final String[] BLUETOOTH_PERMISSIONS_NEW = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    // Get appropriate permissions based on Android version
    private String[] getRequiredBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) { // Android 12+
            return BLUETOOTH_PERMISSIONS_NEW;
        } else {
            return BLUETOOTH_PERMISSIONS_LEGACY; // Android 11 and below
        }
    }
    // NEW: Request code for Bluetooth enable
    private static final int REQUEST_ENABLE_BLUETOOTH = 1002;
    // BLE Service Integration
    private BleConnectionManager bleConnectionManager;
    private boolean isScaleServiceReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set status bar and navigation bar colors
        setupSystemBars();

        // ALWAYS initialize the app - don't block on permissions
        initializeApp();

        // Then check and request permissions if needed
        if (!PermissionManager.hasAllRequiredPermissions(this) && !permissionsRequested) {
            // Show gentle permission request after app loads
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                requestAllPermissions();
            }, 500); // Small delay to let UI settle
        }
    }

    private void setupSystemBars() {
        // Set status bar color with light (white) status bar icons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.background_medium));

            // Ensure status bar icons are light/white (for dark backgrounds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getWindow().getDecorView().setSystemUiVisibility(0); // Clear any flags to keep icons light
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.background_medium));
        }
    }

    // 2. UPDATE requestAllPermissions() method
    private void requestAllPermissions() {
        if (permissionsRequested) {
            return; // Avoid multiple permission requests
        }

        List<String> missingPermissions = PermissionManager.getMissingPermissions(this);

        if (missingPermissions.isEmpty()) {
            // Permissions already granted, refresh features
            refreshFeaturesBasedOnPermissions();
            return;
        }

        permissionsRequested = true;

        // Show explanation dialog with option to skip
        PermissionManager.showPermissionExplanationDialog(this, missingPermissions,
                () -> {
                    // User wants to grant permissions
                    ActivityCompat.requestPermissions(this,
                            missingPermissions.toArray(new String[0]),
                            BLUETOOTH_PERMISSION_REQUEST_CODE);
                },
                () -> {
                    // User wants to skip for now
                    showSnackbar("You can grant permissions later in Settings", Snackbar.LENGTH_LONG);
                    // App continues to work with limited functionality
                });
    }

    // 3. UPDATE initializeApp() method
    private void initializeApp() {
        if (appInitialized) {
            return; // Prevent double initialization
        }

        appInitialized = true;

        // Apply custom font to toolbar
        setupToolbarFont();

        // Initialize basic components (always works)
        initializeViews();
        initializeToolbar();
        initializeBottomNavigation();

        // IMPORTANT: Load fragments first so UI is responsive
        bottomNavigationView.setSelectedItemId(R.id.nav_home);
        loadFragment(new Home());

        // Then try to initialize features that need permissions
        // These will gracefully fail if permissions are missing
        checkAndRequestBluetoothEnable();
        initializeBluetoothWithGracefulDegradation();
        initializeBleServiceWithGracefulDegradation();

        Log.d(TAG, "App initialized - basic functionality ready");
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_OK) {
                showSnackbar("✅ Bluetooth enabled successfully!", Snackbar.LENGTH_SHORT);
                Log.d(TAG, "Bluetooth enabled by user");

                // Now initialize Bluetooth functionality
                initializeBluetooth();

                // Notify fragments about Bluetooth state change
                notifyFragmentsOfBluetoothStateChange();
            } else {
                showSnackbar("ℹ️ Bluetooth can be enabled later in device settings", Snackbar.LENGTH_LONG);
                Log.d(TAG, "User declined to enable Bluetooth");
            }
        }
    }

    // NEW: Graceful Bluetooth initialization
    private void initializeBluetoothWithGracefulDegradation() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

            if (adapter == null) {
                Log.w(TAG, "Bluetooth not supported - skipping Bluetooth initialization");
                return;
            }

            if (!adapter.isEnabled()) {
                Log.w(TAG, "Bluetooth not enabled - skipping Bluetooth initialization");
                return;
            }

            if (!PermissionManager.hasAllRequiredPermissions(this)) {
                Log.w(TAG, "Missing Bluetooth permissions - skipping Bluetooth initialization");
                return;
            }

            // All checks passed - initialize Bluetooth
            initializeBluetooth();
            Log.d(TAG, "Bluetooth initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error during Bluetooth initialization", e);
            showSnackbar("⚠️ Bluetooth initialization failed", Snackbar.LENGTH_SHORT);
        }
    }

    // NEW: Graceful BLE service initialization
    private void initializeBleServiceWithGracefulDegradation() {
        try {
            // Check if we have location permission (required for BLE)
            if (!hasLocationPermissionForBLE()) {
                Log.w(TAG, "Missing location permission - skipping BLE service initialization");
                return;
            }

            // Initialize BLE service
            initializeBleService();
            Log.d(TAG, "BLE service initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error during BLE service initialization", e);
            showSnackbar("⚠️ BLE service initialization failed", Snackbar.LENGTH_SHORT);
        }
    }

    // NEW: Notify fragments about Bluetooth state changes
    public void notifyFragmentsOfBluetoothStateChange() {
        Fragment settingsFragment = getSupportFragmentManager().findFragmentByTag("SETTINGS");
        if (settingsFragment instanceof Settings) {
            ((Settings) settingsFragment).onPermissionsChanged();
        }

        // Notify other fragments if needed
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof Home) {
            // Home fragment can update its Bluetooth-dependent UI
        }
    }
    // 6. UPDATE checkAndRequestBluetoothEnable() to be more graceful
    private void checkAndRequestBluetoothEnable() {
        // First check if we have permissions to even check Bluetooth
        if (!hasAllBluetoothPermissions()) {
            Log.d(TAG, "Skipping Bluetooth enable check - missing permissions");
            return;
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth not supported on this device");
            // Don't show snackbar here - it's too early in startup
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            // Delay showing the dialog to let UI settle
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    showBluetoothEnableDialog();
                }
            }, 1000);
        } else {
            Log.d(TAG, "Bluetooth is already enabled");
        }
    }

    // 7. UPDATE showBluetoothEnableDialog() to be optional
    private void showBluetoothEnableDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Bluetooth")
                .setMessage("📡 MeruScrap uses Bluetooth to connect to printers and scales.\n\nWould you like to enable Bluetooth now?")
                .setIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setPositiveButton("Enable Bluetooth", (dialog, which) -> {
                    requestBluetoothEnable();
                })
                .setNegativeButton("Not Now", (dialog, which) -> {
                    // User can enable later - app continues to work
                    Log.d(TAG, "User chose to enable Bluetooth later");
                })
                .setCancelable(true) // Allow dismissing the dialog
                .show();
    }

    // NEW: Safe method to request Bluetooth enable with proper permission checking
    private void requestBluetoothEnable() {
        try {
            // Check if we have the required permission for Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, cannot request Bluetooth enable");
                    showBluetoothEnableAlternative();
                    return;
                }
            }

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when requesting Bluetooth enable", e);
            showBluetoothEnableAlternative();
        } catch (Exception e) {
            Log.e(TAG, "Error requesting Bluetooth enable", e);
            showSnackbar("Unable to enable Bluetooth automatically", Snackbar.LENGTH_SHORT);
        }
    }

    // NEW: Alternative method when we can't directly request Bluetooth enable
    private void showBluetoothEnableAlternative() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Bluetooth Manually")
                .setMessage("📱 Please enable Bluetooth manually:\n\n" +
                        "1. Open device Settings\n" +
                        "2. Go to Connected devices or Bluetooth\n" +
                        "3. Turn on Bluetooth\n" +
                        "4. Return to MeruScrap")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    try {
                        Intent settingsIntent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                        startActivity(settingsIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Could not open Bluetooth settings", e);
                        showSnackbar("Please enable Bluetooth in your device settings", Snackbar.LENGTH_LONG);
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    showSnackbar("Bluetooth is required for printer and scale features", Snackbar.LENGTH_LONG);
                })
                .show();
    }
    private void setupToolbarFont() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        try {
            Typeface customFont = Typeface.createFromAsset(getAssets(), "fonts/casanova.ttf");
            setToolbarFont(toolbar, customFont, 26f, true);
        } catch (Exception e) {
            Log.e("Font", "Could not load font from assets");
        }
    }

    private void setToolbarFont(MaterialToolbar toolbar, Typeface typeface, float textSize, boolean isBold) {
        // This method finds the title TextView inside the toolbar and sets the font, size, and style
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView) {
                TextView textView = (TextView) child;

                // Set custom font
                if (isBold) {
                    textView.setTypeface(typeface, Typeface.BOLD);
                } else {
                    textView.setTypeface(typeface);
                }

                // Set custom text size (in SP)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
            }
        }
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        mainHandler = new Handler(Looper.getMainLooper());
        bluetoothExecutor = Executors.newSingleThreadExecutor();
        discoveredDevices = new ArrayList<>();

        // Initialize preferences
        printerPrefs = getSharedPreferences("printer_settings", MODE_PRIVATE);

        // Initialize print manager
        printManager = new BluetoothPrintManager(this);
        setupPrintManagerListener();
    }

    private void initializeBleService() {
        Log.d(TAG, "Initializing BLE service integration");

        try {
            // Get the connection manager from application
            MeruScrapApplication app = (MeruScrapApplication) getApplication();
            bleConnectionManager = app.getBleConnectionManager();

            if (bleConnectionManager != null) {
                // Add MainActivity as a listener for service events
                bleConnectionManager.addListener(new BleConnectionManager.ConnectionManagerListener() {
                    @Override
                    public void onManagerReady() {
                        Log.d(TAG, "BLE service manager ready");
                        isScaleServiceReady = true;
                        runOnUiThread(() -> {
                            showSnackbar("BLE Scale service ready", Snackbar.LENGTH_SHORT);
                            updateBleServiceStatus();
                        });
                    }

                    @Override
                    public void onManagerDisconnected() {
                        Log.w(TAG, "BLE service manager disconnected");
                        isScaleServiceReady = false;
                        runOnUiThread(() -> {
                            showSnackbar("BLE service disconnected", Snackbar.LENGTH_LONG);
                            updateBleServiceStatus();
                        });
                    }

                    @Override
                    public void onConnectionStateChanged(boolean isConnected, String deviceName) {
                        Log.d(TAG, "BLE scale connection changed: " + isConnected + ", device: " + deviceName);
                        runOnUiThread(() -> {
                            // Notify current fragment about scale connection change
                            Fragment currentFragment = getCurrentFragment();
                            if (currentFragment instanceof Home) {
                                // The Home fragment will get this update through its ViewModel
                            }
                            updateBleServiceStatus();
                        });
                    }

                    @Override
                    public void onWeightReceived(double weight, boolean isStable) {
                        // Weight data is handled by ViewModels, MainActivity doesn't need to process this
                        Log.d(TAG, "Weight received in MainActivity: " + weight + " kg");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "BLE service error: " + error);
                        runOnUiThread(() -> {
                            showSnackbar("BLE Error: " + error, Snackbar.LENGTH_LONG);
                        });
                    }

                    @Override
                    public void onServiceStatusChanged(String status) {
                        Log.d(TAG, "BLE service status: " + status);
                        // You can update UI here if needed
                    }
                });

                Log.d(TAG, "BLE service integration completed");
            } else {
                Log.w(TAG, "BLE connection manager not available");
                showSnackbar("BLE service not available", Snackbar.LENGTH_LONG);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing BLE service integration", e);
            showSnackbar("Error initializing BLE service", Snackbar.LENGTH_LONG);
        }
    }

    private void updateBleServiceStatus() {
        // Update any UI elements that show BLE service status
        if (bleConnectionManager != null) {
            String status = bleConnectionManager.getConnectionStatus();
            Log.d(TAG, "BLE service status updated: " + status);
            // You can update toolbar, status indicators, etc. here
        }
    }

    // Setup the print manager status listener
    private void setupPrintManagerListener() {
        printManager.setStatusListener(new BluetoothPrintManager.PrinterStatusListener() {
            @Override
            public void onConnected() {
                printerConnected = true;
                android.util.Log.d(TAG, "Printer connected successfully");
                showSnackbar("Printer connected successfully", Snackbar.LENGTH_SHORT);

                // Update UI elements
                updatePrinterConnectionStatus();

                // ADD THIS LINE - Notify Settings fragment
                notifySettingsFragmentOfPrinterChange();
            }

            @Override
            public void onDisconnected() {
                printerConnected = false;
                connectedPrinter = null;
                android.util.Log.d(TAG, "Printer disconnected");
                showSnackbar("Printer disconnected", Snackbar.LENGTH_SHORT);

                // Update UI elements
                updatePrinterConnectionStatus();

                // ADD THIS LINE - Notify Settings fragment
                notifySettingsFragmentOfPrinterChange();
            }

            @Override
            public void onError(String error) {
                android.util.Log.e(TAG, "Printer error: " + error);
                showSnackbar("Printer error: " + error, Snackbar.LENGTH_LONG);

                // ADD THIS LINE - Notify Settings fragment of error state
                notifySettingsFragmentOfPrinterChange();
            }

            @Override
            public void onPrintJobCompleted(String jobId) {
                android.util.Log.d(TAG, "Print job completed: " + jobId);
                showSnackbar("Print job completed", Snackbar.LENGTH_SHORT);

                // ADD THIS LINE - Update Settings to refresh queue status
                notifySettingsFragmentOfPrinterChange();
            }

            @Override
            public void onPrintJobFailed(String jobId, String error) {
                android.util.Log.e(TAG, "Print job failed: " + jobId + " - " + error);
                showSnackbar("Print job failed: " + error, Snackbar.LENGTH_LONG);

                // ADD THIS LINE - Update Settings to refresh queue status
                notifySettingsFragmentOfPrinterChange();
            }

            @Override
            public void onStatusUpdate(BluetoothPrintManager.PrinterStatus status) {
                android.util.Log.d(TAG, "Printer status: " + status.name());

                String statusMessage = getPrinterStatusMessage(status);
                if (!status.equals(BluetoothPrintManager.PrinterStatus.ONLINE)) {
                    showSnackbar("Printer status: " + statusMessage, Snackbar.LENGTH_LONG);
                }

                // ADD THIS LINE - Notify Settings of status changes
                notifySettingsFragmentOfPrinterChange();
            }
        });
    }

    // Helper method to get human-readable status messages
    private String getPrinterStatusMessage(BluetoothPrintManager.PrinterStatus status) {
        switch (status) {
            case ONLINE:
                return "Ready";
            case OFFLINE:
                return "Offline";
            case PAPER_JAM:
                return "Paper jam detected";
            case NO_PAPER:
                return "Out of paper";
            case LOW_BATTERY:
                return "Low battery";
            case COVER_OPEN:
                return "Cover is open";
            case OVERHEATED:
                return "Printer overheated";
            case UNKNOWN_ERROR:
            default:
                return "Unknown error";
        }
    }

    private void initializeToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setTitle("Meru Scrap Metal Market");
        }
    }

    private void initializeBottomNavigation() {
        bottomNavigationView.setOnNavigationItemSelectedListener(this);
        // Disable icon tinting to show custom colors
        bottomNavigationView.setItemIconTintList(null);
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            showSnackbar("Bluetooth not supported on this device", Snackbar.LENGTH_LONG);
            return;
        }

        // Register for broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothReceiver, filter);

        checkBluetoothPermissions();
    }

    // Enhanced permission result handling
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            handlePermissionResults(permissions, grantResults);
        }
    }



    // ENHANCED: Better permission manager integration
    public void checkBluetoothPermissions() {
        List<String> missingPermissions = PermissionManager.getMissingPermissions(this);

        if (!missingPermissions.isEmpty()) {
            Log.d(TAG, "Missing Bluetooth permissions: " + missingPermissions);

            // Filter to only Bluetooth-related permissions
            List<String> bluetoothPermissions = new ArrayList<>();
            for (String permission : missingPermissions) {
                if (permission.contains("BLUETOOTH") || permission.contains("LOCATION")) {
                    bluetoothPermissions.add(permission);
                }
            }

            if (!bluetoothPermissions.isEmpty()) {
                // Show explanation before requesting
                PermissionManager.showPermissionRationale(this, bluetoothPermissions, () -> {
                    ActivityCompat.requestPermissions(this,
                            bluetoothPermissions.toArray(new String[0]),
                            BLUETOOTH_PERMISSION_REQUEST_CODE);
                });
            }
        } else {
            Log.d(TAG, "All required Bluetooth permissions already granted");
        }
    }

    private boolean hasAllBluetoothPermissions() {
        return PermissionManager.hasAllRequiredPermissions(this);
    }

    // Public method for fragments to check permissions
    public boolean hasBluetoothPermissionsForPrinting() {
        return PermissionManager.hasAllRequiredPermissions(this);
    }

    // Add method to check if location permissions are available for BLE
    public boolean hasLocationPermissionForBLE() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }


    // Add method to manually trigger permission request from Settings
    public void requestMissingPermissions() {
        List<String> missing = PermissionManager.getMissingPermissions(this);
        if (!missing.isEmpty()) {
            permissionsRequested = false; // Reset flag to allow new request
            requestAllPermissions();
        } else {
            showSnackbar("All permissions already granted", Snackbar.LENGTH_SHORT);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        android.util.Log.d(TAG, "Navigation selected: " + item.getTitle());

        updateToolbarTitle(itemId);

        Fragment selectedFragment = null;

        if (itemId == R.id.nav_home) {
            selectedFragment = new Home();
            android.util.Log.d(TAG, "Loading Home fragment");
        } else if (itemId == R.id.nav_materials) {
            selectedFragment = new Materials();
            android.util.Log.d(TAG, "Loading Materials fragment");
        } else if (itemId == R.id.nav_transactions) {
            selectedFragment = new Transactions();
            android.util.Log.d(TAG, "Loading Transactions fragment");
        } else if (itemId == R.id.nav_info) {
            selectedFragment = new Info();
            android.util.Log.d(TAG, "Loading Info fragment");
        } else if (itemId == R.id.nav_settings) {
            selectedFragment = new Settings();
            android.util.Log.d(TAG, "Loading Settings fragment");
        }

        if (selectedFragment != null) {
            loadFragment(selectedFragment);
            return true;
        }

        return false;
    }

    private void updateToolbarTitle(int itemId) {
        String title;
        if (itemId == R.id.nav_home) {
            title = "Dashboard";
        } else if (itemId == R.id.nav_materials) {
            title = "Materials Management";
        } else if (itemId == R.id.nav_transactions) {
            title = "New Transaction";
        } else if (itemId == R.id.nav_info) {
            title = "System Information";
        } else if (itemId == R.id.nav_settings) {
            title = "Settings & Configuration";
        } else {
            title = "Meru Scrap Metal Market";
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }
    // 10. UPDATE loadFragment to handle edge cases
    private void loadFragment(Fragment fragment) {
        try {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

            fragmentTransaction.setCustomAnimations(
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right,
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right
            );

            String tag = null;
            if (fragment instanceof Settings) {
                tag = "SETTINGS";
            }

            fragmentTransaction.replace(R.id.fragment_container, fragment, tag);
            fragmentTransaction.commitAllowingStateLoss(); // Use commitAllowingStateLoss for safety

        } catch (Exception e) {
            Log.e(TAG, "Error loading fragment", e);
            showSnackbar("Error loading screen", Snackbar.LENGTH_SHORT);
        }
    }

    public boolean isScaleServiceReady() {
        // Check if we have the necessary components
        if (bleConnectionManager == null || !hasLocationPermissionForBLE()) {
            return false;
        }
        return isScaleServiceReady && bleConnectionManager.isServiceReady();
    }

    public BleConnectionManager getBleConnectionManager() {
        return bleConnectionManager;
    }

    public boolean isScaleConnected() {
        return bleConnectionManager != null && bleConnectionManager.isConnected();
    }

    public String getConnectedScaleName() {
        return bleConnectionManager != null ? bleConnectionManager.getConnectedDeviceName() : "";
    }

    public double getCurrentScaleWeight() {
        return bleConnectionManager != null ? bleConnectionManager.getCurrentWeight() : 0.0;
    }

    public void disconnectScale() {
        if (bleConnectionManager != null) {
            bleConnectionManager.disconnect();
        }
    }

    public void tareScale() {
        if (bleConnectionManager != null) {
            bleConnectionManager.tare();
        } else {
            showSnackbar("BLE service not available", Snackbar.LENGTH_SHORT);
        }
    }

    public String getBleServiceDiagnostics() {
        if (bleConnectionManager != null) {
            return bleConnectionManager.getDetailedStatus();
        }
        return "BLE service not available";
    }
    // Add this method to MainActivity.java
    public void notifyFragmentsOfPermissionChange() {
        // Notify Settings fragment about permission changes
        Fragment settingsFragment = getSupportFragmentManager().findFragmentByTag("SETTINGS");
        if (settingsFragment instanceof Settings) {
            ((Settings) settingsFragment).onPermissionsChanged();
        }
    }
    // =================================================================
    // PRINTER MANAGEMENT METHODS (for Settings fragment)
    // =================================================================

    // Enhanced printer scanning with timeout and retry logic
    public void showPrinterScanDialog() {
        // Check Bluetooth permissions first
        if (!hasAllBluetoothPermissions()) {
            showSnackbar("Bluetooth permissions required for printer functionality", Snackbar.LENGTH_LONG);
            checkBluetoothPermissions();
            return;
        }

        // Check if Bluetooth is enabled
        if (bluetoothAdapter == null) {
            showSnackbar("Bluetooth not supported on this device", Snackbar.LENGTH_LONG);
            return;
        }

        // AFTER:
        if (!bluetoothAdapter.isEnabled()) {
            showSnackbar("Please enable Bluetooth to scan for printers", Snackbar.LENGTH_LONG);
            requestBluetoothEnable();  // <- Use the safe method
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_printer_scan, null);

        RecyclerView recyclerView = dialogView.findViewById(R.id.rv_printers);
        TextView scanStatusText = dialogView.findViewById(R.id.tv_scan_status);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        printerAdapter = new BluetoothPrinterAdapter(discoveredDevices, this::onPrinterSelected);
        recyclerView.setAdapter(printerAdapter);

        currentScanDialog = builder.setView(dialogView)
                .setTitle("Scan for Bluetooth Printers")
                .setNegativeButton("Cancel", (d, w) -> {
                    stopBluetoothScan();
                    currentScanDialog = null;
                })
                .setNeutralButton("Retry", (d, w) -> {
                    currentRetryAttempt = 0;
                    startEnhancedBluetoothScan(scanStatusText);
                })
                .setOnDismissListener(dialog -> {
                    stopBluetoothScan();
                    currentScanDialog = null;
                })
                .create();

        currentScanDialog.show();

        // Reset retry counter and start scanning
        currentRetryAttempt = 0;
        startEnhancedBluetoothScan(scanStatusText);
    }

    private void startEnhancedBluetoothScan(TextView statusText) {
        if (!hasAllBluetoothPermissions()) {
            if (statusText != null) {
                statusText.setText("❌ Bluetooth permissions required");
            }
            showSnackbar("Bluetooth permissions required for printer functionality", Snackbar.LENGTH_LONG);
            return;
        }

        // Clear previous results
        discoveredDevices.clear();
        if (printerAdapter != null) {
            printerAdapter.notifyDataSetChanged();
        }

        // Cancel any ongoing discovery
        cancelBluetoothDiscovery();

        try {
            currentRetryAttempt++;
            isScanning = true;

            if (statusText != null) {
                statusText.setText("🔍 Scanning for printers... (Attempt " + currentRetryAttempt + "/" + MAX_RETRY_ATTEMPTS + ")");
            }

            boolean scanStarted = bluetoothAdapter.startDiscovery();
            if (!scanStarted) {
                handleScanFailure(statusText, "Failed to start Bluetooth scan");
                return;
            }

            showSnackbar("Scanning for Bluetooth printers...", Snackbar.LENGTH_SHORT);

            // Set up scan timeout
            scanTimeoutRunnable = () -> {
                if (isScanning) {
                    stopBluetoothScan();
                    handleScanTimeout(statusText);
                }
            };
            mainHandler.postDelayed(scanTimeoutRunnable, SCAN_DURATION_MS);

        } catch (SecurityException e) {
            handleScanFailure(statusText, "Bluetooth permissions required");
            showSnackbar("Bluetooth permissions required for printer functionality", Snackbar.LENGTH_LONG);
            checkBluetoothPermissions();
        } catch (Exception e) {
            handleScanFailure(statusText, "Scan failed: " + e.getMessage());
        }
    }

    private void handleScanTimeout(TextView statusText) {
        if (discoveredDevices.isEmpty()) {
            if (currentRetryAttempt < MAX_RETRY_ATTEMPTS) {
                if (statusText != null) {
                    statusText.setText("🔄 No printers found, retrying...");
                }
                mainHandler.postDelayed(() -> startEnhancedBluetoothScan(statusText), 2000);
            } else {
                if (statusText != null) {
                    statusText.setText("❌ No Bluetooth printers found after " + MAX_RETRY_ATTEMPTS + " attempts");
                }
                showSnackbar("No Bluetooth printers found. Make sure printer is in pairing mode.", Snackbar.LENGTH_LONG);
            }
        } else {
            if (statusText != null) {
                statusText.setText("✅ Found " + discoveredDevices.size() + " printer(s). Tap to connect.");
            }
        }
    }

    private void handleScanFailure(TextView statusText, String error) {
        isScanning = false;
        if (statusText != null) {
            statusText.setText("❌ " + error);
        }
        showSnackbar(error, Snackbar.LENGTH_LONG);
    }

    private void stopBluetoothScan() {
        isScanning = false;

        // Cancel timeout
        if (scanTimeoutRunnable != null) {
            mainHandler.removeCallbacks(scanTimeoutRunnable);
            scanTimeoutRunnable = null;
        }

        // Stop discovery
        cancelBluetoothDiscovery();
    }

    public void showPairedDevicesDialog() {
        if (!hasBluetoothPermissions()) {
            checkBluetoothPermissions();
            return;
        }

        Set<BluetoothDevice> pairedDevices;
        try {
            pairedDevices = bluetoothAdapter.getBondedDevices();
        } catch (SecurityException e) {
            showSnackbar("Bluetooth permissions required", Snackbar.LENGTH_SHORT);
            checkBluetoothPermissions();
            return;
        }

        List<BluetoothDevice> printerDevices = new ArrayList<>();

        for (BluetoothDevice device : pairedDevices) {
            String deviceName = getDeviceNameSafely(device);
            if (isPrinterDevice(deviceName)) {
                printerDevices.add(device);
            }
        }

        if (printerDevices.isEmpty()) {
            showSnackbar("No paired printer devices found", Snackbar.LENGTH_SHORT);
            return;
        }

        String[] deviceNames = new String[printerDevices.size()];
        for (int i = 0; i < printerDevices.size(); i++) {
            deviceNames[i] = getDeviceNameSafely(printerDevices.get(i));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Paired Printer");
        builder.setItems(deviceNames, (dialog, which) -> {
            onPrinterSelected(printerDevices.get(which));
        });
        builder.show();
    }

    private boolean isPrinterDevice(String deviceName) {
        if (deviceName == null) return false;

        String lowerDeviceName = deviceName.toLowerCase();

        // QUICK FIX: Allow "Unknown Device" to pass through for manual selection
        if (deviceName.equals("Unknown Device")) {
            android.util.Log.d(TAG, "Allowing Unknown Device to pass through filter");
            return true; // Let user decide if it's their printer
        }

        // Your existing printer identifiers (keeping all your current logic)
        String[] printerIdentifiers = {
                "printer", "print", "pos", "receipt", "thermal", "label",
                // Major printer brands
                "epson", "canon", "hp", "brother", "zebra", "star", "citizen", "bixolon",
                "seiko", "rongta", "xprinter", "godex", "tsc", "datamax", "intermec",
                "sato", "cab", "avery", "brady", "dymo", "rollo",
                // POS and receipt printer specific
                "tm-", "rp-", "ct-", "tsp", "ftp", "sp700", "sp742", "sp847",
                "bluetooth printer", "bt printer", "mobile printer", "portable printer",
                // Model patterns that indicate printers
                "58mm", "80mm", "110mm", "203dpi", "300dpi",
                // Asian printer brands
                "gprinter", "jiabo", "zjiang", "milestone", "goojprt", "netum"
        };

        for (String identifier : printerIdentifiers) {
            if (lowerDeviceName.contains(identifier)) {
                android.util.Log.d(TAG, "Identified printer by keyword '" + identifier + "': " + deviceName);
                return true;
            }
        }

        // Check for model number patterns that suggest printers
        if (lowerDeviceName.matches(".*[a-z]{2,4}[-_]?\\d{3,4}.*") ||  // Brand-number pattern
                lowerDeviceName.matches(".*\\d{2,3}mm.*") ||                 // Size patterns
                lowerDeviceName.matches(".*dpi.*")) {                        // DPI patterns
            android.util.Log.d(TAG, "Identified printer by pattern: " + deviceName);
            return true;
        }

        android.util.Log.d(TAG, "Not identified as printer: " + deviceName);
        return false;
    }

    // Show all devices dialog (for debugging)
    public void showAllDevicesDialog() {
        if (!hasAllBluetoothPermissions()) {
            showSnackbar("Bluetooth permissions required", Snackbar.LENGTH_LONG);
            checkBluetoothPermissions();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            showSnackbar("Please enable Bluetooth", Snackbar.LENGTH_LONG);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_printer_scan, null);

        RecyclerView recyclerView = dialogView.findViewById(R.id.rv_printers);
        TextView scanStatusText = dialogView.findViewById(R.id.tv_scan_status);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Create adapter that shows ALL devices (not filtered)
        BluetoothAllDevicesAdapter allDevicesAdapter = new BluetoothAllDevicesAdapter(discoveredDevices, device -> {
            // Show confirmation dialog since this might not be a printer
            new AlertDialog.Builder(this)
                    .setTitle("Connect to Device?")
                    .setMessage("Device: " + getDeviceNameSafely(device) +
                            "\nMAC: " + device.getAddress() +
                            "\n\n⚠️ This device may not be a printer. Continue?")
                    .setPositiveButton("Connect", (d, w) -> {
                        onPrinterSelected(device);
                        currentScanDialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        recyclerView.setAdapter(allDevicesAdapter);

        currentScanDialog = builder.setView(dialogView)
                .setTitle("All Bluetooth Devices (Debug Mode)")
                .setMessage("⚠️ This shows ALL devices, not just printers")
                .setNegativeButton("Cancel", (d, w) -> {
                    stopBluetoothScan();
                    currentScanDialog = null;
                })
                .setNeutralButton("Normal Scan", (d, w) -> {
                    currentScanDialog.dismiss();
                    showPrinterScanDialog();
                })
                .setOnDismissListener(dialog -> {
                    stopBluetoothScan();
                    currentScanDialog = null;
                })
                .create();

        currentScanDialog.show();

        // Start scanning for ALL devices
        startAllDevicesScan(scanStatusText, allDevicesAdapter);
    }

    // Modified scan method that doesn't filter devices
    private void startAllDevicesScan(TextView statusText, BluetoothAllDevicesAdapter adapter) {
        if (!hasAllBluetoothPermissions()) {
            if (statusText != null) {
                statusText.setText("❌ Bluetooth permissions required");
            }
            return;
        }

        // Clear previous results
        discoveredDevices.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        // Cancel any ongoing discovery
        cancelBluetoothDiscovery();

        try {
            isScanning = true;

            if (statusText != null) {
                statusText.setText("🔍 Scanning for ALL Bluetooth devices...");
            }

            boolean scanStarted = bluetoothAdapter.startDiscovery();
            if (!scanStarted) {
                handleScanFailure(statusText, "Failed to start Bluetooth scan");
                return;
            }

            showSnackbar("Scanning for all Bluetooth devices...", Snackbar.LENGTH_SHORT);

            // Set up scan timeout
            scanTimeoutRunnable = () -> {
                if (isScanning) {
                    stopBluetoothScan();
                    if (statusText != null) {
                        statusText.setText("✅ Scan complete. Found " + discoveredDevices.size() + " device(s)");
                    }
                }
            };
            mainHandler.postDelayed(scanTimeoutRunnable, SCAN_DURATION_MS);

        } catch (SecurityException e) {
            handleScanFailure(statusText, "Bluetooth permissions required");
        } catch (Exception e) {
            handleScanFailure(statusText, "Scan failed: " + e.getMessage());
        }
    }

    // Method called when printer is selected (now notifies Settings fragment)
    private void onPrinterSelected(BluetoothDevice device) {
        // Save printer info
        savePrinterInfo(device);

        // Set as connected printer
        connectedPrinter = device;

        // Connect using print manager
        printManager.connect(device);

        // Show connecting message
        showSnackbar("Connecting to " + getDeviceNameSafely(device) + "...", Snackbar.LENGTH_SHORT);

        // Notify current fragment if it's Settings
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof Settings) {
            ((Settings) currentFragment).onPrinterSelected(device);
        }
    }

    private void savePrinterInfo(BluetoothDevice device) {
        SharedPreferences.Editor editor = printerPrefs.edit();
        editor.putString(PREF_PRINTER_ADDRESS, device.getAddress());
        editor.putString(PREF_PRINTER_NAME, getDeviceNameSafely(device));
        editor.apply();
    }

    public void clearSavedPrinter() {
        SharedPreferences.Editor editor = printerPrefs.edit();
        editor.remove(PREF_PRINTER_ADDRESS);
        editor.remove(PREF_PRINTER_NAME);
        editor.apply();
        showSnackbar("Saved printer cleared", Snackbar.LENGTH_SHORT);
    }



    // Enhanced Bluetooth device discovery receiver
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    try {
                        String deviceName = getDeviceNameSafely(device);
                        android.util.Log.d(TAG, "Found device: " + deviceName + " (" + device.getAddress() + ")");

                        if (isPrinterDevice(deviceName)) {
                            if (!discoveredDevices.contains(device)) {
                                discoveredDevices.add(device);
                                android.util.Log.d(TAG, "Added printer: " + deviceName);

                                if (printerAdapter != null) {
                                    mainHandler.post(() -> {
                                        printerAdapter.notifyItemInserted(discoveredDevices.size() - 1);
                                        // Update scan status if dialog is showing
                                        if (currentScanDialog != null && currentScanDialog.isShowing()) {
                                            TextView statusText = currentScanDialog.findViewById(R.id.tv_scan_status);
                                            if (statusText != null) {
                                                statusText.setText("🔍 Found " + discoveredDevices.size() + " printer(s)...");
                                            }
                                        }
                                    });
                                }
                            }
                        } else {
                            android.util.Log.d(TAG, "Skipped non-printer device: " + deviceName);
                        }
                    } catch (Exception e) {
                        android.util.Log.w(TAG, "Error processing discovered device", e);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                android.util.Log.d(TAG, "Discovery finished. Found " + discoveredDevices.size() + " printers");
                debugPrintDiscoveredDevices();

                // Cancel timeout since discovery finished naturally
                if (scanTimeoutRunnable != null) {
                    mainHandler.removeCallbacks(scanTimeoutRunnable);
                    scanTimeoutRunnable = null;
                }

                isScanning = false;

                mainHandler.post(() -> {
                    if (currentScanDialog != null && currentScanDialog.isShowing()) {
                        TextView statusText = currentScanDialog.findViewById(R.id.tv_scan_status);

                        if (discoveredDevices.isEmpty()) {
                            if (currentRetryAttempt < MAX_RETRY_ATTEMPTS) {
                                if (statusText != null) {
                                    statusText.setText("🔄 No printers found, retrying...");
                                }
                                mainHandler.postDelayed(() -> startEnhancedBluetoothScan(statusText), 2000);
                            } else {
                                if (statusText != null) {
                                    statusText.setText("❌ No Bluetooth printers found after " + MAX_RETRY_ATTEMPTS + " attempts");
                                }
                                showSnackbar("No Bluetooth printers found. Ensure printer is in pairing mode and nearby.", Snackbar.LENGTH_LONG);
                            }
                        } else {
                            if (statusText != null) {
                                statusText.setText("✅ Found " + discoveredDevices.size() + " printer(s). Tap to connect.");
                            }
                            showSnackbar("Found " + discoveredDevices.size() + " printer(s). Tap to connect.", Snackbar.LENGTH_SHORT);
                        }
                    }
                });
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    android.util.Log.d(TAG, "Device disconnected: " + getDeviceNameSafely(device));

                    // If this was our connected printer, clear the connection
                    if (connectedPrinter != null && device.getAddress().equals(connectedPrinter.getAddress())) {
                        disconnectPrinter();
                        showSnackbar("Printer disconnected", Snackbar.LENGTH_SHORT);
                    }

                    // Notify current fragment if it's Settings
                    Fragment currentFragment = getCurrentFragment();
                    if (currentFragment instanceof Settings) {
                        ((Settings) currentFragment).onPrinterDisconnected(device);
                    }
                }
            }
        }
    };

    public void onScaleConnectionChanged(boolean isConnected) {
        // Update any MainActivity-level connection tracking if needed
    }

    // =================================================================
    // PRINTER UTILITY METHODS
    // =================================================================

    // Method to print receipt data
    public void printReceipt(String receiptContent) {

        // CHECK LICENSE for printing
        if (!LicenseChecker.checkLicense(this, "print receipts")) {
            return;
        }
        if (!isPrinterConnected()) {
            showSnackbar("No printer connected", Snackbar.LENGTH_SHORT);
            return;
        }

        if (printManager.isPrinting()) {
            showSnackbar("Printer is busy, adding to queue", Snackbar.LENGTH_SHORT);
        }

        // Generate unique job ID
        String jobId = "receipt_" + System.currentTimeMillis();

        // Add receipt print job to queue
        printManager.addPrintJob(jobId, receiptContent, BluetoothPrintManager.PrintJobType.RECEIPT);

        showSnackbar("Receipt queued for printing", Snackbar.LENGTH_SHORT);
    }

    // 9. Make printer/BLE methods more defensive
    public boolean isPrinterConnected() {
        // Check if we have the necessary components
        if (printManager == null || !hasAllBluetoothPermissions()) {
            return false;
        }
        return printerConnected && connectedPrinter != null;
    }

    // Method to get the currently connected printer
    public BluetoothDevice getConnectedPrinter() {
        return connectedPrinter;
    }

    // Method to update UI elements when printer connection status changes
    private void updatePrinterConnectionStatus() {
        // Notify fragments about connection status change
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof Settings) {
            ((Settings) currentFragment).updatePreferenceStates();
        }
    }

    // Method to get printer status details
    public String getPrinterStatusDetails() {
        if (!isPrinterConnected()) {
            return "No printer connected";
        }

        if (printManager.isPrinting()) {
            int queueSize = printManager.getQueueSize();
            return "Printing... (" + queueSize + " jobs in queue)";
        }

        return "Printer ready";
    }

    // Method to check if printer is ready for new jobs
    public boolean isPrinterReady() {
        return isPrinterConnected() && !printManager.isPrinting();
    }

    // Method to get print queue information
    public String getPrintQueueInfo() {
        if (printManager == null) {
            return "Print manager not initialized";
        }

        int queueSize = printManager.getQueueSize();
        boolean isPrinting = printManager.isPrinting();

        if (queueSize == 0 && !isPrinting) {
            return "No pending print jobs";
        } else if (isPrinting && queueSize == 0) {
            return "Currently printing";
        } else if (isPrinting) {
            return "Currently printing (" + queueSize + " jobs waiting)";
        } else {
            return queueSize + " jobs in queue";
        }
    }

    public void clearPrintQueue() {
        if (printManager != null) {
            printManager.clearQueue();
            showSnackbar("Print queue cleared", Snackbar.LENGTH_SHORT);

            // ADD THIS LINE - Notify Settings to refresh queue status
            notifySettingsFragmentOfPrinterChange();
        }
    }

    // Method to retry auto-connect to saved printer
    public void autoConnectToSavedPrinter() {
        if (!hasAllBluetoothPermissions()) {
            android.util.Log.d(TAG, "Cannot auto-connect: missing Bluetooth permissions");
            return;
        }

        String savedAddress = printerPrefs.getString(PREF_PRINTER_ADDRESS, null);
        if (savedAddress != null) {
            try {
                BluetoothDevice savedDevice = bluetoothAdapter.getRemoteDevice(savedAddress);
                android.util.Log.d(TAG, "Auto-connecting to saved printer: " + savedAddress);

                connectedPrinter = savedDevice;
                printManager.connect(savedDevice);

            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to auto-connect to saved printer", e);
            }
        }
    }

    // Method to set connected printer (call this when printer connects)
    public void setConnectedPrinter(BluetoothDevice device) {
        this.connectedPrinter = device;
        this.printerConnected = (device != null);

        // Update any fragments that need to know about printer status
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof Settings) {
            // Refresh the settings fragment to update button states
            ((Settings) currentFragment).updatePreferenceStates();
        }

        // ADD THIS LINE - Notify Settings fragment
        notifySettingsFragmentOfPrinterChange();

        android.util.Log.d(TAG, "Connected printer set to: " +
                (device != null ? getDeviceNameSafely(device) : "null"));
    }

    public void disconnectPrinter() {
        if (printManager != null) {
            printManager.disconnect();
        }

        connectedPrinter = null;
        printerConnected = false;

        android.util.Log.d(TAG, "Printer disconnected");

        // Update UI
        updatePrinterConnectionStatus();

        // ADD THIS LINE - Notify Settings fragment
        notifySettingsFragmentOfPrinterChange();
    }

    // Fix the printTestPage method by updating JobType and JobStatus references
    public void printTestPage() {
        if (!isPrinterConnected()) {
            showSnackbar("No printer connected", Snackbar.LENGTH_SHORT);
            return;
        }

        try {
            // Create test print data
            String testData = generateTestPageData();

            // Send to printer
            sendToPrinter(testData);

            // Log the print job - FIX: Use PrintHistoryManager enums
            PrintHistoryManager historyManager = PrintHistoryManager.getInstance(this);
            historyManager.addPrintJob(createPrintJob("Test Page",
                    PrintHistoryManager.PrintJobType.TEST_PAGE,
                    PrintHistoryManager.PrintJobStatus.COMPLETED,
                    testData));

            showSnackbar("Test page sent to printer", Snackbar.LENGTH_SHORT);

        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to print test page", e);

            // Log failed print job - FIX: Use PrintHistoryManager enums
            PrintHistoryManager historyManager = PrintHistoryManager.getInstance(this);
            historyManager.addPrintJob(createPrintJob("Test Page",
                    PrintHistoryManager.PrintJobType.TEST_PAGE,
                    PrintHistoryManager.PrintJobStatus.FAILED,
                    "Test print failed: " + e.getMessage()));

            showSnackbar("Failed to print test page", Snackbar.LENGTH_SHORT);
        }
    }

    /**
     * Helper method to create a PrintJob object
     */
    private PrintHistoryManager.PrintJob createPrintJob(String contentPreview,
                                                        PrintHistoryManager.PrintJobType jobType,
                                                        PrintHistoryManager.PrintJobStatus status,
                                                        String fullContent) {

        PrintHistoryManager.PrintJob job = new PrintHistoryManager.PrintJob();
        job.jobId = "job_" + System.currentTimeMillis();
        job.jobType = jobType;
        job.contentPreview = contentPreview;
        job.status = status;
        job.createdAt = new java.util.Date();

        // Set printer info if available
        if (connectedPrinter != null) {
            job.printerAddress = connectedPrinter.getAddress();
            job.printerName = getDeviceNameSafely(connectedPrinter);
        }

        return job;
    }

    /**
     * Send data to the connected printer
     * @param data The string data to print
     */
    private void sendToPrinter(String data) throws Exception {
        if (!isPrinterConnected()) {
            android.util.Log.w(TAG, "Cannot send to printer - no printer connected");
            return;
        }

        try {
            // Use the print manager to send the data
            if (printManager != null) {
                String jobId = "manual_print_" + System.currentTimeMillis();
                printManager.addPrintJob(jobId, data, BluetoothPrintManager.PrintJobType.TEST_PAGE);
                android.util.Log.d(TAG, "Data sent to printer via print manager");
            } else {
                android.util.Log.e(TAG, "Print manager is null");
                throw new Exception("Print manager not initialized");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to send data to printer", e);
            throw e;
        }
    }

    // Generate test page content
    private String generateTestPageData() {
        StringBuilder testData = new StringBuilder();

        // ESC/POS commands for thermal printers
        testData.append("\u001b@"); // Initialize printer
        testData.append("\u001b\u0061\u0001"); // Center align

        // Header
        testData.append("PRINTER TEST PAGE\n");
        testData.append("================\n\n");

        // Left align
        testData.append("\u001b\u0061\u0000");

        // Test different text styles
        testData.append("Normal Text\n");
        testData.append("\u001b\u0045\u0001Bold Text\u001b\u0045\u0000\n"); // Bold on/off
        testData.append("\u001b\u0021\u0010Small Text\u001b\u0021\u0000\n"); // Small text
        testData.append("\u001b\u0021\u0020Large Text\u001b\u0021\u0000\n"); // Large text

        testData.append("\n");

        // Test characters
        testData.append("Special Characters:\n");
        testData.append("áéíóúñü ÁÉÍÓÚÑÜ\n");
        testData.append("123456789 !@#$%^&*()\n");

        testData.append("\n");

        // Test alignment
        testData.append("\u001b\u0061\u0000Left Aligned\n");
        testData.append("\u001b\u0061\u0001Center Aligned\n");
        testData.append("\u001b\u0061\u0002Right Aligned\n");
        testData.append("\u001b\u0061\u0000"); // Reset to left

        testData.append("\n");

        // Date and time
        testData.append("Date: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date()) + "\n");

        // Device info
        testData.append("Device: " + (connectedPrinter != null ? getDeviceNameSafely(connectedPrinter) : "Unknown") + "\n");
        testData.append("MAC: " + (connectedPrinter != null ? connectedPrinter.getAddress() : "Unknown") + "\n");

        testData.append("\n");
        testData.append("Test completed successfully!\n");

        // Cut paper if supported
        testData.append("\u001d\u0056\u0041\u0010"); // Partial cut

        return testData.toString();
    }

    // =================================================================
    // UTILITY AND HELPER METHODS
    // =================================================================

    // Enhanced snackbar method (replace the existing showSnackbar method)
    private void showSnackbar(String message, int duration) {
        try {
            Snackbar.make(findViewById(android.R.id.content), message, duration).show();
        } catch (Exception e) {
            Log.w(TAG, "Could not show snackbar, falling back to Toast");
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
        }
    }



    @Override
    public void onBackPressed() {
        if (bottomNavigationView.getSelectedItemId() != R.id.nav_home) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }

    public Fragment getCurrentFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.fragment_container);

        if (fragment == null) {
            android.util.Log.w(TAG, "No fragment found in container");
        } else {
            android.util.Log.d(TAG, "Found fragment: " + fragment.getClass().getSimpleName());
        }

        return fragment;
    }

    public void switchToTab(int navItemId) {
        bottomNavigationView.setSelectedItemId(navItemId);
    }


    // Helper methods
    private String getDeviceNameSafely(BluetoothDevice device) {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                String name = device.getName();
                return name != null ? name : "Unknown Device";
            }
        } catch (SecurityException e) {
            android.util.Log.w(TAG, "SecurityException while getting device name", e);
        }
        return "Unknown Device";
    }

    private boolean hasBluetoothPermissions() {
        // For Android 12+, check the new permissions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            // For Android 11 and below, check legacy permissions
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void cancelBluetoothDiscovery() {
        try {
            if (bluetoothAdapter != null && hasBluetoothPermissions() && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException e) {
            android.util.Log.w(TAG, "SecurityException while canceling discovery", e);
        }
    }

    public void debugPrintDiscoveredDevices() {
        android.util.Log.d(TAG, "=== DISCOVERED DEVICES DEBUG ===");
        android.util.Log.d(TAG, "discoveredDevices.size(): " + discoveredDevices.size());

        for (int i = 0; i < discoveredDevices.size(); i++) {
            BluetoothDevice device = discoveredDevices.get(i);
            String name = getDeviceNameSafely(device);
            String address = device.getAddress();
            android.util.Log.d(TAG, "Device " + i + ": " + name + " (" + address + ")");
        }
        android.util.Log.d(TAG, "=== END DEBUG ===");
    }

    // 8. UPDATE onResume() to be more graceful
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "MainActivity resumed");

        // Always ensure app is initialized
        if (!appInitialized) {
            initializeApp();
        }

        // Check if permissions changed while app was in background
        boolean hasAllPermissions = PermissionManager.hasAllRequiredPermissions(this);

        if (hasAllPermissions && (bluetoothAdapter == null || printManager == null || bleConnectionManager == null)) {
            // We got permissions while away, initialize features
            refreshFeaturesBasedOnPermissions();
        }

        // Notify fragments of any changes
        notifyFragmentsOfPermissionChange();

        // Update BLE service status when returning to app
        if (bleConnectionManager != null) {
            updateBleServiceStatus();
        }
        // Check and show license warning if needed
        checkAndShowLicenseWarning();
    }
    public String getPermissionStatusSummary() {
        List<String> missing = PermissionManager.getMissingPermissions(this);
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        StringBuilder status = new StringBuilder();

        if (missing.isEmpty()) {
            status.append("✅ All permissions granted");
        } else {
            status.append("⚠️ ").append(missing.size()).append(" permission(s) missing");
        }

        // Add Bluetooth status
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isEnabled()) {
                status.append(" | 📡 Bluetooth ON");
            } else {
                status.append(" | 📡 Bluetooth OFF");
            }
        }

        return status.toString();
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "MainActivity paused");

        // The BLE service continues running in background
        // No need to disconnect or stop anything
    }

    @Override
    protected void onDestroy() {
        // Shutdown print manager
        if (printManager != null) {
            printManager.shutdown();
        }

        // Your existing onDestroy code...
        super.onDestroy();
        try {
            unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered
        }

        stopBluetoothScan();

        if (currentScanDialog != null && currentScanDialog.isShowing()) {
            currentScanDialog.dismiss();
            currentScanDialog = null;
        }

        if (bluetoothExecutor != null) {
            bluetoothExecutor.shutdown();
        }
    }

    // Helper class for scrap items
    public static class ScrapItem {
        public String materialType;
        public double weight;
        public double pricePerKg;
        public double totalPrice;

        public ScrapItem(String materialType, double weight, double pricePerKg) {
            this.materialType = materialType;
            this.weight = weight;
            this.pricePerKg = pricePerKg;
            this.totalPrice = weight * pricePerKg;
        }
    }

    // Add these methods to MainActivity class
    public void reprintReceipt(String jobId) {
        PrintHistoryManager.PrintJob job = PrintHistoryManager.getInstance(this).getPrintJobById(jobId);
        if (job != null && job.contentPreview != null) {
            // Mark job for reprint
            PrintHistoryManager.getInstance(this).markJobForReprint(jobId);

            // Print the saved content using your BluetoothPrintManager's addPrintJob method
            if (printManager != null && isPrinterConnected()) {
                // Convert PrintHistoryManager.PrintJobType to BluetoothPrintManager.PrintJobType
                BluetoothPrintManager.PrintJobType printType = convertJobType(job.jobType);

                // Add to print queue with reprint prefix
                String reprintJobId = "reprint_" + jobId + "_" + System.currentTimeMillis();
                printManager.addPrintJob(reprintJobId, job.contentPreview, printType);

                // Update the job status in history
                PrintHistoryManager.getInstance(this).updatePrintJobStatus(jobId,
                        PrintHistoryManager.PrintJobStatus.PRINTING, null);

                // ADD THIS LINE - Notify Settings to refresh queue status
                notifySettingsFragmentOfPrinterChange();

            } else {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, "Printer not connected. Cannot reprint.",
                            android.widget.Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    public void printPendingJobs() {
        List<PrintHistoryManager.PrintJob> pendingJobs =
                PrintHistoryManager.getInstance(this).getPendingPrintJobs();

        if (pendingJobs.isEmpty()) {
            android.widget.Toast.makeText(this, "No pending print jobs",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isPrinterConnected()) {
            android.widget.Toast.makeText(this, "Printer not connected",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // Print all pending jobs using your BluetoothPrintManager's addPrintJob method
        int jobsAdded = 0;
        for (PrintHistoryManager.PrintJob job : pendingJobs) {
            if (job.contentPreview != null) {
                // Convert job type
                BluetoothPrintManager.PrintJobType printType = convertJobType(job.jobType);

                // Add to print queue with pending prefix
                String pendingJobId = "pending_" + job.jobId + "_" + System.currentTimeMillis();
                printManager.addPrintJob(pendingJobId, job.contentPreview, printType);

                // Update status to printing
                PrintHistoryManager.getInstance(this).updatePrintJobStatus(job.jobId,
                        PrintHistoryManager.PrintJobStatus.PRINTING, null);

                jobsAdded++;
            }
        }

        android.widget.Toast.makeText(this,
                "Added " + jobsAdded + " jobs to print queue",
                android.widget.Toast.LENGTH_SHORT).show();

        // ADD THIS LINE - Notify Settings to refresh queue status
        notifySettingsFragmentOfPrinterChange();
    }

    // Add this helper method to convert between job types
    private BluetoothPrintManager.PrintJobType convertJobType(PrintHistoryManager.PrintJobType historyJobType) {
        switch (historyJobType) {
            case RECEIPT:
                return BluetoothPrintManager.PrintJobType.RECEIPT;
            case REPORT:
                return BluetoothPrintManager.PrintJobType.REPORT;
            case TEST_PAGE:
                return BluetoothPrintManager.PrintJobType.TEST_PAGE;
            case LABEL:
            case BARCODE:
            default:
                // Default to RECEIPT for unsupported types
                return BluetoothPrintManager.PrintJobType.RECEIPT;
        }
    }

    // Add this method to MainActivity.java
    private void notifySettingsFragmentOfPrinterChange() {
        // Find the Settings fragment and update its status
        Fragment settingsFragment = getSupportFragmentManager().findFragmentByTag("SETTINGS");
        if (settingsFragment instanceof Settings) {
            ((Settings) settingsFragment).refreshPrinterStatus();
        }
    }

    public List<PrintHistoryManager.PrintJob> getReprintableJobs() {
        return PrintHistoryManager.getInstance(this).getAllReprintablePrintJobs();
    }
    // 4. UPDATE handlePermissionResults() method
    private void handlePermissionResults(String[] permissions, int[] grantResults) {
        List<String> deniedPermissions = new ArrayList<>();
        List<String> grantedPermissions = new ArrayList<>();
        List<String> permanentlyDeniedPermissions = new ArrayList<>();

        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                String permission = permissions[i];
                deniedPermissions.add(permission);

                // Check if permission is permanently denied
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    permanentlyDeniedPermissions.add(permission);
                }
            } else {
                grantedPermissions.add(permissions[i]);
            }
        }

        // App is already initialized, just refresh features based on new permissions
        refreshFeaturesBasedOnPermissions();

        // Show appropriate feedback based on results
        if (deniedPermissions.isEmpty()) {
            // All permissions granted
            showSnackbar("✅ All permissions granted! Full functionality enabled.", Snackbar.LENGTH_SHORT);
        } else if (!grantedPermissions.isEmpty()) {
            // Some granted, some denied
            showSnackbar("⚡ " + grantedPermissions.size() + " permissions granted. You can enable others in Settings.", Snackbar.LENGTH_LONG);
        } else {
            // All denied - show gentle reminder
            showSnackbar("ℹ️ Permissions can be granted later in Settings for full functionality.", Snackbar.LENGTH_LONG);
        }

        // Notify Settings fragment about permission changes
        notifyFragmentsOfPermissionChange();
    }
    // 5. ADD new method to refresh features based on permissions
    private void refreshFeaturesBasedOnPermissions() {
        // Check what permissions we have and enable/disable features accordingly

        // Refresh Bluetooth if we now have permissions
        if (PermissionManager.hasAllRequiredPermissions(this)) {
            if (bluetoothAdapter == null || printManager == null) {
                initializeBluetoothWithGracefulDegradation();
            }
            if (bleConnectionManager == null) {
                initializeBleServiceWithGracefulDegradation();
            }
        }

        // Update UI to reflect permission status
        notifyFragmentsOfPermissionChange();

        // Try auto-connect if we have permissions now
        if (hasAllBluetoothPermissions()) {
            autoConnectToSavedPrinter();
        }
    }

    public boolean isBluetoothEnabledAndReady() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            return false;
        }

        boolean isEnabled = bluetoothAdapter.isEnabled();
        boolean hasPermissions = hasAllBluetoothPermissions();

        Log.d(TAG, "Bluetooth ready check - Enabled: " + isEnabled + ", Permissions: " + hasPermissions);

        return isEnabled && hasPermissions;
    }


    private String getBluetoothStateString(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_OFF:
                return "OFF";
            case BluetoothAdapter.STATE_TURNING_ON:
                return "TURNING ON";
            case BluetoothAdapter.STATE_ON:
                return "ON";
            case BluetoothAdapter.STATE_TURNING_OFF:
                return "TURNING OFF";
            default:
                return "UNKNOWN (" + state + ")";
        }
    }
    // Add method to show license dialog from anywhere
    // In MainActivity.java - Add this method
    public void showLicenseManagement() {
        LicenseDialog licenseDialog = new LicenseDialog(this,
                new LicenseDialog.OnLicenseStatusChangeListener() {
                    @Override
                    public void onLicenseActivated() {
                        showSnackbar("License activated successfully!", Snackbar.LENGTH_LONG);

                        // Notify Settings fragment to update license display
                        Fragment settingsFragment = getSupportFragmentManager().findFragmentByTag("SETTINGS");
                        if (settingsFragment instanceof Settings) {
                            Settings settings = (Settings) settingsFragment;
                            // This will trigger updateLicenseDisplay() in Settings
                            settings.updateLicenseDisplay();
                        }
                    }

                    @Override
                    public void onLicenseExpired() {
                        showSnackbar("License has expired", Snackbar.LENGTH_LONG);
                    }
                });

        licenseDialog.show();
    }

    public void notifyFragmentsOfLicenseChange() {
        // Notify Settings fragment if it exists
        Fragment settingsFragment = getSupportFragmentManager().findFragmentByTag("SETTINGS");
        if (settingsFragment instanceof Settings) {
            ((Settings) settingsFragment).onLicenseStatusChanged();
        }

        // Notify Home fragment to update any license-related UI
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof Home) {
            ((Home) currentFragment).refreshData();
        }

        // Update any other fragments that might show license info
        Log.d(TAG, "Notified fragments of license change");
    }
    // Add this method to check license status without blocking
    public void checkAndShowLicenseWarning() {
        OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(this);

        if (licenseManager.validateLicense()) {
            int daysRemaining = licenseManager.getDaysRemaining();

            if (daysRemaining > 0 && daysRemaining <= 7) {
                // Show warning snackbar
                Snackbar.make(findViewById(android.R.id.content),
                                "License expires in " + daysRemaining + " days",
                                Snackbar.LENGTH_LONG)
                        .setAction("Renew", v -> showLicenseManagement())
                        .setBackgroundTint(ContextCompat.getColor(this, R.color.warning))
                        .show();
            }
        }
    }
}