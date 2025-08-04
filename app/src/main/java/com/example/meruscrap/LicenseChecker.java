package com.example.meruscrap;

import android.content.Context;
import android.content.DialogInterface;
import androidx.appcompat.app.AlertDialog;

public class LicenseChecker {

    public interface LicenseCheckCallback {
        void onLicenseValid();
        void onLicenseDenied();
    }

    /**
     * Check license and show appropriate dialog if invalid
     * @return true if license is valid, false otherwise
     */
    public static boolean checkLicense(Context context, String operation) {
        OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(context);

        if (licenseManager.validateLicense()) {
            return true;
        }

        // License is invalid - show appropriate message
        if (licenseManager.isTrialExpired()) {
            showTrialExpiredDialog(context, operation);
        } else {
            showLicenseRequiredDialog(context, operation);
        }

        return false;
    }

    /**
     * Check license with callback
     */
    public static void checkLicenseWithCallback(Context context, String operation,
                                                LicenseCheckCallback callback) {
        OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(context);

        if (licenseManager.validateLicense()) {
            callback.onLicenseValid();
        } else {
            // Show dialog and handle callback
            if (licenseManager.isTrialExpired()) {
                showTrialExpiredDialogWithCallback(context, operation, callback);
            } else {
                showLicenseRequiredDialogWithCallback(context, operation, callback);
            }
        }
    }

    private static void showTrialExpiredDialog(Context context, String operation) {
        new AlertDialog.Builder(context)
                .setTitle("Trial Period Expired")
                .setMessage("Your 30-day trial has expired.\n\n" +
                        "To " + operation + ", please activate a license.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("View License Options", (dialog, which) -> {
                    showLicenseDialog(context);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showLicenseRequiredDialog(Context context, String operation) {
        new AlertDialog.Builder(context)
                .setTitle("License Required")
                .setMessage("A valid license is required to " + operation + ".")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("Enter License", (dialog, which) -> {
                    showLicenseDialog(context);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showTrialExpiredDialogWithCallback(Context context, String operation,
                                                           LicenseCheckCallback callback) {
        new AlertDialog.Builder(context)
                .setTitle("Trial Period Expired")
                .setMessage("Your 30-day trial has expired.\n\n" +
                        "To " + operation + ", please activate a license.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("View License Options", (dialog, which) -> {
                    showLicenseDialogWithCallback(context, callback);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    callback.onLicenseDenied();
                })
                .setOnCancelListener(dialog -> callback.onLicenseDenied())
                .show();
    }

    private static void showLicenseRequiredDialogWithCallback(Context context, String operation,
                                                              LicenseCheckCallback callback) {
        new AlertDialog.Builder(context)
                .setTitle("License Required")
                .setMessage("A valid license is required to " + operation + ".")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("Enter License", (dialog, which) -> {
                    showLicenseDialogWithCallback(context, callback);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    callback.onLicenseDenied();
                })
                .setOnCancelListener(dialog -> callback.onLicenseDenied())
                .show();
    }

    private static void showLicenseDialog(Context context) {
        LicenseDialog licenseDialog = new LicenseDialog(context,
                new LicenseDialog.OnLicenseStatusChangeListener() {
                    @Override
                    public void onLicenseActivated() {
                        // License activated
                    }

                    @Override
                    public void onLicenseExpired() {
                        // License expired
                    }
                });
        licenseDialog.show();
    }

    private static void showLicenseDialogWithCallback(Context context, LicenseCheckCallback callback) {
        LicenseDialog licenseDialog = new LicenseDialog(context,
                new LicenseDialog.OnLicenseStatusChangeListener() {
                    @Override
                    public void onLicenseActivated() {
                        callback.onLicenseValid();
                    }

                    @Override
                    public void onLicenseExpired() {
                        callback.onLicenseDenied();
                    }
                });

        licenseDialog.setOnDismissListener(dialog -> {
            // Check if license is now valid
            OfflineLicenseManager licenseManager = OfflineLicenseManager.getInstance(context);
            if (!licenseManager.validateLicense()) {
                callback.onLicenseDenied();
            }
        });

        licenseDialog.show();
    }
}