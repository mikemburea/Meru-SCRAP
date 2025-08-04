package com.example.meruscrap;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.example.meruscrap.MainActivity;
import com.example.meruscrap.R;
import com.example.meruscrap.PrintHistoryManager;
import com.example.meruscrap.PrinterPreferencesManager;
import com.example.meruscrap.PrinterDiagnosticUtility;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PrinterSettingsFragment extends PreferenceFragmentCompat implements
        PrinterDiagnosticUtility.DiagnosticListener {

    private MainActivity mainActivity;
    private PrinterPreferencesManager prefsManager;
    private PrintHistoryManager historyManager;
    private PrinterDiagnosticUtility diagnosticUtility;

    // Preferences
    private Preference savedPrinterPref;
    private Preference diagnosticPref;
    private Preference testPrintPref;
    private Preference printHistoryPref;
    private Preference clearHistoryPref;
    private SwitchPreferenceCompat autoConnectPref;
    private SwitchPreferenceCompat printHeaderPref;
    private SwitchPreferenceCompat printFooterPref;
    private SwitchPreferenceCompat printQrCodePref;
    private SwitchPreferenceCompat autoCutPref;
    private SwitchPreferenceCompat drawerKickPref;
    private EditTextPreference businessNamePref;
    private EditTextPreference businessAddressPref;
    private EditTextPreference businessPhonePref;
    private SeekBarPreference receiptWidthPref;
    private SeekBarPreference connectionTimeoutPref;
    private SeekBarPreference printRetriesPref;
    private ListPreference fontSizePref;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            mainActivity = (MainActivity) context;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.printer_preferences, rootKey);

        prefsManager = new PrinterPreferencesManager(getContext());
        historyManager = PrintHistoryManager.getInstance(getContext());
        diagnosticUtility = new PrinterDiagnosticUtility(getContext());
        diagnosticUtility.setDiagnosticListener(this);

        initializePreferences();
        updatePreferenceStates();
    }

    private void initializePreferences() {
        // Find all preferences
        savedPrinterPref = findPreference("saved_printer");
        diagnosticPref = findPreference("run_diagnostic");
        testPrintPref = findPreference("test_print");
        printHistoryPref = findPreference("print_history");
        clearHistoryPref = findPreference("clear_history");

        autoConnectPref = findPreference("auto_connect");
        printHeaderPref = findPreference("print_header");
        printFooterPref = findPreference("print_footer");
        printQrCodePref = findPreference("print_qr_code");
        autoCutPref = findPreference("auto_cut");
        drawerKickPref = findPreference("drawer_kick");

        businessNamePref = findPreference("business_name");
        businessAddressPref = findPreference("business_address");
        businessPhonePref = findPreference("business_phone");

        receiptWidthPref = findPreference("receipt_width");
        connectionTimeoutPref = findPreference("connection_timeout");
        printRetriesPref = findPreference("print_retries");
        fontSizePref = findPreference("font_size");

        // Set up click listeners
        setupClickListeners();

        // Set up change listeners
        setupChangeListeners();

        // Load current values
        loadCurrentValues();
    }

    // Update the setupClickListeners method to include queue status
    private void setupClickListeners() {
        if (savedPrinterPref != null) {
            savedPrinterPref.setOnPreferenceClickListener(preference -> {
                showSavedPrinterDialog();
                return true;
            });
        }

        if (diagnosticPref != null) {
            diagnosticPref.setOnPreferenceClickListener(preference -> {
                runDiagnostic();
                return true;
            });
        }

        if (testPrintPref != null) {
            testPrintPref.setOnPreferenceClickListener(preference -> {
                performTestPrint();
                return true;
            });
        }

        if (printHistoryPref != null) {
            printHistoryPref.setOnPreferenceClickListener(preference -> {
                showPrintHistory();
                return true;
            });
        }

        if (clearHistoryPref != null) {
            clearHistoryPref.setOnPreferenceClickListener(preference -> {
                showClearHistoryConfirmation();
                return true;
            });
        }

        // Add print queue status preference if it exists
        Preference queueStatusPref = findPreference("print_queue_status");
        if (queueStatusPref != null) {
            queueStatusPref.setOnPreferenceClickListener(preference -> {
                showPrintQueueStatus();
                return true;
            });
        }
    }
    private void setupChangeListeners() {
        if (autoConnectPref != null) {
            autoConnectPref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setAutoConnect((Boolean) newValue);
                return true;
            });
        }

        if (printHeaderPref != null) {
            printHeaderPref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setPrintHeader((Boolean) newValue);
                return true;
            });
        }

        if (printFooterPref != null) {
            printFooterPref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setPrintFooter((Boolean) newValue);
                return true;
            });
        }

        if (printQrCodePref != null) {
            printQrCodePref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setPrintQRCode((Boolean) newValue);
                return true;
            });
        }

        if (autoCutPref != null) {
            autoCutPref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setAutoCut((Boolean) newValue);
                return true;
            });
        }

        if (drawerKickPref != null) {
            drawerKickPref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setDrawerKick((Boolean) newValue);
                return true;
            });
        }

        if (businessNamePref != null) {
            businessNamePref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setBusinessName((String) newValue);
                updatePreferenceSummary(businessNamePref, (String) newValue);
                return true;
            });
        }

        if (businessAddressPref != null) {
            businessAddressPref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setBusinessAddress((String) newValue);
                updatePreferenceSummary(businessAddressPref, (String) newValue);
                return true;
            });
        }

        if (businessPhonePref != null) {
            businessPhonePref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setBusinessPhone((String) newValue);
                updatePreferenceSummary(businessPhonePref, (String) newValue);
                return true;
            });
        }

        if (receiptWidthPref != null) {
            receiptWidthPref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setReceiptWidth((Integer) newValue);
                return true;
            });
        }

        if (connectionTimeoutPref != null) {
            connectionTimeoutPref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setConnectionTimeout((Integer) newValue);
                return true;
            });
        }

        if (printRetriesPref != null) {
            printRetriesPref.setOnPreferenceChangeListener((preference, newValue) -> {
                prefsManager.setPrintRetries((Integer) newValue);
                return true;
            });
        }

        if (fontSizePref != null) {
            fontSizePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String fontSize = (String) newValue;
                prefsManager.setFontSize(PrinterPreferencesManager.FontSize.valueOf(fontSize));
                return true;
            });
        }
    }

    private void loadCurrentValues() {
        PrinterPreferencesManager.PrinterSettings settings = prefsManager.getAllSettings();

        // Load switch preferences
        if (autoConnectPref != null) autoConnectPref.setChecked(settings.autoConnect);
        if (printHeaderPref != null) printHeaderPref.setChecked(settings.printHeader);
        if (printFooterPref != null) printFooterPref.setChecked(settings.printFooter);
        if (printQrCodePref != null) printQrCodePref.setChecked(settings.printQRCode);
        if (autoCutPref != null) autoCutPref.setChecked(settings.autoCut);
        if (drawerKickPref != null) drawerKickPref.setChecked(settings.drawerKick);

        // Load text preferences
        if (businessNamePref != null) {
            businessNamePref.setText(settings.businessName);
            updatePreferenceSummary(businessNamePref, settings.businessName);
        }
        if (businessAddressPref != null) {
            businessAddressPref.setText(settings.businessAddress);
            updatePreferenceSummary(businessAddressPref, settings.businessAddress);
        }
        if (businessPhonePref != null) {
            businessPhonePref.setText(settings.businessPhone);
            updatePreferenceSummary(businessPhonePref, settings.businessPhone);
        }

        // Load numeric preferences
        if (receiptWidthPref != null) receiptWidthPref.setValue(settings.receiptWidth);
        if (connectionTimeoutPref != null) connectionTimeoutPref.setValue(settings.connectionTimeout);
        if (printRetriesPref != null) printRetriesPref.setValue(settings.printRetries);

        // Load list preferences
        if (fontSizePref != null) fontSizePref.setValue(settings.fontSize.name());
    }

    // Enhanced updatePreferenceStates method
    public void updatePreferenceStates() {
        // Update saved printer preference
        String savedPrinter = prefsManager.getSavedPrinterName();
        if (savedPrinterPref != null) {
            if (savedPrinter != null && !savedPrinter.equals("Unknown Printer")) {
                savedPrinterPref.setSummary("Current: " + savedPrinter);
            } else {
                savedPrinterPref.setSummary("No printer saved");
            }
        }

        // Update test print preference based on connection status
        if (testPrintPref != null) {
            boolean isConnected = mainActivity != null && mainActivity.isPrinterConnected();
            boolean isReady = mainActivity != null && mainActivity.isPrinterReady();

            testPrintPref.setEnabled(isConnected);

            if (!isConnected) {
                testPrintPref.setSummary("Connect to printer first");
            } else if (!isReady) {
                testPrintPref.setSummary("Printer is busy");
            } else {
                testPrintPref.setSummary("Printer is ready");
            }
        }

        // Update diagnostic preference
        if (diagnosticPref != null) {
            BluetoothDevice connectedPrinter = mainActivity != null ? mainActivity.getConnectedPrinter() : null;
            diagnosticPref.setEnabled(connectedPrinter != null);
            diagnosticPref.setSummary(connectedPrinter != null ? "Run comprehensive diagnostic" : "Select a printer first");
        }

        // Update print queue status preference if it exists
        Preference queueStatusPref = findPreference("print_queue_status");
        if (queueStatusPref != null && mainActivity != null) {
            String statusDetails = mainActivity.getPrinterStatusDetails();
            queueStatusPref.setSummary(statusDetails);
            queueStatusPref.setEnabled(mainActivity.isPrinterConnected());
        }
    }
    // Add method to show enhanced printer status dialog
    private void showEnhancedPrinterStatus() {
        if (mainActivity == null) {
            Toast.makeText(getContext(), "Cannot access printer status", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder statusText = new StringBuilder();

        // Connection status
        statusText.append("Connection Status: ");
        if (mainActivity.isPrinterConnected()) {
            statusText.append("Connected\n");

            BluetoothDevice printer = mainActivity.getConnectedPrinter();
            if (printer != null) {
                statusText.append("Device: ").append(printer.getName()).append("\n");
                statusText.append("Address: ").append(printer.getAddress()).append("\n");
            }

            statusText.append("\nPrinter Status: ").append(mainActivity.getPrinterStatusDetails()).append("\n");
            statusText.append("Queue Status: ").append(mainActivity.getPrintQueueInfo()).append("\n");

        } else {
            statusText.append("Disconnected\n");
        }

        // Show auto-connect option if disconnected
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Printer Status");
        builder.setMessage(statusText.toString());
        builder.setPositiveButton("OK", null);

        if (!mainActivity.isPrinterConnected()) {
            builder.setNeutralButton("Auto Connect", (dialog, which) -> {
                mainActivity.autoConnectToSavedPrinter();
                Toast.makeText(getContext(), "Attempting to connect to saved printer...", Toast.LENGTH_SHORT).show();
            });
        }

        builder.show();
    }

    private void showSavedPrinterDialog() {
        String savedAddress = prefsManager.getSavedPrinterAddress();
        String savedName = prefsManager.getSavedPrinterName();

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Saved Printer");

        if (savedAddress != null) {
            String message = "Name: " + savedName + "\nAddress: " + savedAddress;
            builder.setMessage(message);
            builder.setPositiveButton("Clear", (dialog, which) -> {
                prefsManager.clearSavedPrinter();
                updatePreferenceStates();
                Toast.makeText(getContext(), "Saved printer cleared", Toast.LENGTH_SHORT).show();
            });
            builder.setNegativeButton("Cancel", null);
        } else {
            builder.setMessage("No printer is currently saved. Connect to a printer to save it.");
            builder.setPositiveButton("OK", null);
        }

        builder.show();
    }

    private void runDiagnostic() {
        if (mainActivity == null) {
            Toast.makeText(getContext(), "Cannot access printer", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothDevice connectedPrinter = mainActivity.getConnectedPrinter();
        if (connectedPrinter == null) {
            Toast.makeText(getContext(), "No printer selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(getContext())
                .setTitle("Running Diagnostic")
                .setMessage("Checking printer connectivity...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        diagnosticUtility.runFullDiagnostic(connectedPrinter);
    }


    private void showPrintHistory() {
        List<PrintHistoryManager.PrintJob> recentJobs = historyManager.getRecentPrintJobs(20);

        if (recentJobs.isEmpty()) {
            Toast.makeText(getContext(), "No print history available", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder historyText = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());

        for (PrintHistoryManager.PrintJob job : recentJobs) {
            historyText.append(dateFormat.format(job.createdAt))
                    .append(" - ")
                    .append(job.jobType.name())
                    .append(" (")
                    .append(job.status.name())
                    .append(")\n");
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Recent Print Jobs")
                .setMessage(historyText.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("View Statistics", (dialog, which) -> showPrintStatistics())
                .show();
    }

    private void showPrintStatistics() {
        long weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
        long now = System.currentTimeMillis();

        PrintHistoryManager.PrintStatistics stats = historyManager.getPrintStatistics(weekAgo, now);

        String statsText = String.format(Locale.getDefault(),
                "Last 7 Days Statistics:\n\n" +
                        "Total Jobs: %d\n" +
                        "Successful: %d\n" +
                        "Failed: %d\n" +
                        "Success Rate: %.1f%%",
                stats.totalJobs, stats.successfulJobs, stats.failedJobs, stats.successRate);

        new AlertDialog.Builder(getContext())
                .setTitle("Print Statistics")
                .setMessage(statsText)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showClearHistoryConfirmation() {
        new AlertDialog.Builder(getContext())
                .setTitle("Clear Print History")
                .setMessage("Are you sure you want to clear all print history? This action cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    historyManager.clearAllHistory();
                    Toast.makeText(getContext(), "Print history cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updatePreferenceSummary(EditTextPreference preference, String value) {
        if (value != null && !value.trim().isEmpty()) {
            preference.setSummary(value);
        } else {
            preference.setSummary("Not set");
        }
    }

    // DiagnosticListener implementation
    @Override
    public void onDiagnosticStarted() {
        // Progress dialog is already shown
    }

    @Override
    public void onDiagnosticStep(String step, PrinterDiagnosticUtility.DiagnosticResult result) {
        // Could update progress dialog with current step
    }

    @Override
    public void onDiagnosticCompleted(List<PrinterDiagnosticUtility.DiagnosticResult> results,
                                      List<String> recommendations) {
        // Dismiss progress dialog and show results
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                showDiagnosticResults(results, recommendations);
            });
        }
    }

    // Update the performTestPrint method
    private void performTestPrint() {
        if (mainActivity != null) {
            if (!mainActivity.isPrinterConnected()) {
                Toast.makeText(getContext(), "No printer connected", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!mainActivity.isPrinterReady()) {
                Toast.makeText(getContext(), "Printer is busy, please wait", Toast.LENGTH_SHORT).show();
                return;
            }

            mainActivity.printTestPage();
            Toast.makeText(getContext(), "Test page queued for printing", Toast.LENGTH_SHORT).show();

        } else {
            Toast.makeText(getContext(), "Cannot access printer", Toast.LENGTH_SHORT).show();
        }
    }

    // Add a new method to show print queue status
    private void showPrintQueueStatus() {
        if (mainActivity == null) {
            Toast.makeText(getContext(), "Cannot access print manager", Toast.LENGTH_SHORT).show();
            return;
        }

        String queueInfo = mainActivity.getPrintQueueInfo();
        String statusDetails = mainActivity.getPrinterStatusDetails();

        String message = "Printer Status: " + statusDetails + "\n\n" +
                "Queue Status: " + queueInfo;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Print Manager Status");
        builder.setMessage(message);
        builder.setPositiveButton("OK", null);

        // Add clear queue option if there are jobs waiting
        if (mainActivity.getPrintQueueInfo().contains("jobs")) {
            builder.setNeutralButton("Clear Queue", (dialog, which) -> {
                new AlertDialog.Builder(getContext())
                        .setTitle("Clear Print Queue")
                        .setMessage("Are you sure you want to clear all pending print jobs?")
                        .setPositiveButton("Clear", (d2, w2) -> {
                            mainActivity.clearPrintQueue();
                            Toast.makeText(getContext(), "Print queue cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        builder.show();
    }

    private void showDiagnosticResults(List<PrinterDiagnosticUtility.DiagnosticResult> results,
                                       List<String> recommendations) {
        StringBuilder resultText = new StringBuilder();

        // Add results summary
        int passed = 0, warnings = 0, failed = 0;
        for (PrinterDiagnosticUtility.DiagnosticResult result : results) {
            switch (result.status) {
                case PASSED: passed++; break;
                case WARNING: warnings++; break;
                case FAILED: failed++; break;
            }
        }

        resultText.append(String.format("Results: %d passed, %d warnings, %d failed\n\n",
                passed, warnings, failed));

        // Add detailed results
        for (PrinterDiagnosticUtility.DiagnosticResult result : results) {
            String statusIcon;
            switch (result.status) {
                case PASSED: statusIcon = "✓"; break;
                case WARNING: statusIcon = "⚠"; break;
                case FAILED: statusIcon = "✗"; break;
                default: statusIcon = "?"; break;
            }

            resultText.append(statusIcon)
                    .append(" ")
                    .append(result.testName)
                    .append(": ")
                    .append(result.message)
                    .append("\n");
        }

        if (!recommendations.isEmpty()) {
            resultText.append("\nRecommendations:\n");
            for (String recommendation : recommendations) {
                resultText.append(recommendation).append("\n");
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Diagnostic Results")
                .setMessage(resultText.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferenceStates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (diagnosticUtility != null) {
            diagnosticUtility.shutdown();
        }
    }
}