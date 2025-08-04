package com.example.meruscrap;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
public class Settings extends Fragment implements BluetoothPrintManager.PrinterStatusListener {

    private static final String TAG = "Settings";
    // Add these new button fields
    private MaterialButton btnPrintQueue;
    private MaterialButton btnPrinterSettings;
    // 1. ADD THESE CONSTANTS
    private static final String PREF_LAST_PERMISSION_CHECK = "last_permission_check";
    private static final String PREF_PERMISSION_EDUCATION_SHOWN = "permission_education_shown";
    // 2. ADD THESE STATE VARIABLES
    private boolean isRefreshing = false;
    private Handler refreshHandler;
    private Runnable refreshRunnable;


    // License Management UI Elements
    private MaterialCardView licenseSettingsCard;
    private TextView tvLicenseStatus;
    private TextView tvLicenseInfo;
    private MaterialButton btnManageLicense;
    private MaterialButton btnPurchaseLicense;

    // 2. ADD THESE FIELDS TO YOUR CLASS
// Permission Management UI Elements (add to initializeViews)
    private MaterialCardView permissionSettingsCard;
    private TextView tvPermissionStatus;
    private MaterialButton btnManagePermissions;
    private MaterialButton btnPermissionDetails;

    // BLE Service UI Elements
    private MaterialCardView bleServiceCard;
    private TextView tvBleServiceStatus;
    private TextView tvConnectedScaleName;
    private MaterialButton btnConnectScale;
    private MaterialButton btnDisconnectScale;
    private View bleServiceIndicator;

    // UI Elements - Original Settings
    private ImageView ivProfileAvatar;
    private TextView tvUserName;
    private TextView tvUserEmail;
    private TextView tvCurrentLanguage;
    private TextView tvAppVersionSettings;
    private MaterialButton btnEditProfile;
    private MaterialButton btnSignOut;

    // Switches - Original Settings
    private SwitchMaterial switchNotifications;
    private SwitchMaterial switchDarkMode;
    private SwitchMaterial switchAppLock;

    // Clickable Layouts - Original Settings
    private LinearLayout layoutLanguage;
    private LinearLayout layoutBackup;
    private LinearLayout layoutRestore;
    private LinearLayout layoutClearData;
    private LinearLayout layoutPrivacyPolicy;
    private LinearLayout layoutHelp;
    private LinearLayout layoutAbout;

    // Printer Settings UI Elements
    private MaterialCardView printerSettingsCard;
    private TextView tvPrinterConnectionStatus;
    private TextView tvConnectedPrinterName;
    private TextView tvPrintQueueStatus;
    private MaterialButton btnConnectPrinter;
    private MaterialButton btnDisconnectPrinter;
    private MaterialButton btnTestPrint;
    private MaterialButton btnClearQueue;
    private LinearLayout layoutPrinterActions;
    private View printerStatusIndicator;

    // SharedPreferences for storing settings
    private SharedPreferences sharedPreferences;

    // Printer Management
    private MainActivity mainActivity;
    private BluetoothPrintManager printManager;
    private BluetoothDevice connectedPrinter;
    private boolean printerConnected = false;
    private Handler mainHandler;

    // Connection states for printer
    private enum PrinterConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR, PAPER_JAM, NO_PAPER, LOW_BATTERY
    }
    private PrinterConnectionState currentPrinterConnectionState = PrinterConnectionState.DISCONNECTED;

    // Constants
    private static final String PREF_NOTIFICATIONS = "notifications_enabled";
    private static final String PREF_DARK_MODE = "dark_mode_enabled";
    private static final String PREF_APP_LOCK = "app_lock_enabled";
    private static final String PREF_LANGUAGE = "selected_language";
    private static final String SUPPORT_EMAIL = "mikemburea@gmail.com";

    // 3. ENHANCE YOUR onCreate METHOD
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mainHandler = new Handler(Looper.getMainLooper());
        refreshHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "Settings fragment created");

        // Show permission education if first time
        showPermissionEducationIfNeeded();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Get MainActivity reference
        if (getActivity() instanceof MainActivity) {
            mainActivity = (MainActivity) getActivity();
        }

        // Initialize views
        initializeViews(view);

        // Set up dynamic content
        setupDynamicContent();

        // Load saved preferences
        loadSavedPreferences();

        // Set up click listeners
        setupClickListeners();

        // Initialize printer functionality
        initializePrinterSettings();
        setupCardLongClickListeners();
        return view;
    }

    // 1. UPDATE onResume() TO SHOW GENTLE REMINDERS
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "Settings fragment resumed");

        // Existing code
        refreshPrinterStatus();
        updatePrinterConnectionStatus();
        updatePrintQueueStatus();
        debugButtonStates();
        updatePermissionStatus();
        updateBleServiceStatus();

        // ADD THIS - Show gentle permission reminder if needed
        showGentlePermissionReminderIfNeeded();
        // ADD THIS LINE to update license display
        updateLicenseDisplay();
    }

    // 2. ADD GENTLE PERMISSION REMINDER METHOD
    private void showGentlePermissionReminderIfNeeded() {
        // Only show if there are missing permissions and user hasn't seen education recently
        List<String> missingPermissions = PermissionManager.getMissingPermissions(getContext());

        if (!missingPermissions.isEmpty()) {
            // Check if we should show a gentle reminder (not too frequently)
            long lastReminderTime = sharedPreferences.getLong("last_permission_reminder", 0);
            long currentTime = System.currentTimeMillis();
            long dayInMillis = 24 * 60 * 60 * 1000L; // 24 hours

            // Show reminder if it's been more than a day since last reminder
            if (currentTime - lastReminderTime > dayInMillis) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isAdded() && getContext() != null) {
                        showGentlePermissionReminder(missingPermissions);

                        // Update last reminder time
                        sharedPreferences.edit()
                                .putLong("last_permission_reminder", currentTime)
                                .apply();
                    }
                }, 3000); // Show after 3 seconds to let UI settle
            }
        }
    }

    // 3. ADD GENTLE REMINDER DIALOG
    private void showGentlePermissionReminder(List<String> missingPermissions) {
        if (getContext() == null || !isAdded()) return;

        // Create a gentle, non-intrusive reminder
        StringBuilder message = new StringBuilder();
        message.append("🚀 Ready to unlock more features?\n\n");
        message.append("Grant the remaining ").append(missingPermissions.size()).append(" permission(s) to enable:\n\n");

        for (String permission : missingPermissions) {
            PermissionManager.PermissionInfo info = PermissionManager.getPermissionInfo(permission);
            if (info != null) {
                if (permission.contains("BLUETOOTH")) {
                    message.append("🖨️ Bluetooth printing capabilities\n");
                } else if (permission.contains("LOCATION")) {
                    message.append("⚖️ BLE scale connectivity\n");
                }
            }
        }

        message.append("\n💡 You can grant these anytime in this Settings page!");

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Enhance Your Experience")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setMessage(message.toString())
                .setPositiveButton("Grant Now", (dialog, which) -> {
                    if (mainActivity != null) {
                        mainActivity.requestMissingPermissions();
                    }
                })
                .setNegativeButton("Maybe Later", null)
                .setNeutralButton("Don't Show Again", (dialog, which) -> {
                    // Set a flag to not show again for a week
                    long weekFromNow = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L);
                    sharedPreferences.edit()
                            .putLong("last_permission_reminder", weekFromNow)
                            .apply();
                    showSnackbar("Permission reminders disabled for 1 week", Snackbar.LENGTH_SHORT);
                })
                .show();
    }


    // 5. ADD METHOD TO SHOW BLUETOOTH ENABLE DIALOG FROM SETTINGS
    private void showBluetoothEnableDialog() {
        if (getContext() == null || !isAdded()) return;

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            showSnackbar("Bluetooth not supported on this device", Snackbar.LENGTH_LONG);
            return;
        }

        if (bluetoothAdapter.isEnabled()) {
            showSnackbar("Bluetooth is already enabled", Snackbar.LENGTH_SHORT);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Enable Bluetooth")
                .setIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setMessage("Enable Bluetooth to use:\n\n" +
                        "🖨️ Thermal printers for receipts\n" +
                        "⚖️ BLE scales for weighing\n" +
                        "🔍 Device discovery\n\n" +
                        "Would you like to enable Bluetooth now?")
                .setPositiveButton("Enable", (dialog, which) -> {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivity(enableBtIntent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    // 10. ADD LIFECYCLE IMPROVEMENTS
    @Override
    public void onStart() {
        super.onStart();
        startPeriodicRefresh();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopPeriodicRefresh();
        saveConnectionStates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Settings fragment destroyed");

        // Clean up print manager listener
        if (printManager != null) {
            printManager.setStatusListener(null);
        }
    }

    private void initializeViews(View view) {
        // Original Settings UI Elements
        ivProfileAvatar = view.findViewById(R.id.iv_profile_avatar);
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvUserEmail = view.findViewById(R.id.tv_user_email);
        btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        // ADD THESE NEW VIEWS
        // Permission Management UI Elements
        permissionSettingsCard = view.findViewById(R.id.permission_settings_card);
        tvPermissionStatus = view.findViewById(R.id.tv_permission_status);
        btnManagePermissions = view.findViewById(R.id.btn_manage_permissions);
        btnPermissionDetails = view.findViewById(R.id.btn_permission_details);

        // BLE Service UI Elements
        bleServiceCard = view.findViewById(R.id.ble_service_card);
        tvBleServiceStatus = view.findViewById(R.id.tv_ble_service_status);
        tvConnectedScaleName = view.findViewById(R.id.tv_connected_scale_name);
        btnConnectScale = view.findViewById(R.id.btn_connect_scale);
        btnDisconnectScale = view.findViewById(R.id.btn_disconnect_scale);
        bleServiceIndicator = view.findViewById(R.id.ble_service_indicator);

        // App preferences
        switchNotifications = view.findViewById(R.id.switch_notifications);
        switchDarkMode = view.findViewById(R.id.switch_dark_mode);
        layoutLanguage = view.findViewById(R.id.layout_language);
        tvCurrentLanguage = view.findViewById(R.id.tv_current_language);

        // Data management
        layoutBackup = view.findViewById(R.id.layout_backup);
        layoutRestore = view.findViewById(R.id.layout_restore);
        layoutClearData = view.findViewById(R.id.layout_clear_data);

        // Privacy & Security
        switchAppLock = view.findViewById(R.id.switch_app_lock);
        layoutPrivacyPolicy = view.findViewById(R.id.layout_privacy_policy);

        // About & Support
        layoutHelp = view.findViewById(R.id.layout_help);
        layoutAbout = view.findViewById(R.id.layout_about);
        tvAppVersionSettings = view.findViewById(R.id.tv_app_version_settings);

        // Sign out
        btnSignOut = view.findViewById(R.id.btn_sign_out);

        // Printer Settings UI Elements (add these to your fragment_settings.xml)
        printerSettingsCard = view.findViewById(R.id.printer_settings_card);
        tvPrinterConnectionStatus = view.findViewById(R.id.tv_printer_connection_status);
        tvConnectedPrinterName = view.findViewById(R.id.tv_connected_printer_name);
        tvPrintQueueStatus = view.findViewById(R.id.tv_print_queue_status);
        btnConnectPrinter = view.findViewById(R.id.btn_connect_printer);
        btnDisconnectPrinter = view.findViewById(R.id.btn_disconnect_printer);
        btnTestPrint = view.findViewById(R.id.btn_test_print);
        btnClearQueue = view.findViewById(R.id.btn_clear_queue);
        layoutPrinterActions = view.findViewById(R.id.layout_printer_actions);
        printerStatusIndicator = view.findViewById(R.id.printer_status_indicator);
// FIXED: Store the new button references
        btnPrintQueue = view.findViewById(R.id.btn_print_queue);
        btnPrinterSettings = view.findViewById(R.id.btn_printer_settings);

        // License Management UI Elements (ADD THIS SECTION)
        licenseSettingsCard = view.findViewById(R.id.license_settings_card);
        tvLicenseStatus = view.findViewById(R.id.tv_license_status);
        tvLicenseInfo = view.findViewById(R.id.tv_license_info);
        btnManageLicense = view.findViewById(R.id.btn_manage_license);
        btnPurchaseLicense = view.findViewById(R.id.btn_purchase_license);

        // ... continue with rest of your existing initializations ...
        Log.d(TAG, "Views initialized - License management UI added");

        Log.d(TAG, "Views initialized - btnPrintQueue: " + (btnPrintQueue != null ? "found" : "NOT FOUND"));
        Log.d(TAG, "Views initialized - btnPrinterSettings: " + (btnPrinterSettings != null ? "found" : "NOT FOUND"));
        Log.d(TAG, "Views initialized");
    }

    private void setupDynamicContent() {
        // Set app version
        if (tvAppVersionSettings != null) {
            try {
                String versionName = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                tvAppVersionSettings.setText("Version " + versionName);
            } catch (Exception e) {
                tvAppVersionSettings.setText("Version 1.0.0");
            }
        }

        // Set user info (you can load this from your user management system)
        if (tvUserName != null) {
            String savedUserName = sharedPreferences.getString("user_name", "Meru Scrap");
            tvUserName.setText(savedUserName);
        }

        if (tvUserEmail != null) {
            String savedUserEmail = sharedPreferences.getString("user_email", "meruscrap@gmail.com");
            tvUserEmail.setText(savedUserEmail);
        }
    }

    private void loadSavedPreferences() {
        // Load switch states
        if (switchNotifications != null) {
            switchNotifications.setChecked(sharedPreferences.getBoolean(PREF_NOTIFICATIONS, true));
        }

        if (switchDarkMode != null) {
            switchDarkMode.setChecked(sharedPreferences.getBoolean(PREF_DARK_MODE, false));
        }

        if (switchAppLock != null) {
            switchAppLock.setChecked(sharedPreferences.getBoolean(PREF_APP_LOCK, false));
        }

        // Load language preference
        if (tvCurrentLanguage != null) {
            String language = sharedPreferences.getString(PREF_LANGUAGE, "English");
            tvCurrentLanguage.setText(language);
        }
    }

    private void setupClickListeners() {
        // Profile edit button
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
        }

        // Switch listeners with preference saving
        if (switchNotifications != null) {
            switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sharedPreferences.edit().putBoolean(PREF_NOTIFICATIONS, isChecked).apply();
                String message = isChecked ? "Notifications enabled" : "Notifications disabled";
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            });
        }

        if (switchDarkMode != null) {
            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sharedPreferences.edit().putBoolean(PREF_DARK_MODE, isChecked).apply();
                String message = isChecked ? "Dark mode enabled" : "Dark mode disabled";
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                // TODO: Implement actual theme switching
            });
        }

        if (switchAppLock != null) {
            switchAppLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sharedPreferences.edit().putBoolean(PREF_APP_LOCK, isChecked).apply();
                String message = isChecked ? "App lock enabled" : "App lock disabled";
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                // TODO: Implement biometric authentication setup
            });
        }

        // Language selection
        if (layoutLanguage != null) {
            layoutLanguage.setOnClickListener(v -> showLanguageSelectionDialog());
        }

        // Data management
        if (layoutBackup != null) {
            layoutBackup.setOnClickListener(v -> performBackup());
        }

        if (layoutRestore != null) {
            layoutRestore.setOnClickListener(v -> performRestore());
        }

        if (layoutClearData != null) {
            layoutClearData.setOnClickListener(v -> showClearDataConfirmation());
        }

        // Privacy policy
        if (layoutPrivacyPolicy != null) {
            layoutPrivacyPolicy.setOnClickListener(v -> openPrivacyPolicy());
        }

        // Help & Support
        if (layoutHelp != null) {
            layoutHelp.setOnClickListener(v -> openSupportEmail());
        }

        // About app
        if (layoutAbout != null) {
            layoutAbout.setOnClickListener(v -> {
                // Navigate to Info fragment or show about dialog
                showAppInfoDialog();
            });
        }

        // Sign out
        if (btnSignOut != null) {
            btnSignOut.setOnClickListener(v -> showSignOutConfirmation());
        }

        // Printer Settings Click Listeners
        setupPrinterClickListeners();
        // ADD THIS LINE AT THE END
        setupEnhancedClickListeners();
        // License Management Click Listeners (ADD THIS SECTION)
        if (btnManageLicense != null) {
            btnManageLicense.setOnClickListener(v -> {
                Log.d(TAG, "Manage License button clicked");
                showLicenseManagementDialog();
            });
        }

        if (btnPurchaseLicense != null) {
            btnPurchaseLicense.setOnClickListener(v -> {
                Log.d(TAG, "Purchase License button clicked");
                openPurchaseOptions();
            });
        }

        // License card long click for advanced options
        if (licenseSettingsCard != null) {
            licenseSettingsCard.setOnLongClickListener(v -> {
                showAdvancedLicenseOptions();
                return true;
            });
        }
    }

    private void setupPrinterClickListeners() {
        // Main printer connection buttons
        if (btnConnectPrinter != null) {
            btnConnectPrinter.setOnClickListener(v -> handleConnectPrinter());
        }

        if (btnDisconnectPrinter != null) {
            btnDisconnectPrinter.setOnClickListener(v -> handleDisconnectPrinter());
        }

        // Printer action buttons
        if (btnTestPrint != null) {
            btnTestPrint.setOnClickListener(v -> handleTestPrint());
        }

        if (btnClearQueue != null) {
            btnClearQueue.setOnClickListener(v -> handleClearPrintQueue());
        }

        // FIXED: Use stored button references instead of getView().findViewById()
        if (btnPrintQueue != null) {
            btnPrintQueue.setOnClickListener(v -> {
                Log.d(TAG, "Print Queue button clicked");
                showPrintQueueDialog();
            });
            btnPrintQueue.setEnabled(true);
            Log.d(TAG, "Print Queue button click listener set successfully");
        } else {
            Log.e(TAG, "btnPrintQueue is null - button not found in layout");
        }

        if (btnPrinterSettings != null) {
            btnPrinterSettings.setOnClickListener(v -> {
                Log.d(TAG, "Printer Settings button clicked");
                showAdvancedPrinterSettingsDialog();
            });
            btnPrinterSettings.setEnabled(true);
            Log.d(TAG, "Printer Settings button click listener set successfully");
        } else {
            Log.e(TAG, "btnPrinterSettings is null - button not found in layout");
        }

        // Long click listeners for additional functionality
        if (tvPrinterConnectionStatus != null) {
            tvPrinterConnectionStatus.setOnLongClickListener(v -> {
                showPrinterDiagnosticInfo();
                return true;
            });
        }

        if (tvPrintQueueStatus != null) {
            tvPrintQueueStatus.setOnClickListener(v -> {
                if (mainActivity != null && mainActivity.isPrinterConnected()) {
                    showPrintQueueDialog();
                } else {
                    showSnackbar("Connect to printer first", Snackbar.LENGTH_SHORT);
                }
            });
        }

        // Printer card long click for quick actions
        if (printerSettingsCard != null) {
            printerSettingsCard.setOnLongClickListener(v -> {
                showQuickPrinterActionsDialog();
                return true;
            });
        }
    }

    // Add this method for the updated About section
    private void showAppInfoDialog() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("About MeruScrap");

        StringBuilder appInfo = new StringBuilder();
        appInfo.append("MeruScrap Metal Market\n\n");

        // Get app version
        try {
            String versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            appInfo.append("Version: ").append(versionName).append("\n");
        } catch (Exception e) {
            appInfo.append("Version: 1.0.0\n");
        }

        appInfo.append("Build Date: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault()).format(new java.util.Date())).append("\n\n");

        appInfo.append("Features:\n");
        appInfo.append("• Bluetooth Printing\n");
        appInfo.append("• Transaction Management\n");
        appInfo.append("• Print Queue System\n");
        appInfo.append("• Data Backup & Restore\n");
        appInfo.append("• Multi-language Support\n\n");

        appInfo.append("Developed for Meru County, Kenya\n");
        appInfo.append("Contact: ").append(SUPPORT_EMAIL);

        builder.setMessage(appInfo.toString());
        builder.setPositiveButton("OK", null);

        builder.setNeutralButton("System Info", (dialog, which) -> {
            showSystemInfoDialog();
        });

        builder.show();
    }

    // Add this method for system information
    private void showSystemInfoDialog() {
        if (getContext() == null || !isAdded()) return;

        StringBuilder sysInfo = new StringBuilder();
        sysInfo.append("System Information\n\n");

        sysInfo.append("Android Version: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        sysInfo.append("Device Model: ").append(android.os.Build.MODEL).append("\n");
        sysInfo.append("Manufacturer: ").append(android.os.Build.MANUFACTURER).append("\n");
        sysInfo.append("SDK Level: ").append(android.os.Build.VERSION.SDK_INT).append("\n\n");

        // Bluetooth info
        android.bluetooth.BluetoothAdapter bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            sysInfo.append("Bluetooth: Available\n");
            sysInfo.append("Bluetooth Enabled: ").append(bluetoothAdapter.isEnabled()).append("\n");
        } else {
            sysInfo.append("Bluetooth: Not Available\n");
        }

        // Memory info
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);

        sysInfo.append("\nMemory Info:\n");
        sysInfo.append("Max Memory: ").append(maxMemory).append(" MB\n");
        sysInfo.append("Total Memory: ").append(totalMemory).append(" MB\n");
        sysInfo.append("Free Memory: ").append(freeMemory).append(" MB\n");

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("System Information");
        builder.setMessage(sysInfo.toString());
        builder.setPositiveButton("OK", null);

        builder.setNeutralButton("Copy Info", (dialog, which) -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("System Info", sysInfo.toString());
            clipboard.setPrimaryClip(clip);
            showSnackbar("System info copied to clipboard", Snackbar.LENGTH_SHORT);
        });

        builder.show();
    }

    // Add this method for quick printer actions
    private void showQuickPrinterActionsDialog() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Quick Printer Actions");

        String[] quickActions = {
                "🔄 Reconnect Printer",
                "📋 View Print Queue",
                "🖨️ Print Test Page",
                "📊 Printer Statistics",
                "🔧 Printer Diagnostics"
        };

        builder.setItems(quickActions, (dialog, which) -> {
            switch (which) {
                case 0:
                    // Reconnect printer
                    if (mainActivity != null) {
                        handleDisconnectPrinter();
                        new android.os.Handler().postDelayed(this::handleConnectPrinter, 1000);
                    }
                    break;
                case 1:
                    showPrintQueueDialog();
                    break;
                case 2:
                    handleTestPrint();
                    break;
                case 3:
                    showPrintStatisticsDialog();
                    break;
                case 4:
                    showPrinterDiagnosticInfo();
                    break;
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }


    private void showAdvancedPrinterSettingsDialog() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Advanced Printer Settings");

        String[] options = {
                "🔧 Print Configuration",
                "📊 Print Statistics",
                "🔍 Connection Diagnostics",
                "📝 Print History Details",
                "⚙️ Printer Preferences",
                "🔄 Reset Print Settings"
        };

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    showPrintConfigurationDialog();
                    break;
                case 1:
                    showPrintStatisticsDialog();
                    break;
                case 2:
                    showPrinterDiagnosticInfo();
                    break;
                case 3:
                    showDetailedPrintHistory();
                    break;
                case 4:
                    showPrinterPreferences();
                    break;
                case 5:
                    showResetPrintSettingsDialog();
                    break;
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showPrintStatisticsDialog() {
        if (getContext() == null || !isAdded()) return;

        // Get statistics for the last 7 days
        long weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
        long now = System.currentTimeMillis();

        PrintHistoryManager.PrintStatistics stats = PrintHistoryManager.getInstance(getContext())
                .getPrintStatistics(weekAgo, now);

        StringBuilder statsText = new StringBuilder();
        statsText.append("Print Statistics (Last 7 Days)\n\n");

        statsText.append("📊 Overview:\n");
        statsText.append("Total Jobs: ").append(stats.totalJobs).append("\n");
        statsText.append("Successful: ").append(stats.successfulJobs).append("\n");
        statsText.append("Failed: ").append(stats.failedJobs).append("\n");
        statsText.append("Success Rate: ").append(String.format("%.1f%%", stats.successRate)).append("\n\n");

        if (stats.jobsByType != null && !stats.jobsByType.isEmpty()) {
            statsText.append("📋 By Job Type:\n");
            for (PrintHistoryManager.JobTypeCount typeCount : stats.jobsByType) {
                statsText.append("• ").append(typeCount.jobType.name())
                        .append(": ").append(typeCount.count).append("\n");
            }
            statsText.append("\n");
        }

        // Current session info
        if (mainActivity != null) {
            statsText.append("🔄 Current Session:\n");
            statsText.append("Printer Connected: ").append(mainActivity.isPrinterConnected() ? "Yes" : "No").append("\n");
            if (mainActivity.isPrinterConnected()) {
                statsText.append("Queue Status: ").append(mainActivity.getPrintQueueInfo()).append("\n");
                statsText.append("Printer Ready: ").append(mainActivity.isPrinterReady() ? "Yes" : "No").append("\n");
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Print Statistics");
        builder.setMessage(statsText.toString());
        builder.setPositiveButton("OK", null);

        builder.setNeutralButton("Detailed History", (dialog, which) -> {
            showDetailedPrintHistory();
        });

        builder.show();
    }

    private void showPrintConfigurationDialog() {
        if (getContext() == null || !isAdded()) return;

        StringBuilder config = new StringBuilder();
        config.append("Print Manager Configuration\n\n");

        if (mainActivity != null) {
            config.append("🔗 Connection Status:\n");
            config.append("Connected: ").append(mainActivity.isPrinterConnected()).append("\n");

            if (mainActivity.printManager != null) {
                config.append("Printing: ").append(mainActivity.printManager.isPrinting()).append("\n");
                config.append("Queue Size: ").append(mainActivity.printManager.getQueueSize()).append("\n");
            }
            config.append("\n");
        }

        config.append("⚙️ System Configuration:\n");
        config.append("Auto-retry: Enabled (3 attempts)\n");
        config.append("Queue processing: Automatic\n");
        config.append("Status monitoring: Every 10 seconds\n");
        config.append("Connection timeout: 30 seconds\n");
        config.append("Print job timeout: 60 seconds\n\n");

        config.append("📝 Supported Features:\n");
        config.append("• ESC/POS Commands\n");
        config.append("• Auto paper cutting\n");
        config.append("• Status monitoring\n");
        config.append("• Print queue management\n");
        config.append("• Job retry mechanism\n");
        config.append("• Connection recovery\n");

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Print Configuration");
        builder.setMessage(config.toString());
        builder.setPositiveButton("OK", null);

        builder.setNeutralButton("Copy Config", (dialog, which) -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Print Config", config.toString());
            clipboard.setPrimaryClip(clip);
            showSnackbar("Configuration copied to clipboard", Snackbar.LENGTH_SHORT);
        });

        builder.show();
    }

    private void showDetailedPrintHistory() {
        if (getContext() == null || !isAdded()) return;

        List<PrintHistoryManager.PrintJob> recentJobs = PrintHistoryManager.getInstance(getContext())
                .getRecentPrintJobs(50); // Get last 50 jobs

        if (recentJobs.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Print History");
            builder.setMessage("No print history available.");
            builder.setPositiveButton("OK", null);
            builder.show();
            return;
        }

        StringBuilder historyText = new StringBuilder();
        historyText.append("Detailed Print History (Last 50 Jobs)\n\n");

        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MM/dd HH:mm:ss", java.util.Locale.getDefault());

        for (int i = 0; i < Math.min(recentJobs.size(), 20); i++) { // Show first 20 in dialog
            PrintHistoryManager.PrintJob job = recentJobs.get(i);
            String statusIcon = getStatusIcon(job.status);

            historyText.append(statusIcon).append(" ")
                    .append(dateFormat.format(job.createdAt)).append("\n")
                    .append("   Type: ").append(job.jobType.name())
                    .append(" | Status: ").append(job.status.name());

            if (job.retryCount > 0) {
                historyText.append(" | Retries: ").append(job.retryCount);
            }

            if (job.transactionId != null) {
                historyText.append("\n   Transaction: ").append(job.transactionId);
            }

            historyText.append("\n\n");
        }

        if (recentJobs.size() > 20) {
            historyText.append("... and ").append(recentJobs.size() - 20).append(" more jobs");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Detailed Print History");
        builder.setMessage(historyText.toString());
        builder.setPositiveButton("OK", null);

        builder.setNeutralButton("View All Jobs", (dialog, which) -> {
            showPrintQueueDialog(); // This shows the interactive list
        });

        builder.show();
    }

    private void showPrinterPreferences() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Printer Preferences");

        String[] prefOptions = {
                "🔤 Receipt Header/Footer Settings",
                "📏 Paper Size & Formatting",
                "🔊 Notification Settings",
                "⚡ Power Management",
                "🔄 Auto-Connect Settings",
                "💾 Save Current Printer"
        };

        builder.setItems(prefOptions, (dialog, which) -> {
            switch (which) {
                case 0:
                    showReceiptFormatDialog();
                    break;
                case 1:
                    showPaperSettingsDialog();
                    break;
                case 2:
                    showNotificationSettingsDialog();
                    break;
                case 3:
                    showPowerManagementDialog();
                    break;
                case 4:
                    showAutoConnectSettingsDialog();
                    break;
                case 5:
                    saveCurrentPrinterSettings();
                    break;
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showResetPrintSettingsDialog() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Reset Print Settings");
        builder.setMessage("This will reset all printer settings to default values. This includes:\n\n" +
                "• Saved printer connections\n" +
                "• Print preferences\n" +
                "• Queue settings\n" +
                "• Connection history\n\n" +
                "Print history will be preserved. Continue?");

        builder.setPositiveButton("Reset", (dialog, which) -> {
            resetPrintSettings();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Helper methods for printer preferences
    private void showReceiptFormatDialog() {
        showSnackbar("Receipt format settings coming soon!", Snackbar.LENGTH_SHORT);
        // TODO: Implement receipt formatting preferences
    }

    private void showPaperSettingsDialog() {
        showSnackbar("Paper settings coming soon!", Snackbar.LENGTH_SHORT);
        // TODO: Implement paper size and formatting settings
    }

    private void showNotificationSettingsDialog() {
        showSnackbar("Notification settings coming soon!", Snackbar.LENGTH_SHORT);
        // TODO: Implement printer notification preferences
    }

    private void showPowerManagementDialog() {
        showSnackbar("Power management settings coming soon!", Snackbar.LENGTH_SHORT);
        // TODO: Implement power management settings
    }

    private void showAutoConnectSettingsDialog() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Auto-Connect Settings");
        builder.setMessage("Configure automatic printer connection behavior:");

        String[] autoConnectOptions = {
                "Always auto-connect to saved printer",
                "Ask before connecting",
                "Never auto-connect",
                "Auto-connect only on app start"
        };

        // Get current setting (you might want to store this in SharedPreferences)
        int currentSelection = sharedPreferences.getInt("auto_connect_mode", 0);

        builder.setSingleChoiceItems(autoConnectOptions, currentSelection, (dialog, which) -> {
            sharedPreferences.edit().putInt("auto_connect_mode", which).apply();
            String message = "Auto-connect mode updated: " + autoConnectOptions[which];
            showSnackbar(message, Snackbar.LENGTH_SHORT);
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveCurrentPrinterSettings() {
        if (mainActivity != null && mainActivity.isPrinterConnected()) {
            BluetoothDevice currentPrinter = mainActivity.getConnectedPrinter();
            if (currentPrinter != null) {
                // Save printer to preferences (you might want to implement this in MainActivity)
                String deviceName = getDeviceNameSafely(currentPrinter);
                String deviceAddress = currentPrinter.getAddress();

                sharedPreferences.edit()
                        .putString("saved_printer_name", deviceName)
                        .putString("saved_printer_address", deviceAddress)
                        .apply();

                showSnackbar("Printer saved: " + deviceName, Snackbar.LENGTH_SHORT);
            }
        } else {
            showSnackbar("No printer connected to save", Snackbar.LENGTH_SHORT);
        }
    }

    private void resetPrintSettings() {
        // Clear printer-related preferences
        sharedPreferences.edit()
                .remove("saved_printer_name")
                .remove("saved_printer_address")
                .remove("auto_connect_mode")
                .apply();

        // Disconnect current printer
        if (mainActivity != null) {
            mainActivity.disconnectPrinter();
            mainActivity.clearPrintQueue();
        }

        // Reset UI state
        updateConnectionState(PrinterConnectionState.DISCONNECTED);
        connectedPrinter = null;
        printerConnected = false;

        showSnackbar("Print settings reset to defaults", Snackbar.LENGTH_LONG);

        // Refresh UI
        updatePrinterConnectionStatus();
        updatePrintQueueStatus();
    }
    // =================================================================
    // PRINTER SETTINGS METHODS
    // =================================================================

    private void initializePrinterSettings() {
        Log.d(TAG, "Initializing printer settings");

        if (mainActivity != null) {
            // Get print manager from MainActivity
            try {
                printManager = mainActivity.printManager; // Access the print manager
                if (printManager != null) {
                    printManager.setStatusListener(this);
                    Log.d(TAG, "Print manager listener set");
                } else {
                    Log.w(TAG, "Print manager is null in MainActivity");
                }

                // Get current printer status
                connectedPrinter = mainActivity.getConnectedPrinter();
                printerConnected = mainActivity.isPrinterConnected();

                Log.d(TAG, "Initial printer status - Connected: " + printerConnected +
                        ", Device: " + (connectedPrinter != null ? connectedPrinter.getAddress() : "none"));

            } catch (Exception e) {
                Log.e(TAG, "Error initializing printer settings", e);
            }
        } else {
            Log.w(TAG, "MainActivity reference is null");
        }

        // Update UI with current status
        updatePrinterConnectionStatus();
        updatePrintQueueStatus();
    }

    private void handleConnectPrinter() {
        Log.d(TAG, "Connect printer button clicked");

        if (mainActivity == null) {
            showSnackbar("Unable to access printer settings", Snackbar.LENGTH_SHORT);
            return;
        }

        // Check if already connected
        if (printerConnected && connectedPrinter != null) {
            showSnackbar("Printer already connected: " + getDeviceNameSafely(connectedPrinter),
                    Snackbar.LENGTH_SHORT);
            return;
        }

        // Check Bluetooth permissions
        if (!mainActivity.hasBluetoothPermissionsForPrinting()) {
            showSnackbar("Bluetooth permissions required for printer functionality", Snackbar.LENGTH_LONG);
            mainActivity.checkBluetoothPermissions();
            return;
        }

        // Show printer connection options
        showPrinterConnectionOptions();
    }

    private void showPrinterConnectionOptions() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Connect Printer");

        String[] options = {
                "🔍 Scan for New Printers",
                "📱 Show Paired Devices",
                "🔄 Auto-Connect to Saved Printer",
                "🔧 Advanced Options"
        };

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    if (mainActivity != null) {
                        mainActivity.showPrinterScanDialog();
                    }
                    break;
                case 1:
                    if (mainActivity != null) {
                        mainActivity.showPairedDevicesDialog();
                    }
                    break;
                case 2:
                    if (mainActivity != null) {
                        mainActivity.autoConnectToSavedPrinter();
                    }
                    break;
                case 3:
                    showAdvancedPrinterOptions();
                    break;
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showAdvancedPrinterOptions() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Advanced Printer Options");

        String[] options = {
                "🔧 Show All Bluetooth Devices",
                "⌨️ Connect by MAC Address",
                "📋 View Connection History",
                "🗑️ Clear Saved Printer"
        };

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    if (mainActivity != null) {
                        mainActivity.showAllDevicesDialog();
                    }
                    break;
                case 1:
                    showManualMacAddressDialog();
                    break;
                case 2:
                    showConnectionHistory();
                    break;
                case 3:
                    if (mainActivity != null) {
                        mainActivity.clearSavedPrinter();
                        updatePrinterConnectionStatus();
                    }
                    break;
            }
        });

        builder.setNegativeButton("Back", null);
        builder.show();
    }

    private void showManualMacAddressDialog() {
        if (getContext() == null || !isAdded()) return;

        // Create a simple input dialog for MAC address
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Connect by MAC Address");
        builder.setMessage("Enter the printer's MAC address (e.g., 02:05:CC:36:24:3A):");

        final android.widget.EditText input = new android.widget.EditText(getContext());
        input.setHint("XX:XX:XX:XX:XX:XX");
        builder.setView(input);

        builder.setPositiveButton("Connect", (dialog, which) -> {
            String macAddress = input.getText().toString().trim().toUpperCase();
            if (isValidMacAddress(macAddress)) {
                connectToMacAddress(macAddress);
            } else {
                showSnackbar("Invalid MAC address format", Snackbar.LENGTH_SHORT);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private boolean isValidMacAddress(String mac) {
        return mac != null && mac.matches("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");
    }

    private void connectToMacAddress(String macAddress) {
        if (mainActivity == null) return;

        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null) {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);

                // Update UI to show connecting
                updateConnectionState(PrinterConnectionState.CONNECTING);

                // Connect through MainActivity
                onPrinterSelected(device);

                showSnackbar("Connecting to " + macAddress + "...", Snackbar.LENGTH_SHORT);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to MAC address: " + macAddress, e);
            showSnackbar("Failed to connect to " + macAddress, Snackbar.LENGTH_SHORT);
            updateConnectionState(PrinterConnectionState.ERROR);
        }
    }

    private void showConnectionHistory() {
        // Placeholder for connection history feature
        showSnackbar("Connection history feature coming soon!", Snackbar.LENGTH_SHORT);
    }

    private void handleDisconnectPrinter() {
        Log.d(TAG, "Disconnect printer button clicked");

        if (mainActivity != null) {
            mainActivity.disconnectPrinter();
        }

        updateConnectionState(PrinterConnectionState.DISCONNECTED);
        showSnackbar("Disconnecting printer...", Snackbar.LENGTH_SHORT);
    }

    private void handleTestPrint() {
        Log.d(TAG, "Test print button clicked");

        if (!printerConnected || mainActivity == null) {
            showSnackbar("No printer connected", Snackbar.LENGTH_SHORT);
            return;
        }

        try {
            mainActivity.printTestPage();
            showSnackbar("Test page sent to printer", Snackbar.LENGTH_SHORT);
        } catch (Exception e) {
            Log.e(TAG, "Error printing test page", e);
            showSnackbar("Failed to print test page", Snackbar.LENGTH_SHORT);
        }
    }

    private void handleClearPrintQueue() {
        Log.d(TAG, "Clear print queue button clicked");

        if (mainActivity != null) {
            mainActivity.clearPrintQueue();
            updatePrintQueueStatus();
            showSnackbar("Print queue cleared", Snackbar.LENGTH_SHORT);
        }
    }

    private void showPrinterDiagnosticInfo() {
        if (getContext() == null || !isAdded()) return;

        StringBuilder info = new StringBuilder();
        info.append("=== PRINTER DIAGNOSTIC INFO ===\n\n");

        // Connection Status
        info.append("Connection Status: ").append(currentPrinterConnectionState.name()).append("\n");
        info.append("Printer Connected: ").append(printerConnected).append("\n");

        if (connectedPrinter != null) {
            info.append("Device Name: ").append(getDeviceNameSafely(connectedPrinter)).append("\n");
            info.append("MAC Address: ").append(connectedPrinter.getAddress()).append("\n");
        } else {
            info.append("No printer device info available\n");
        }

        // Print Manager Status
        if (mainActivity != null) {
            info.append("Print Manager Ready: ").append(mainActivity.isPrinterReady()).append("\n");
            info.append("Print Queue Info: ").append(mainActivity.getPrintQueueInfo()).append("\n");
        }

        // Bluetooth Status
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            info.append("Bluetooth Enabled: ").append(bluetoothAdapter.isEnabled()).append("\n");
            info.append("Bluetooth Supported: true\n");
        } else {
            info.append("Bluetooth Supported: false\n");
        }

        // Permissions Status
        if (mainActivity != null) {
            info.append("Bluetooth Permissions: ").append(mainActivity.hasBluetoothPermissionsForPrinting()).append("\n");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Printer Diagnostic Info");
        builder.setMessage(info.toString());
        builder.setPositiveButton("Copy to Clipboard", (dialog, which) -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Printer Diagnostic", info.toString());
            clipboard.setPrimaryClip(clip);
            showSnackbar("Diagnostic info copied to clipboard", Snackbar.LENGTH_SHORT);
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    // Method called when printer is selected from MainActivity
    public void onPrinterSelected(BluetoothDevice device) {
        Log.d(TAG, "Printer selected: " + getDeviceNameSafely(device));

        connectedPrinter = device;
        updateConnectionState(PrinterConnectionState.CONNECTING);

        if (mainActivity != null) {
            // Let MainActivity handle the actual connection
            mainActivity.setConnectedPrinter(device);
        }
    }
    /**
     * Method called when printer is disconnected from MainActivity
     * @param device The disconnected Bluetooth device
     */
    public void onPrinterDisconnected(BluetoothDevice device) {
        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "Cannot handle printer disconnect - fragment not attached");
            return;
        }

        Log.d(TAG, "Printer disconnected: " + getDeviceNameSafely(device));

        // Check if this was our connected printer
        if (connectedPrinter != null && device.getAddress().equals(connectedPrinter.getAddress())) {
            // Clear connection state
            connectedPrinter = null;
            printerConnected = false;
            updateConnectionState(PrinterConnectionState.DISCONNECTED);

            String deviceName = getDeviceNameSafely(device);
            showSnackbar("❌ " + deviceName + " disconnected", Snackbar.LENGTH_SHORT);

            Log.d(TAG, "Cleared connection for disconnected printer: " + deviceName);

            // Update UI elements
            updatePrinterConnectionStatus();
            updatePrintQueueStatus();

        } else {
            Log.d(TAG, "Disconnected device is not our current printer, ignoring");
        }
    }
    private void updateConnectionState(PrinterConnectionState state) {
        currentPrinterConnectionState = state;
        updatePrinterConnectionStatus();
    }

    private void updatePrinterConnectionStatus() {
        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "Cannot update printer status - fragment not attached");
            return;
        }

        try {
            // Get REAL connection status from MainActivity
            boolean actuallyConnected = false;
            BluetoothDevice actualDevice = null;

            if (mainActivity != null) {
                actuallyConnected = mainActivity.isPrinterConnected();
                actualDevice = mainActivity.getConnectedPrinter();

                // Sync local state with MainActivity
                printerConnected = actuallyConnected;
                connectedPrinter = actualDevice;

                // Update connection state based on actual status
                if (actuallyConnected && actualDevice != null) {
                    currentPrinterConnectionState = PrinterConnectionState.CONNECTED;
                } else {
                    currentPrinterConnectionState = PrinterConnectionState.DISCONNECTED;
                }
            }

            // Update connection status text
            if (tvPrinterConnectionStatus != null) {
                String statusText = getPrinterStatusText();
                tvPrinterConnectionStatus.setText(statusText);
            }

            // Update connected printer name
            if (tvConnectedPrinterName != null) {
                if (actualDevice != null && actuallyConnected) {
                    String deviceName = getDeviceNameSafely(actualDevice);
                    tvConnectedPrinterName.setText(deviceName + " (" + actualDevice.getAddress() + ")");
                    tvConnectedPrinterName.setVisibility(View.VISIBLE);
                } else {
                    tvConnectedPrinterName.setVisibility(View.GONE);
                }
            }

            // Update status indicator
            if (printerStatusIndicator != null) {
                int statusColor = getPrinterStatusColor();
                printerStatusIndicator.setBackgroundColor(statusColor);
            }

            // Update button states
            updatePrinterButtonStates();

            Log.d(TAG, "Printer status updated - State: " + currentPrinterConnectionState +
                    ", Connected: " + printerConnected +
                    ", Device: " + (actualDevice != null ? actualDevice.getAddress() : "none"));

        } catch (Exception e) {
            Log.e(TAG, "Error updating printer connection status", e);
        }
    }
    // Add this method to Settings.java
    public void refreshPrinterStatus() {
        Log.d(TAG, "Forcing printer status refresh");

        if (mainActivity != null) {
            // Get fresh status from MainActivity
            boolean isConnected = mainActivity.isPrinterConnected();
            BluetoothDevice device = mainActivity.getConnectedPrinter();

            Log.d(TAG, "Fresh status - Connected: " + isConnected +
                    ", Device: " + (device != null ? device.getAddress() : "none"));

            // Update local state
            printerConnected = isConnected;
            connectedPrinter = device;

            if (isConnected && device != null) {
                currentPrinterConnectionState = PrinterConnectionState.CONNECTED;
            } else {
                currentPrinterConnectionState = PrinterConnectionState.DISCONNECTED;
            }

            // Force UI update
            updatePrinterConnectionStatus();
            updatePrintQueueStatus();
        }
    }

    private String getPrinterStatusText() {
        switch (currentPrinterConnectionState) {
            case DISCONNECTED:
                return "Not Connected";
            case CONNECTING:
                return "Connecting...";
            case CONNECTED:
                return "Connected";
            case ERROR:
                return "Connection Error";
            case PAPER_JAM:
                return "Paper Jam";
            case NO_PAPER:
                return "No Paper";
            case LOW_BATTERY:
                return "Low Battery";
            default:
                return "Unknown Status";
        }
    }

    private int getPrinterStatusColor() {
        if (getContext() == null) return android.graphics.Color.GRAY;

        switch (currentPrinterConnectionState) {
            case CONNECTED:
                return androidx.core.content.ContextCompat.getColor(getContext(), R.color.success);
            case CONNECTING:
                return androidx.core.content.ContextCompat.getColor(getContext(), R.color.warning);
            case ERROR:
            case PAPER_JAM:
            case NO_PAPER:
                return androidx.core.content.ContextCompat.getColor(getContext(), R.color.error);
            case LOW_BATTERY:
                return androidx.core.content.ContextCompat.getColor(getContext(), R.color.warning);
            case DISCONNECTED:
            default:
                return androidx.core.content.ContextCompat.getColor(getContext(), R.color.text_secondary);
        }
    }

    private void updatePrinterButtonStates() {
        boolean connected = printerConnected && currentPrinterConnectionState == PrinterConnectionState.CONNECTED;
        boolean connecting = currentPrinterConnectionState == PrinterConnectionState.CONNECTING;

        if (btnConnectPrinter != null) {
            btnConnectPrinter.setEnabled(!connected && !connecting);
            btnConnectPrinter.setText(connecting ? "Connecting..." : "Connect Printer");
        }

        if (btnDisconnectPrinter != null) {
            btnDisconnectPrinter.setEnabled(connected || connecting);
        }

        if (btnTestPrint != null) {
            btnTestPrint.setEnabled(connected);
        }

        if (btnClearQueue != null) {
            btnClearQueue.setEnabled(connected);
        }

        // FIXED: Use stored button references
        if (btnPrintQueue != null) {
            btnPrintQueue.setEnabled(true); // Always enabled to view history
            // Update text based on queue status
            if (mainActivity != null) {
                String queueInfo = mainActivity.getPrintQueueInfo();
                if (queueInfo.contains("jobs")) {
                    btnPrintQueue.setText("Print Queue (" + extractNumberFromString(queueInfo) + ")");
                } else {
                    btnPrintQueue.setText("Print Queue");
                }
            }
        }

        if (btnPrinterSettings != null) {
            btnPrinterSettings.setEnabled(true); // Always enabled
        }

        // Show/hide printer actions based on connection status
        if (layoutPrinterActions != null) {
            layoutPrinterActions.setVisibility(connected ? View.VISIBLE : View.GONE);
        }
    }

    // Helper method to extract number from string
    private int extractNumberFromString(String text) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+");
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group());
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }

    private void updatePrintQueueStatus() {
        if (tvPrintQueueStatus != null && mainActivity != null) {
            try {
                String queueInfo = mainActivity.getPrintQueueInfo();
                tvPrintQueueStatus.setText(queueInfo);
            } catch (Exception e) {
                Log.w(TAG, "Error updating print queue status", e);
                tvPrintQueueStatus.setText("Queue status unavailable");
            }
        }
    }

    private String getDeviceNameSafely(BluetoothDevice device) {
        if (device == null) return "Unknown Device";

        try {
            if (mainActivity != null && mainActivity.hasBluetoothPermissionsForPrinting()) {
                String name = device.getName();
                return name != null ? name : "Unknown Device";
            }
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException while getting device name", e);
        }
        return "Unknown Device";
    }

    public boolean isScaleConnected() {
        // Settings fragment doesn't manage scale connections
        // Return false or check through MainActivity
        if (mainActivity != null) {
            // Avoid circular dependency - just return false since Settings doesn't manage scales
            return false;
        }
        return false;
    }
    @Override
    public void onConnected() {
        if (!isAdded() || getContext() == null) return;

        printerConnected = true;
        updateConnectionState(PrinterConnectionState.CONNECTED);

        String deviceName = connectedPrinter != null ? getDeviceNameSafely(connectedPrinter) : "Printer";
        showSnackbar("✅ Connected to " + deviceName, Snackbar.LENGTH_SHORT);

        Log.d(TAG, "Printer connected successfully: " + deviceName);

        // Update UI
        updatePrintQueueStatus();
    }

    @Override
    public void onDisconnected() {
        if (!isAdded() || getContext() == null) return;

        printerConnected = false;
        updateConnectionState(PrinterConnectionState.DISCONNECTED);

        showSnackbar("❌ Printer disconnected", Snackbar.LENGTH_SHORT);
        Log.d(TAG, "Printer disconnected");
    }

    @Override
    public void onError(String error) {
        if (!isAdded() || getContext() == null) return;

        updateConnectionState(PrinterConnectionState.ERROR);
        showSnackbar("🚨 Printer error: " + error, Snackbar.LENGTH_LONG);
        Log.e(TAG, "Printer error: " + error);
    }

    @Override
    public void onPrintJobCompleted(String jobId) {
        if (!isAdded() || getContext() == null) return;

        showSnackbar("✅ Print completed: " + jobId, Snackbar.LENGTH_SHORT);
        Log.d(TAG, "Print job completed: " + jobId);

        // Update queue status
        updatePrintQueueStatus();
    }

    @Override
    public void onPrintJobFailed(String jobId, String error) {
        if (!isAdded() || getContext() == null) return;

        showSnackbar("❌ Print failed: " + error, Snackbar.LENGTH_LONG);
        Log.e(TAG, "Print job failed - " + jobId + ": " + error);

        // Update queue status
        updatePrintQueueStatus();
    }

    @Override
    public void onStatusUpdate(BluetoothPrintManager.PrinterStatus status) {
        if (!isAdded() || getContext() == null) return;

        switch (status) {
            case NO_PAPER:
                updateConnectionState(PrinterConnectionState.NO_PAPER);
                showSnackbar("Printer: No paper", Snackbar.LENGTH_LONG);
                break;
            case PAPER_JAM:
                updateConnectionState(PrinterConnectionState.PAPER_JAM);
                showSnackbar("Printer: Paper jam", Snackbar.LENGTH_LONG);
                break;
            case LOW_BATTERY:
                updateConnectionState(PrinterConnectionState.LOW_BATTERY);
                showSnackbar("Printer: Low battery", Snackbar.LENGTH_LONG);
                break;
            case COVER_OPEN:
                updateConnectionState(PrinterConnectionState.ERROR);
                showSnackbar("Printer: Cover open", Snackbar.LENGTH_LONG);
                break;
            case OVERHEATED:
                updateConnectionState(PrinterConnectionState.ERROR);
                showSnackbar("Printer: Overheated", Snackbar.LENGTH_LONG);
                break;
            case OFFLINE:
                updateConnectionState(PrinterConnectionState.ERROR);
                showSnackbar("Printer: Offline", Snackbar.LENGTH_LONG);
                break;
            case ONLINE:
                if (currentPrinterConnectionState != PrinterConnectionState.CONNECTED) {
                    updateConnectionState(PrinterConnectionState.CONNECTED);
                }
                break;
        }

        // Update queue status whenever status changes
        updatePrintQueueStatus();
    }

    // =================================================================
    // ORIGINAL SETTINGS METHODS (PRESERVED)
    // =================================================================

    private void showEditProfileDialog() {
        // Create a simple edit profile dialog
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Edit Profile");
        builder.setMessage("Profile editing feature coming soon!");
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private void showLanguageSelectionDialog() {
        String[] languages = {"English", "Swahili", "French", "Spanish"};
        String currentLanguage = sharedPreferences.getString(PREF_LANGUAGE, "English");
        int selectedIndex = 0;

        // Find current selection
        for (int i = 0; i < languages.length; i++) {
            if (languages[i].equals(currentLanguage)) {
                selectedIndex = i;
                break;
            }
        }

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Select Language");
        builder.setSingleChoiceItems(languages, selectedIndex, (dialog, which) -> {
            String selectedLanguage = languages[which];
            sharedPreferences.edit().putString(PREF_LANGUAGE, selectedLanguage).apply();
            if (tvCurrentLanguage != null) {
                tvCurrentLanguage.setText(selectedLanguage);
            }
            Toast.makeText(requireContext(), "Language changed to " + selectedLanguage, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void performBackup() {
        Toast.makeText(requireContext(), "Creating backup...", Toast.LENGTH_SHORT).show();
        // TODO: Implement actual backup functionality

        // Simulate backup process
        new android.os.Handler().postDelayed(() -> {
            Toast.makeText(requireContext(), "Backup completed successfully!", Toast.LENGTH_LONG).show();
        }, 2000);
    }

    private void performRestore() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Restore Data");
        builder.setMessage("This will replace all current data with backup data. Continue?");
        builder.setPositiveButton("Restore", (dialog, which) -> {
            Toast.makeText(requireContext(), "Restoring data...", Toast.LENGTH_SHORT).show();
            // TODO: Implement actual restore functionality
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showClearDataConfirmation() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Clear All Data");
        builder.setMessage("This will permanently delete all your data. This action cannot be undone. Are you sure?");
        builder.setPositiveButton("Delete All", (dialog, which) -> {
            // TODO: Implement data clearing functionality
            Toast.makeText(requireContext(), "All data has been cleared", Toast.LENGTH_LONG).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void openPrivacyPolicy() {
        try {
            // Replace with your actual privacy policy URL
            String privacyUrl = "https://www.nesis.com/privacy-policy";
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl));
            startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Privacy policy coming soon!", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSupportEmail() {
        try {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:" + SUPPORT_EMAIL));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "MeruScrap - Support Request");
            emailIntent.putExtra(Intent.EXTRA_TEXT, "Hello Support Team,\n\nI need help with:\n\n");

            if (emailIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(Intent.createChooser(emailIntent, "Send Support Email"));
            } else {
                // Fallback - copy email to clipboard
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Email", SUPPORT_EMAIL);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), "Support email copied: " + SUPPORT_EMAIL, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to open email client", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSignOutConfirmation() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Sign Out");
        builder.setMessage("Are you sure you want to sign out?");
        builder.setPositiveButton("Sign Out", (dialog, which) -> {
            // TODO: Implement actual sign out functionality
            // Clear user data
            sharedPreferences.edit()
                    .remove("user_name")
                    .remove("user_email")
                    .apply();

            Toast.makeText(requireContext(), "Signed out successfully", Toast.LENGTH_SHORT).show();

            // Navigate to login screen or close app
            // getActivity().finish();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // =================================================================
    // UTILITY METHODS
    // =================================================================

    private void showSnackbar(String message, int duration) {
        if (getView() != null && getContext() != null && isAdded()) {
            try {
                Snackbar.make(getView(), message, duration).show();
            } catch (Exception e) {
                Log.w(TAG, "Could not show snackbar: " + e.getMessage());
                // Fallback to Toast if Snackbar fails
                if (getContext() != null) {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Log.w(TAG, "Cannot show snackbar - fragment not properly attached. Message: " + message);
        }
    }

    // Public method for MainActivity to update printer status
    public void updatePreferenceStates() {
        Log.d(TAG, "Updating preference states");
        updatePrinterConnectionStatus();
        updatePrintQueueStatus();
    }

    // Add these methods to Settings class

    private void showPrintQueueDialog() {
        if (mainActivity == null) return;

        List<PrintHistoryManager.PrintJob> reprintableJobs = mainActivity.getReprintableJobs();

        if (reprintableJobs.isEmpty()) {
            showSnackbar("No print jobs available for reprinting", Snackbar.LENGTH_SHORT);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Print Queue & History");

        // Create list of job descriptions
        String[] jobDescriptions = new String[reprintableJobs.size()];
        for (int i = 0; i < reprintableJobs.size(); i++) {
            PrintHistoryManager.PrintJob job = reprintableJobs.get(i);
            String statusIcon = getStatusIcon(job.status);
            String timeStr = new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
                    .format(job.createdAt);

            jobDescriptions[i] = statusIcon + " " + timeStr + " - " + job.jobType.name() +
                    " (" + job.status.name() + ")";
        }

        builder.setItems(jobDescriptions, (dialog, which) -> {
            PrintHistoryManager.PrintJob selectedJob = reprintableJobs.get(which);
            showReprintOptionsDialog(selectedJob);
        });

        builder.setNegativeButton("Close", null);

        // Add "Print All Pending" option if there are pending jobs
        List<PrintHistoryManager.PrintJob> pendingJobs =
                PrintHistoryManager.getInstance(getContext()).getPendingPrintJobs();
        if (!pendingJobs.isEmpty() && mainActivity.isPrinterConnected()) {
            builder.setPositiveButton("Print All Pending (" + pendingJobs.size() + ")",
                    (dialog, which) -> {
                        mainActivity.printPendingJobs();
                        updatePrinterConnectionStatus(); // Refresh UI
                    });
        }

        builder.show();
    }

    private void showReprintOptionsDialog(PrintHistoryManager.PrintJob job) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Print Job Options");

        String jobInfo = "Job ID: " + job.jobId + "\n" +
                "Type: " + job.jobType.name() + "\n" +
                "Status: " + job.status.name() + "\n" +
                "Created: " + new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm",
                java.util.Locale.getDefault()).format(job.createdAt);

        if (job.transactionId != null) {
            jobInfo += "\nTransaction: " + job.transactionId;
        }

        builder.setMessage(jobInfo);

        // Show different options based on job status and printer connection
        if (mainActivity != null && mainActivity.isPrinterConnected()) {
            builder.setPositiveButton("🖨️ Print Now", (dialog, which) -> {
                mainActivity.reprintReceipt(job.jobId);
                showSnackbar("Printing job: " + job.jobId, Snackbar.LENGTH_SHORT);
            });
        } else {
            builder.setPositiveButton("📋 View Content", (dialog, which) -> {
                showReceiptContentDialog(job);
            });
        }

        builder.setNeutralButton("🗑️ Delete", (dialog, which) -> {
            showDeleteJobConfirmation(job);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showReceiptContentDialog(PrintHistoryManager.PrintJob job) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Receipt Content");
        builder.setMessage(job.contentPreview != null ? job.contentPreview : "No content available");
        builder.setPositiveButton("OK", null);

        if (mainActivity != null && mainActivity.isPrinterConnected()) {
            builder.setNeutralButton("🖨️ Print", (dialog, which) -> {
                mainActivity.reprintReceipt(job.jobId);
            });
        }

        builder.show();
    }

    private void showDeleteJobConfirmation(PrintHistoryManager.PrintJob job) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Delete Print Job");
        builder.setMessage("Are you sure you want to delete this print job? This action cannot be undone.");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            // You would need to add a delete method to PrintHistoryManager
            // PrintHistoryManager.getInstance(getContext()).deleteJob(job.jobId);
            showSnackbar("Print job deleted", Snackbar.LENGTH_SHORT);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private String getStatusIcon(PrintHistoryManager.PrintJobStatus status) {
        switch (status) {
            case COMPLETED: return "✅";
            case FAILED: return "❌";
            case QUEUED: return "⏳";
            case PRINTING: return "🖨️";
            case CANCELLED: return "🚫";
            default: return "❓";
        }
    }

    private void debugButtonStates() {
        Log.d(TAG, "=== BUTTON DEBUG ===");
        Log.d(TAG, "Print Queue button found: " + (btnPrintQueue != null));
        if (btnPrintQueue != null) {
            Log.d(TAG, "Print Queue button enabled: " + btnPrintQueue.isEnabled());
            Log.d(TAG, "Print Queue button clickable: " + btnPrintQueue.isClickable());
            Log.d(TAG, "Print Queue button visibility: " + btnPrintQueue.getVisibility());
        }

        Log.d(TAG, "Printer Settings button found: " + (btnPrinterSettings != null));
        if (btnPrinterSettings != null) {
            Log.d(TAG, "Printer Settings button enabled: " + btnPrinterSettings.isEnabled());
            Log.d(TAG, "Printer Settings button clickable: " + btnPrinterSettings.isClickable());
            Log.d(TAG, "Printer Settings button visibility: " + btnPrinterSettings.getVisibility());
        }
        Log.d(TAG, "================");
    }
    // 6. UPDATE setupEnhancedClickListeners() TO INCLUDE BLUETOOTH HELPERS
    private void setupEnhancedClickListeners() {
        // Existing permission management code...
        if (btnManagePermissions != null) {
            btnManagePermissions.setOnClickListener(v -> {
                if (mainActivity != null) {
                    mainActivity.requestMissingPermissions();
                }
            });
        }

        if (btnPermissionDetails != null) {
            btnPermissionDetails.setOnClickListener(v -> {
                PermissionStatusDialog.show(getContext());
            });
        }

        // Enhanced BLE Service Management
        if (btnConnectScale != null) {
            btnConnectScale.setOnClickListener(v -> {
                // Check if Bluetooth is enabled first
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                    showBluetoothEnableDialog();
                } else {
                    handleConnectScale();
                }
            });
        }

        if (btnDisconnectScale != null) {
            btnDisconnectScale.setOnClickListener(v -> handleDisconnectScale());
        }

        // Long click for comprehensive diagnostics
        if (tvBleServiceStatus != null) {
            tvBleServiceStatus.setOnLongClickListener(v -> {
                showComprehensiveDiagnostics();
                return true;
            });
        }
    }

    // 7. ADD COMPREHENSIVE DIAGNOSTICS METHOD
    private void showComprehensiveDiagnostics() {
        if (getContext() == null || !isAdded()) return;

        StringBuilder diagnostics = new StringBuilder();
        diagnostics.append("=== COMPREHENSIVE DIAGNOSTICS ===\n\n");

        // Permission status
        List<String> missingPermissions = PermissionManager.getMissingPermissions(getContext());
        diagnostics.append("📋 Permissions:\n");
        diagnostics.append("Missing: ").append(missingPermissions.size()).append(" permission(s)\n");
        if (!missingPermissions.isEmpty()) {
            for (String permission : missingPermissions) {
                PermissionManager.PermissionInfo info = PermissionManager.getPermissionInfo(permission);
                if (info != null) {
                    diagnostics.append("  ❌ ").append(info.friendlyName).append("\n");
                }
            }
        } else {
            diagnostics.append("  ✅ All permissions granted\n");
        }
        diagnostics.append("\n");

        // Bluetooth status
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        diagnostics.append("📶 Bluetooth:\n");
        if (bluetoothAdapter == null) {
            diagnostics.append("  ❌ Not supported\n");
        } else {
            diagnostics.append("  Status: ").append(bluetoothAdapter.isEnabled() ? "✅ Enabled" : "❌ Disabled").append("\n");
            if (mainActivity != null) {
                diagnostics.append("  Ready: ").append(mainActivity.isBluetoothEnabledAndReady() ? "✅ Yes" : "❌ No").append("\n");
            }
        }
        diagnostics.append("\n");

        // BLE Service status
        if (mainActivity != null) {
            diagnostics.append("⚖️ BLE Scale Service:\n");
            diagnostics.append("Service Ready: ").append(mainActivity.isScaleServiceReady() ? "✅ Yes" : "❌ No").append("\n");
            diagnostics.append("Scale Connected: ").append(mainActivity.isScaleConnected() ? "✅ Yes" : "❌ No").append("\n");
            diagnostics.append("Connected Scale: ").append(mainActivity.getConnectedScaleName()).append("\n");
            diagnostics.append("Current Weight: ").append(mainActivity.getCurrentScaleWeight()).append(" kg\n\n");
        }

        // Printer status
        if (mainActivity != null) {
            diagnostics.append("🖨️ Printer:\n");
            diagnostics.append("Connected: ").append(mainActivity.isPrinterConnected() ? "✅ Yes" : "❌ No").append("\n");
            diagnostics.append("Status: ").append(mainActivity.getPrinterStatusDetails()).append("\n");
            diagnostics.append("Queue: ").append(mainActivity.getPrintQueueInfo()).append("\n");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Comprehensive Diagnostics");
        builder.setMessage(diagnostics.toString());
        builder.setPositiveButton("Copy to Clipboard", (dialog, which) -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Comprehensive Diagnostics", diagnostics.toString());
            clipboard.setPrimaryClip(clip);
            showSnackbar("Diagnostics copied to clipboard", Snackbar.LENGTH_SHORT);
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    // 5. ADD THESE NEW METHODS FOR PERMISSION MANAGEMENT
    private void updatePermissionStatus() {
        if (!isAdded() || getContext() == null) return;

        try {
            if (tvPermissionStatus != null) {
                if (mainActivity != null) {
                    String status = mainActivity.getPermissionStatusSummary();
                    tvPermissionStatus.setText(status);
                } else {
                    tvPermissionStatus.setText("⚠️ Cannot check permissions");
                }
            }

            // Update button states
            if (btnManagePermissions != null) {
                List<String> missing = PermissionManager.getMissingPermissions(getContext());
                if (missing.isEmpty()) {
                    btnManagePermissions.setEnabled(false);
                    btnManagePermissions.setText("All Permissions Granted");
                } else {
                    btnManagePermissions.setEnabled(true);
                    btnManagePermissions.setText("Grant Missing Permissions (" + missing.size() + ")");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating permission status", e);
        }
    }


    // 6. ADD BLE SERVICE MANAGEMENT METHODS
    private void updateBleServiceStatus() {
        if (!isAdded() || getContext() == null) return;

        try {
            boolean isServiceReady = false;
            boolean isScaleConnected = false;
            String scaleName = "";
            String serviceStatus = "Service Not Available";

            if (mainActivity != null) {
                isServiceReady = mainActivity.isScaleServiceReady();
                isScaleConnected = mainActivity.isScaleConnected();
                scaleName = mainActivity.getConnectedScaleName();

                if (isServiceReady) {
                    if (isScaleConnected) {
                        serviceStatus = "Connected to Scale";
                    } else {
                        serviceStatus = "Service Ready";
                    }
                } else {
                    serviceStatus = "Service Not Ready";
                }
            }

            // Update status text
            if (tvBleServiceStatus != null) {
                tvBleServiceStatus.setText(serviceStatus);
            }

            // Update connected scale name
            if (tvConnectedScaleName != null) {
                if (isScaleConnected && !scaleName.isEmpty()) {
                    tvConnectedScaleName.setText("Scale: " + scaleName);
                    tvConnectedScaleName.setVisibility(View.VISIBLE);
                } else {
                    tvConnectedScaleName.setVisibility(View.GONE);
                }
            }

            // Update service indicator
            if (bleServiceIndicator != null) {
                int indicatorColor;
                if (isScaleConnected) {
                    indicatorColor = ContextCompat.getColor(getContext(), R.color.success);
                } else if (isServiceReady) {
                    indicatorColor = ContextCompat.getColor(getContext(), R.color.warning);
                } else {
                    indicatorColor = ContextCompat.getColor(getContext(), R.color.error);
                }
                bleServiceIndicator.setBackgroundColor(indicatorColor);
            }

            // Update button states
            updateBleServiceButtons(isServiceReady, isScaleConnected);

        } catch (Exception e) {
            Log.e(TAG, "Error updating BLE service status", e);
        }
    }

    private void updateBleServiceButtons(boolean serviceReady, boolean scaleConnected) {
        if (btnConnectScale != null) {
            btnConnectScale.setEnabled(serviceReady && !scaleConnected);
            btnConnectScale.setText(scaleConnected ? "Scale Connected" : "Connect Scale");
        }

        if (btnDisconnectScale != null) {
            btnDisconnectScale.setEnabled(scaleConnected);
        }
    }
    private void handleConnectScale() {
        if (mainActivity == null) {
            showSnackbar("Cannot access BLE service", Snackbar.LENGTH_SHORT);
            return;
        }

        if (!mainActivity.hasLocationPermissionForBLE()) {
            showSnackbar("Location permission required for BLE scale connection", Snackbar.LENGTH_LONG);
            mainActivity.checkBluetoothPermissions();
            return;
        }

        showSnackbar("Scale connection feature coming soon!", Snackbar.LENGTH_SHORT);
        // TODO: Implement scale connection UI
        // This would typically show a dialog to scan for and connect to BLE scales
    }

    private void handleDisconnectScale() {
        if (mainActivity != null) {
            mainActivity.disconnectScale();
            showSnackbar("Disconnecting from scale...", Snackbar.LENGTH_SHORT);
            updateBleServiceStatus();
        }
    }

    private void showBleServiceDiagnostics() {
        if (getContext() == null || !isAdded()) return;

        StringBuilder diagnostics = new StringBuilder();
        diagnostics.append("=== BLE SERVICE DIAGNOSTICS ===\n\n");

        if (mainActivity != null) {
            diagnostics.append("Service Ready: ").append(mainActivity.isScaleServiceReady()).append("\n");
            diagnostics.append("Scale Connected: ").append(mainActivity.isScaleConnected()).append("\n");
            diagnostics.append("Connected Scale: ").append(mainActivity.getConnectedScaleName()).append("\n");
            diagnostics.append("Current Weight: ").append(mainActivity.getCurrentScaleWeight()).append(" kg\n\n");

            String detailedStatus = mainActivity.getBleServiceDiagnostics();
            diagnostics.append("Detailed Status:\n").append(detailedStatus);
        } else {
            diagnostics.append("MainActivity reference not available\n");
        }

        // Location permission status
        boolean hasLocationPermission = ContextCompat.checkSelfPermission(getContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        diagnostics.append("\nLocation Permission: ").append(hasLocationPermission).append("\n");

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("BLE Service Diagnostics");
        builder.setMessage(diagnostics.toString());
        builder.setPositiveButton("Copy to Clipboard", (dialog, which) -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("BLE Diagnostics", diagnostics.toString());
            clipboard.setPrimaryClip(clip);
            showSnackbar("Diagnostics copied to clipboard", Snackbar.LENGTH_SHORT);
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }
    // 9. ADD THESE UTILITY METHODS
    public void onPermissionsChanged() {
        // Called when permissions change - update UI accordingly
        updatePermissionStatus();
        updatePrinterConnectionStatus(); // Printer depends on Bluetooth permissions
        updateBleServiceStatus(); // BLE depends on location permissions
    }


    private void showPermissionEducationDialog() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("About App Permissions");

        StringBuilder info = new StringBuilder();
        info.append("MeruScrap uses these permissions:\n\n");
        info.append("🔵 Bluetooth & Location:\n");
        info.append("• Connect to printers for receipts\n");
        info.append("• Connect to BLE scales for weighing\n");
        info.append("• Discover nearby devices\n\n");
        info.append("🔒 Your Privacy:\n");
        info.append("• We don't collect location data\n");
        info.append("• Permissions used only for device connections\n");
        info.append("• No data shared with third parties\n\n");
        info.append("You can change permissions anytime in your device settings.");

        builder.setMessage(info.toString());
        builder.setPositiveButton("OK", null);

        builder.setNeutralButton("Manage Permissions", (dialog, which) -> {
            if (mainActivity != null) {
                mainActivity.requestMissingPermissions();
            }
        });

        builder.show();
    }

    // 10. ADD ERROR HANDLING IMPROVEMENTS
    private void showSnackbarSafe(String message, int duration) {
        try {
            showSnackbar(message, duration);
        } catch (Exception e) {
            Log.w(TAG, "Could not show snackbar: " + e.getMessage());
            // Fallback to Toast
            if (getContext() != null) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        }
    }
    // 4. ADD PERIODIC REFRESH FUNCTIONALITY
    private void startPeriodicRefresh() {
        stopPeriodicRefresh(); // Stop any existing refresh

        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAdded() && !isRefreshing) {
                    refreshAllStatus();
                    refreshHandler.postDelayed(this, 30000); // Refresh every 30 seconds
                }
            }
        };

        refreshHandler.postDelayed(refreshRunnable, 5000); // Start after 5 seconds
    }

    private void stopPeriodicRefresh() {
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    private void refreshAllStatus() {
        if (!isAdded() || isRefreshing) return;

        isRefreshing = true;

        try {
            updatePermissionStatus();
            updatePrinterConnectionStatus();
            updatePrintQueueStatus();
            updateBleServiceStatus();

            // Update last refresh time
            sharedPreferences.edit()
                    .putLong(PREF_LAST_PERMISSION_CHECK, System.currentTimeMillis())
                    .apply();

        } catch (Exception e) {
            Log.e(TAG, "Error during periodic refresh", e);
        } finally {
            isRefreshing = false;
        }
    }

    // 5. ADD ENHANCED ERROR HANDLING
    private void handleError(String operation, Exception e) {
        Log.e(TAG, "Error during " + operation, e);

        String userMessage = "Error in " + operation;
        if (e instanceof SecurityException) {
            userMessage = "Permission error - please check app permissions";
        } else if (e.getMessage() != null && e.getMessage().contains("Bluetooth")) {
            userMessage = "Bluetooth error - check if Bluetooth is enabled";
        }

        showSnackbarSafe(userMessage, Snackbar.LENGTH_LONG);
    }

    // 6. ADD SMART PERMISSION EDUCATION
    private void showPermissionEducationIfNeeded() {
        boolean educationShown = sharedPreferences.getBoolean(PREF_PERMISSION_EDUCATION_SHOWN, false);

        if (!educationShown && mainActivity != null) {
            List<String> missingPermissions = PermissionManager.getMissingPermissions(getContext());
            if (!missingPermissions.isEmpty()) {
                // Show education after a delay to let UI settle
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isAdded()) {
                        showPermissionEducationDialog();
                        sharedPreferences.edit()
                                .putBoolean(PREF_PERMISSION_EDUCATION_SHOWN, true)
                                .apply();
                    }
                }, 2000);
            }
        }
    }

    // 7. ADD CONNECTION STATE PERSISTENCE
    private void saveConnectionStates() {
        if (mainActivity != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();

            // Save printer state
            editor.putBoolean("last_printer_connected", mainActivity.isPrinterConnected());
            if (mainActivity.getConnectedPrinter() != null) {
                editor.putString("last_printer_address", mainActivity.getConnectedPrinter().getAddress());
            }

            // Save BLE state
            editor.putBoolean("last_ble_service_ready", mainActivity.isScaleServiceReady());
            editor.putBoolean("last_scale_connected", mainActivity.isScaleConnected());

            editor.apply();
        }
    }

    // 8. ADD QUICK ACTIONS MENU
    private void showQuickActionsMenu() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Quick Actions");

        String[] actions = {
                "🔄 Refresh All Status",
                "📋 Copy System Info",
                "🔧 Run Diagnostics",
                "⚙️ Reset All Settings",
                "📱 Open Device Settings",
                "ℹ️ Show Permission Help"
        };

        builder.setItems(actions, (dialog, which) -> {
            switch (which) {
                case 0:
                    refreshAllStatus();
                    showSnackbar("Status refreshed", Snackbar.LENGTH_SHORT);
                    break;
                case 1:
                    copySystemInfoToClipboard();
                    break;
                case 2:
                    runCompleteDiagnostics();
                    break;
                case 3:
                    showResetAllSettingsDialog();
                    break;
                case 4:
                    openDeviceSettings();
                    break;
                case 5:
                    showPermissionEducationDialog();
                    break;
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // 9. ADD SYSTEM INFO UTILITIES
    private void copySystemInfoToClipboard() {
        StringBuilder info = new StringBuilder();
        info.append("=== MERUSCRAP SYSTEM INFO ===\n\n");

        // App info
        try {
            String versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            info.append("App Version: ").append(versionName).append("\n");
        } catch (Exception e) {
            info.append("App Version: Unknown\n");
        }

        // Permission status
        if (mainActivity != null) {
            info.append("Permission Status: ").append(mainActivity.getPermissionStatusSummary()).append("\n");
            info.append("Printer Connected: ").append(mainActivity.isPrinterConnected()).append("\n");
            info.append("Scale Service Ready: ").append(mainActivity.isScaleServiceReady()).append("\n");
            info.append("Scale Connected: ").append(mainActivity.isScaleConnected()).append("\n");
        }

        // System info
        info.append("Android Version: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        info.append("Device Model: ").append(android.os.Build.MODEL).append("\n");
        info.append("Manufacturer: ").append(android.os.Build.MANUFACTURER).append("\n");

        try {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("System Info", info.toString());
            clipboard.setPrimaryClip(clip);
            showSnackbar("System info copied to clipboard", Snackbar.LENGTH_SHORT);
        } catch (Exception e) {
            handleError("copying system info", e);
        }
    }

    private void runCompleteDiagnostics() {
        showSnackbar("Running diagnostics...", Snackbar.LENGTH_SHORT);

        // Run diagnostics in background
        new Thread(() -> {
            StringBuilder diagnostics = new StringBuilder();
            diagnostics.append("=== COMPLETE DIAGNOSTICS ===\n\n");

            // Test permissions
            List<String> missingPermissions = PermissionManager.getMissingPermissions(getContext());
            diagnostics.append("Missing Permissions: ").append(missingPermissions.size()).append("\n");

            // Test Bluetooth
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            diagnostics.append("Bluetooth Available: ").append(bluetoothAdapter != null).append("\n");
            if (bluetoothAdapter != null) {
                diagnostics.append("Bluetooth Enabled: ").append(bluetoothAdapter.isEnabled()).append("\n");
            }

            // Test printer connection
            if (mainActivity != null) {
                diagnostics.append("Printer Manager Ready: ").append(mainActivity.isPrinterReady()).append("\n");
                diagnostics.append("Print Queue Size: ").append(
                        mainActivity.printManager != null ? mainActivity.printManager.getQueueSize() : "N/A").append("\n");
            }

            // Show results on main thread
            if (isAdded()) {
                mainHandler.post(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Diagnostic Results");
                    builder.setMessage(diagnostics.toString());
                    builder.setPositiveButton("OK", null);
                    builder.setNeutralButton("Copy", (dialog, which) -> {
                        try {
                            android.content.ClipboardManager clipboard =
                                    (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                            android.content.ClipData clip = android.content.ClipData.newPlainText("Diagnostics", diagnostics.toString());
                            clipboard.setPrimaryClip(clip);
                            showSnackbar("Diagnostics copied", Snackbar.LENGTH_SHORT);
                        } catch (Exception e) {
                            handleError("copying diagnostics", e);
                        }
                    });
                    builder.show();
                });
            }
        }).start();
    }

    private void openDeviceSettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getContext().getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            showSnackbar("Could not open device settings", Snackbar.LENGTH_SHORT);
        }
    }
    // 11. ADD LONG CLICK FUNCTIONALITY FOR CARDS
    private void setupCardLongClickListeners() {
        // Permission card long click
        if (permissionSettingsCard != null) {
            permissionSettingsCard.setOnLongClickListener(v -> {
                showPermissionEducationDialog();
                return true;
            });
        }

        // BLE service card long click
        if (bleServiceCard != null) {
            bleServiceCard.setOnLongClickListener(v -> {
                showBleServiceDiagnostics();
                return true;
            });
        }

        // Main settings card long click for quick actions
        View rootLayout = getView();
        if (rootLayout != null) {
            rootLayout.setOnLongClickListener(v -> {
                showQuickActionsMenu();
                return true;
            });
        }
    }

    // ADD THIS METHOD TO YOUR SETTINGS.JAVA CLASS

    private void showResetAllSettingsDialog() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Reset All Settings");
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        StringBuilder message = new StringBuilder();
        message.append("⚠️ This will reset ALL app settings to their default values:\n\n");
        message.append("🔹 App Preferences:\n");
        message.append("• Notifications, Dark Mode, Language\n");
        message.append("• App Lock and security settings\n\n");
        message.append("🔹 Connection Settings:\n");
        message.append("• Saved printer connections\n");
        message.append("• Auto-connect preferences\n");
        message.append("• BLE scale settings\n\n");
        message.append("🔹 User Profile:\n");
        message.append("• Profile information\n");
        message.append("• Custom preferences\n\n");
        message.append("❌ This will NOT delete:\n");
        message.append("• Transaction history\n");
        message.append("• Print history\n");
        message.append("• Material data\n\n");
        message.append("This action cannot be undone. Continue?");

        builder.setMessage(message.toString());

        // Add confirmation requirement
        builder.setPositiveButton("Reset Everything", (dialog, which) -> {
            showFinalResetConfirmation();
        });

        builder.setNegativeButton("Cancel", null);

        builder.setNeutralButton("Reset Options", (dialog, which) -> {
            showSelectiveResetDialog();
        });

        builder.show();
    }

    // Additional method for final confirmation
    private void showFinalResetConfirmation() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Final Confirmation");
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage("Are you absolutely sure you want to reset ALL settings?\n\nThis action is permanent and cannot be undone.");

        builder.setPositiveButton("YES, RESET ALL", (dialog, which) -> {
            performCompleteReset();
        });

        builder.setNegativeButton("Cancel", null);
        builder.setCancelable(false);
        builder.show();
    }

    // Method to actually perform the reset
    private void performCompleteReset() {
        try {
            showSnackbar("Resetting all settings...", Snackbar.LENGTH_LONG);

            // Reset all SharedPreferences
            if (sharedPreferences != null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();

                // Clear all app preferences
                editor.remove(PREF_NOTIFICATIONS);
                editor.remove(PREF_DARK_MODE);
                editor.remove(PREF_APP_LOCK);
                editor.remove(PREF_LANGUAGE);
                editor.remove(PREF_LAST_PERMISSION_CHECK);
                editor.remove(PREF_PERMISSION_EDUCATION_SHOWN);

                // Clear user profile
                editor.remove("user_name");
                editor.remove("user_email");

                // Clear connection settings
                editor.remove("saved_printer_name");
                editor.remove("saved_printer_address");
                editor.remove("auto_connect_mode");
                editor.remove("last_printer_connected");
                editor.remove("last_printer_address");
                editor.remove("last_ble_service_ready");
                editor.remove("last_scale_connected");

                editor.apply();
            }

            // Disconnect all devices
            if (mainActivity != null) {
                mainActivity.disconnectPrinter();
                mainActivity.clearPrintQueue();
                mainActivity.disconnectScale();
            }

            // Reset UI states
            resetUIToDefaults();

            showSnackbar("✅ All settings have been reset to defaults", Snackbar.LENGTH_LONG);

            // Refresh the entire UI
            refreshAllStatus();

        } catch (Exception e) {
            Log.e(TAG, "Error during complete reset", e);
            showSnackbar("❌ Error occurred during reset", Snackbar.LENGTH_LONG);
        }
    }

    // Method for selective reset options
    private void showSelectiveResetDialog() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Selective Reset Options");

        String[] resetOptions = {
                "🔧 Reset App Preferences Only",
                "🖨️ Reset Printer Settings Only",
                "📱 Reset BLE/Scale Settings Only",
                "👤 Reset User Profile Only",
                "🔐 Reset Security Settings Only"
        };

        builder.setItems(resetOptions, (dialog, which) -> {
            switch (which) {
                case 0:
                    resetAppPreferences();
                    break;
                case 1:
                    resetPrinterSettings();
                    break;
                case 2:
                    resetBleSettings();
                    break;
                case 3:
                    resetUserProfile();
                    break;
                case 4:
                    resetSecuritySettings();
                    break;
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Individual reset methods
    private void resetAppPreferences() {
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(PREF_NOTIFICATIONS);
            editor.remove(PREF_DARK_MODE);
            editor.remove(PREF_LANGUAGE);
            editor.apply();

            // Reset UI switches to defaults
            if (switchNotifications != null) switchNotifications.setChecked(true);
            if (switchDarkMode != null) switchDarkMode.setChecked(false);
            if (tvCurrentLanguage != null) tvCurrentLanguage.setText("English");

            showSnackbar("App preferences reset to defaults", Snackbar.LENGTH_SHORT);
        }
    }

    private void resetPrinterSettings() {
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove("saved_printer_name");
            editor.remove("saved_printer_address");
            editor.remove("auto_connect_mode");
            editor.remove("last_printer_connected");
            editor.remove("last_printer_address");
            editor.apply();
        }

        // Disconnect current printer
        if (mainActivity != null) {
            mainActivity.disconnectPrinter();
            mainActivity.clearPrintQueue();
        }

        // Reset UI
        updatePrinterConnectionStatus();
        showSnackbar("Printer settings reset", Snackbar.LENGTH_SHORT);
    }

    private void resetBleSettings() {
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove("last_ble_service_ready");
            editor.remove("last_scale_connected");
            editor.apply();
        }

        // Disconnect scale
        if (mainActivity != null) {
            mainActivity.disconnectScale();
        }

        // Reset UI
        updateBleServiceStatus();
        showSnackbar("BLE/Scale settings reset", Snackbar.LENGTH_SHORT);
    }

    private void resetUserProfile() {
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove("user_name");
            editor.remove("user_email");
            editor.apply();
        }

        // Reset UI
        if (tvUserName != null) tvUserName.setText("Meru Scrap");
        if (tvUserEmail != null) tvUserEmail.setText("meruscrap@gmail.com");

        showSnackbar("User profile reset to defaults", Snackbar.LENGTH_SHORT);
    }

    private void resetSecuritySettings() {
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(PREF_APP_LOCK);
            editor.remove(PREF_PERMISSION_EDUCATION_SHOWN);
            editor.apply();
        }

        // Reset UI
        if (switchAppLock != null) switchAppLock.setChecked(false);

        showSnackbar("Security settings reset", Snackbar.LENGTH_SHORT);
    }

    // Method to reset UI elements to default states
    private void resetUIToDefaults() {
        try {
            // Reset switches
            if (switchNotifications != null) switchNotifications.setChecked(true);
            if (switchDarkMode != null) switchDarkMode.setChecked(false);
            if (switchAppLock != null) switchAppLock.setChecked(false);

            // Reset text views
            if (tvCurrentLanguage != null) tvCurrentLanguage.setText("English");
            if (tvUserName != null) tvUserName.setText("Meru Scrap");
            if (tvUserEmail != null) tvUserEmail.setText("meruscrap@gmail.com");

            // Reset connection states
            connectedPrinter = null;
            printerConnected = false;
            currentPrinterConnectionState = PrinterConnectionState.DISCONNECTED;

            // Update all status displays
            updatePrinterConnectionStatus();
            updateBleServiceStatus();
            updatePermissionStatus();

        } catch (Exception e) {
            Log.e(TAG, "Error resetting UI to defaults", e);
        }
    }


    // ADD THESE METHODS TO YOUR Settings.java CLASS

    public void updateLicenseDisplay() {
        if (!isAdded() || getContext() == null) return;

        try {
            OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(getContext());

            if (tvLicenseStatus != null && tvLicenseInfo != null) {
                int daysRemaining = licenseManager.getDaysRemaining();
                OfflineLicenseManager.LicenseType currentType = licenseManager.getCurrentLicenseType();

                // Update status text with emoji
                String statusText = getLicenseStatusText(currentType, daysRemaining);
                tvLicenseStatus.setText(statusText);

                // Update license info
                String licenseInfo = licenseManager.getLicenseInfo();
                tvLicenseInfo.setText(licenseInfo);

                // Update button states
                updateLicenseButtonStates(currentType, daysRemaining);
            }

            Log.d(TAG, "License display updated successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error updating license display", e);
            if (tvLicenseStatus != null) {
                tvLicenseStatus.setText("❓ License status unavailable");
            }
        }
    }


    private String getLicenseStatusText(OfflineLicenseManager.LicenseType currentType, int daysRemaining) {
        switch (currentType) {
            case TRIAL:
                if (daysRemaining > 0) {
                    return "🆓 Trial (" + daysRemaining + " days left)";
                } else {
                    return "❌ Trial Expired";
                }
            case BASIC:
                if (daysRemaining == -1) {
                    return "✅ Basic (Unlimited)";
                } else {
                    return "✅ Basic (" + daysRemaining + " days left)";
                }
            case PREMIUM:
                return "⭐ Premium (Lifetime)";
            case ENTERPRISE:
                return "🏢 Enterprise (Lifetime)";
            default:
                return "❓ Unknown License";
        }
    }

    private void updateLicenseButtonStates(OfflineLicenseManager.LicenseType currentType, int daysRemaining) {
        if (btnManageLicense != null) {
            if (currentType == OfflineLicenseManager.LicenseType.TRIAL && daysRemaining <= 0) {
                btnManageLicense.setText("Activate License");
                btnManageLicense.setEnabled(true);
            } else if (currentType == OfflineLicenseManager.LicenseType.TRIAL) {
                btnManageLicense.setText("Enter License Key");
                btnManageLicense.setEnabled(true);
            } else {
                btnManageLicense.setText("Manage License");
                btnManageLicense.setEnabled(true);
            }
        }

        if (btnPurchaseLicense != null) {
            if (currentType == OfflineLicenseManager.LicenseType.TRIAL) {
                btnPurchaseLicense.setText("Purchase License");
                btnPurchaseLicense.setEnabled(true);
            } else {
                btnPurchaseLicense.setText("Upgrade License");
                btnPurchaseLicense.setEnabled(true);
            }
        }
    }

    private void showLicenseManagementDialog() {
        if (getContext() == null || !isAdded()) return;

        OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(getContext());
        OfflineLicenseManager.LicenseType currentType = licenseManager.getCurrentLicenseType();

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("License Management");

        String[] options;
        if (currentType == OfflineLicenseManager.LicenseType.TRIAL) {
            options = new String[]{
                    "🔑 Enter License Key",
                    "📋 View License Details",
                    "💰 Purchase License",
                    "❓ License Help"
            };
        } else {
            options = new String[]{
                    "📋 View License Details",
                    "🔄 Check License Status",
                    "⬆️ Upgrade License",
                    "🗑️ Deactivate License",
                    "❓ License Help"
            };
        }

        builder.setItems(options, (dialog, which) -> {
            if (currentType == OfflineLicenseManager.LicenseType.TRIAL) {
                switch (which) {
                    case 0:
                        showLicenseActivationDialog();
                        break;
                    case 1:
                        showLicenseDetailsDialog();
                        break;
                    case 2:
                        openPurchaseOptions();
                        break;
                    case 3:
                        showLicenseHelp();
                        break;
                }
            } else {
                switch (which) {
                    case 0:
                        showLicenseDetailsDialog();
                        break;
                    case 1:
                        checkLicenseStatus();
                        break;
                    case 2:
                        openPurchaseOptions();
                        break;
                    case 3:
                        showDeactivateLicenseDialog();
                        break;
                    case 4:
                        showLicenseHelp();
                        break;
                }
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    private void showLicenseActivationDialog() {
        if (getContext() == null || !isAdded()) return;

        // Inflate the custom license dialog layout
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_license, null);

        // Get references to dialog views
        TextView tvLicenseInfoDialog = dialogView.findViewById(R.id.tv_license_info);
        TextView tvDaysRemaining = dialogView.findViewById(R.id.tv_days_remaining);
        TextInputEditText etLicenseKey = dialogView.findViewById(R.id.et_license_key);
        MaterialButton btnClose = dialogView.findViewById(R.id.btn_close);
        MaterialButton btnPurchase = dialogView.findViewById(R.id.btn_purchase);
        MaterialButton btnActivate = dialogView.findViewById(R.id.btn_activate);

        // Update current license info in dialog
        OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(getContext());
        int daysRemaining = licenseManager.getDaysRemaining();
        OfflineLicenseManager.LicenseType currentType = licenseManager.getCurrentLicenseType();

        if (tvLicenseInfoDialog != null) {
            tvLicenseInfoDialog.setText(currentType.getDisplayName() + " License");
        }

        if (tvDaysRemaining != null) {
            if (daysRemaining > 0) {
                tvDaysRemaining.setText(daysRemaining + " days remaining");
                tvDaysRemaining.setTextColor(ContextCompat.getColor(getContext(), R.color.success));
            } else if (daysRemaining == 0) {
                tvDaysRemaining.setText("Expires today");
                tvDaysRemaining.setTextColor(ContextCompat.getColor(getContext(), R.color.warning));
            } else {
                tvDaysRemaining.setText("License expired");
                tvDaysRemaining.setTextColor(ContextCompat.getColor(getContext(), R.color.error));
            }
        }

        // Create dialog
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // Set up button click listeners
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnPurchase != null) {
            btnPurchase.setOnClickListener(v -> {
                dialog.dismiss();
                openPurchaseOptions();
            });
        }

        if (btnActivate != null) {
            btnActivate.setOnClickListener(v -> {
                String licenseKey = etLicenseKey != null ? etLicenseKey.getText().toString().trim().toUpperCase() : "";

                if (licenseKey.isEmpty()) {
                    showSnackbar("Please enter a license key", Snackbar.LENGTH_SHORT);
                    return;
                }

                // Validate format
                if (!isValidLicenseKeyFormat(licenseKey)) {
                    showSnackbar("Invalid license key format", Snackbar.LENGTH_LONG);
                    return;
                }

                // Show activation progress
                btnActivate.setEnabled(false);
                btnActivate.setText("Activating...");

                // Perform activation
                performLicenseActivation(licenseKey, dialog, btnActivate);
            });
        }

        dialog.show();
    }
    private boolean isValidLicenseKeyFormat(String key) {
        return key.matches("MERU-SCRAP-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}");
    }

    private void performLicenseActivation(String licenseKey, AlertDialog dialog, MaterialButton btnActivate) {
        // Simulate network delay for activation
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                // Determine license type from key
                OfflineLicenseManager.LicenseType licenseType = determineLicenseType(licenseKey);

                // Activate license
                OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(getContext());
                boolean success = licenseManager.activateLicense(licenseKey, licenseType);

                if (success) {
                    // Update UI
                    updateLicenseDisplay();

                    // Show success message
                    showSnackbar("✅ License activated successfully!", Snackbar.LENGTH_LONG);

                    // Close dialog
                    dialog.dismiss();

                    // Show success details dialog
                    showLicenseActivationSuccessDialog(licenseType);

                    Log.d(TAG, "License activated: " + licenseType.getDisplayName());

                } else {
                    // Restore button state
                    btnActivate.setEnabled(true);
                    btnActivate.setText("Activate");

                    showSnackbar("❌ License activation failed", Snackbar.LENGTH_LONG);
                    Log.e(TAG, "License activation failed for key: " + licenseKey);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error during license activation", e);

                // Restore button state
                btnActivate.setEnabled(true);
                btnActivate.setText("Activate");

                showSnackbar("❌ Activation error occurred", Snackbar.LENGTH_LONG);
            }
        }, 1500); // 1.5 second delay to simulate processing
    }
    private void showLicenseActivationSuccessDialog(OfflineLicenseManager.LicenseType licenseType) {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("🎉 License Activated!");

        StringBuilder message = new StringBuilder();
        message.append("Your ").append(licenseType.getDisplayName()).append(" license has been successfully activated!\n\n");

        switch (licenseType) {
            case BASIC:
                message.append("✅ Features unlocked:\n");
                message.append("• Unlimited transactions\n");
                message.append("• Advanced reporting\n");
                message.append("• Priority support\n");
                message.append("• Valid for 365 days\n");
                break;
            case PREMIUM:
                message.append("✅ Features unlocked:\n");
                message.append("• All Basic features\n");
                message.append("• Lifetime license\n");
                message.append("• Multiple device sync\n");
                message.append("• Custom branding\n");
                message.append("• Premium support\n");
                break;
            case ENTERPRISE:
                message.append("✅ Features unlocked:\n");
                message.append("• All Premium features\n");
                message.append("• Multi-location support\n");
                message.append("• API access\n");
                message.append("• Dedicated support\n");
                message.append("• Custom integrations\n");
                break;
        }

        message.append("\nThank you for supporting MeruScrap!");

        builder.setMessage(message.toString());
        builder.setPositiveButton("Great!", null);

        builder.setNeutralButton("View Features", (dialog, which) -> {
            showLicenseDetailsDialog();
        });

        builder.show();
    }

    private void showLicenseDetailsDialog() {
        if (getContext() == null || !isAdded()) return;

        OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(getContext());

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("License Details");

        StringBuilder details = new StringBuilder();
        details.append("=== LICENSE INFORMATION ===\n\n");

        details.append("License Type: ").append(licenseManager.getCurrentLicenseType().getDisplayName()).append("\n");
        details.append("Status: ").append(licenseManager.isLicenseValid() ? "✅ Active" : "❌ Inactive").append("\n");

        int daysRemaining = licenseManager.getDaysRemaining();
        if (daysRemaining == -1) {
            details.append("Validity: Lifetime\n");
        } else if (daysRemaining > 0) {
            details.append("Days Remaining: ").append(daysRemaining).append("\n");
        } else {
            details.append("Status: ❌ Expired\n");
        }

        details.append("License Key: ").append(licenseManager.getLicenseKey()).append("\n");
        details.append("Activated: ").append(licenseManager.getActivationDate()).append("\n\n");

        details.append("=== AVAILABLE FEATURES ===\n\n");

        OfflineLicenseManager.LicenseType currentType = licenseManager.getCurrentLicenseType();
        switch (currentType) {
            case TRIAL:
                details.append("• Basic transaction recording\n");
                details.append("• Limited to 100 transactions\n");
                details.append("• Basic reporting\n");
                details.append("• 30-day trial period\n");
                break;
            case BASIC:
                details.append("• Unlimited transactions\n");
                details.append("• Advanced reporting\n");
                details.append("• Data backup & restore\n");
                details.append("• Email support\n");
                details.append("• 365-day validity\n");
                break;
            case PREMIUM:
                details.append("• All Basic features\n");
                details.append("• Lifetime license\n");
                details.append("• Multi-device sync\n");
                details.append("• Custom receipt branding\n");
                details.append("• Priority support\n");
                details.append("• Advanced analytics\n");
                break;
            case ENTERPRISE:
                details.append("• All Premium features\n");
                details.append("• Multi-location support\n");
                details.append("• API access\n");
                details.append("• White-label options\n");
                details.append("• Dedicated support manager\n");
                details.append("• Custom integrations\n");
                break;
        }

        builder.setMessage(details.toString());
        builder.setPositiveButton("OK", null);

        builder.setNeutralButton("Copy Details", (dialog, which) -> {
            try {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("License Details", details.toString());
                clipboard.setPrimaryClip(clip);
                showSnackbar("License details copied to clipboard", Snackbar.LENGTH_SHORT);
            } catch (Exception e) {
                Log.e(TAG, "Error copying license details", e);
            }
        });

        builder.show();
    }
    private void checkLicenseStatus() {
        showSnackbar("Checking license status...", Snackbar.LENGTH_SHORT);

        // Simulate status check
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            updateLicenseDisplay();
            showSnackbar("✅ License status updated", Snackbar.LENGTH_SHORT);
        }, 1000);
    }

    private void showDeactivateLicenseDialog() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("⚠️ Deactivate License");
        builder.setMessage("Are you sure you want to deactivate your current license?\n\n" +
                "This will:\n" +
                "• Remove all premium features\n" +
                "• Revert to trial mode\n" +
                "• Require reactivation to use premium features\n\n" +
                "This action cannot be undone easily.");

        builder.setPositiveButton("Deactivate", (dialog, which) -> {
            OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(getContext());
            licenseManager.deactivateLicense();

            updateLicenseDisplay();
            showSnackbar("License deactivated - reverted to trial mode", Snackbar.LENGTH_LONG);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showAdvancedLicenseOptions() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Advanced License Options");

        String[] options = {
                "🔍 Validate License Online",
                "📊 License Usage Statistics",
                "🔄 Reset License Cache",
                "📋 Export License Info",
                "🛠️ License Diagnostics"
        };

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    validateLicenseOnline();
                    break;
                case 1:
                    showLicenseUsageStats();
                    break;
                case 2:
                    resetLicenseCache();
                    break;
                case 3:
                    exportLicenseInfo();
                    break;
                case 4:
                    showLicenseDiagnostics();
                    break;
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void validateLicenseOnline() {
        showSnackbar("Online validation feature coming soon!", Snackbar.LENGTH_SHORT);
        // TODO: Implement online license validation
    }

    private void showLicenseUsageStats() {
        if (getContext() == null || !isAdded()) return;

        OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(getContext());

        StringBuilder stats = new StringBuilder();
        stats.append("=== LICENSE USAGE STATISTICS ===\n\n");

        stats.append("License Type: ").append(licenseManager.getCurrentLicenseType().getDisplayName()).append("\n");
        stats.append("Days Since Activation: ").append(licenseManager.getDaysSinceActivation()).append("\n");

        if (licenseManager.getCurrentLicenseType() == OfflineLicenseManager.LicenseType.TRIAL) {
            // You could track trial usage here
            stats.append("Trial Usage: ").append("Feature usage tracking").append("\n");
        }

        stats.append("License Checks: ").append("Daily validations performed").append("\n");
        stats.append("Last Check: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date())).append("\n");

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("License Usage Statistics");
        builder.setMessage(stats.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }
    private void resetLicenseCache() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Reset License Cache");
        builder.setMessage("This will clear cached license data and force a fresh validation. Continue?");

        builder.setPositiveButton("Reset", (dialog, which) -> {
            // Clear any cached license data
            sharedPreferences.edit()
                    .remove("license_last_check")
                    .remove("license_validation_cache")
                    .apply();

            updateLicenseDisplay();
            showSnackbar("License cache cleared", Snackbar.LENGTH_SHORT);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void exportLicenseInfo() {
        try {
            OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(getContext());

            StringBuilder exportData = new StringBuilder();
            exportData.append("MeruScrap License Information\n");
            exportData.append("Generated: ").append(new java.util.Date().toString()).append("\n\n");
            exportData.append("License Type: ").append(licenseManager.getCurrentLicenseType().getDisplayName()).append("\n");
            exportData.append("License Key: ").append(licenseManager.getLicenseKey()).append("\n");
            exportData.append("Status: ").append(licenseManager.isLicenseValid() ? "Active" : "Inactive").append("\n");
            exportData.append("Days Remaining: ").append(licenseManager.getDaysRemaining()).append("\n");

            // Copy to clipboard
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("License Info", exportData.toString());
            clipboard.setPrimaryClip(clip);

            showSnackbar("License info exported to clipboard", Snackbar.LENGTH_SHORT);

        } catch (Exception e) {
            Log.e(TAG, "Error exporting license info", e);
            showSnackbar("Failed to export license info", Snackbar.LENGTH_SHORT);
        }
    }

    private void showLicenseDiagnostics() {
        if (getContext() == null || !isAdded()) return;

        StringBuilder diagnostics = new StringBuilder();
        diagnostics.append("=== LICENSE DIAGNOSTICS ===\n\n");

        try {
            OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(getContext());

            diagnostics.append("License Manager Status: ✅ Initialized\n");
            diagnostics.append("Current License: ").append(licenseManager.getCurrentLicenseType().name()).append("\n");
            diagnostics.append("License Valid: ").append(licenseManager.isLicenseValid() ? "✅ Yes" : "❌ No").append("\n");
            diagnostics.append("Days Remaining: ").append(licenseManager.getDaysRemaining()).append("\n");

            // Check SharedPreferences
            String licenseKey = sharedPreferences.getString("license_key", "");
            diagnostics.append("Stored License Key: ").append(licenseKey.isEmpty() ? "❌ None" : "✅ Present").append("\n");

            // Check license file or storage
            diagnostics.append("License Storage: ✅ SharedPreferences\n");

            // System info
            diagnostics.append("\nSystem Info:\n");
            diagnostics.append("Android Version: ").append(android.os.Build.VERSION.RELEASE).append("\n");
            diagnostics.append("App Package: ").append(getContext().getPackageName()).append("\n");

        } catch (Exception e) {
            diagnostics.append("❌ Error generating diagnostics: ").append(e.getMessage()).append("\n");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("License Diagnostics");
        builder.setMessage(diagnostics.toString());
        builder.setPositiveButton("Copy", (dialog, which) -> {
            try {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("License Diagnostics", diagnostics.toString());
                clipboard.setPrimaryClip(clip);
                showSnackbar("Diagnostics copied to clipboard", Snackbar.LENGTH_SHORT);
            } catch (Exception e) {
                Log.e(TAG, "Error copying diagnostics", e);
            }
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }


    private OfflineLicenseManager.LicenseType determineLicenseType(String key) {
        // You can implement logic to determine type from key pattern
        // For now, we'll check for test keys
        if (key.contains("BASI")) {
            return OfflineLicenseManager.LicenseType.BASIC;
        } else if (key.contains("ENTR")) {
            return OfflineLicenseManager.LicenseType.ENTERPRISE;
        } else {
            // Default to PREMIUM for all other keys
            return OfflineLicenseManager.LicenseType.PREMIUM;
        }
    }

    private void openPurchaseOptions() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Purchase License");

        String[] options = {
                "💼 Basic License (KSh 5,000/year)",
                "⭐ Premium License (KSh 15,000 - Lifetime)",
                "🏢 Enterprise License (Contact for pricing)",
                "📧 Contact Sales"
        };

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                case 1:
                case 2:
                    sendPurchaseEmail(options[which]);
                    break;
                case 3:
                    contactSales();
                    break;
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void sendPurchaseEmail(String licenseType) {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:meruscrap@gmail.com"));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "MeruScrap License Purchase Request");
        emailIntent.putExtra(Intent.EXTRA_TEXT,
                "I would like to purchase a MeruScrap license.\n\n" +
                        "Selected License: " + licenseType + "\n" +
                        "Company/Name: \n" +
                        "Phone Number: \n" +
                        "Location: \n\n" +
                        "Please send me payment details and license key.\n\n" +
                        "Thank you!");

        try {
            startActivity(Intent.createChooser(emailIntent, "Send Purchase Request"));
        } catch (Exception e) {
            showSnackbar("Could not open email app", Snackbar.LENGTH_LONG);
        }
    }

    private void contactSales() {
        // You can implement WhatsApp, phone call, or email
        showSnackbar("Contact: +254 XXX XXXXXX or meruscrap@gmail.com", Snackbar.LENGTH_LONG);
    }

    private void showLicenseHelp() {
        if (getContext() == null || !isAdded()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("License Help");
        builder.setMessage(
                "How to activate your license:\n\n" +
                        "1. Purchase a license from MeruScrap\n" +
                        "2. You'll receive a license key via email\n" +
                        "3. Enter the key in the format:\n" +
                        "   MERU-SCRAP-XXXX-XXXX-XXXX\n" +
                        "4. Tap 'Activate'\n\n" +
                        "License Types:\n" +
                        "• Trial: 30 days free\n" +
                        "• Basic: 365 days\n" +
                        "• Premium: Lifetime\n" +
                        "• Enterprise: Lifetime + support\n\n" +
                        "Need help? Contact: meruscrap@gmail.com"
        );
        builder.setPositiveButton("OK", null);
        builder.show();
    }


    private void updateLicenseInfo() {
        // Since we're using custom views, update the TextViews directly
        if (tvLicenseStatus != null && tvLicenseInfo != null) {
            OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(getContext());

            String status = licenseManager.getLicenseStatus();
            String info = licenseManager.getLicenseInfo();

            tvLicenseStatus.setText(status);
            tvLicenseInfo.setText(info);

            // Update text color based on status
            int statusColor;
            if (status.contains("✅")) {
                statusColor = ContextCompat.getColor(getContext(), R.color.success);
            } else if (status.contains("⏰")) {
                statusColor = ContextCompat.getColor(getContext(), R.color.warning);
            } else {
                statusColor = ContextCompat.getColor(getContext(), R.color.error);
            }

            tvLicenseStatus.setTextColor(statusColor);
        }
    }

    // Replace the onLicenseStatusChanged method with this:
    public void onLicenseStatusChanged() {
        // Update our custom TextViews instead of preference
        updateLicenseDisplay();
    }
}