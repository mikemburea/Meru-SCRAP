// Create this new file: PermissionStatusDialog.java
package com.example.meruscrap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.util.List;

public class PermissionStatusDialog {

    public static void show(Context context) {
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // Create custom layout for permission status
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        // Title
        TextView titleView = new TextView(context);
        titleView.setText("Permission Status");
        titleView.setTextSize(20);
        titleView.setTextColor(ContextCompat.getColor(context, android.R.color.black));
        titleView.setPadding(0, 0, 0, 30);
        layout.addView(titleView);

        // Get all required permissions
        List<PermissionManager.PermissionInfo> requiredPermissions = PermissionManager.getRequiredPermissions();

        // Check each permission status
        for (PermissionManager.PermissionInfo permissionInfo : requiredPermissions) {
            boolean isGranted = ContextCompat.checkSelfPermission(context, permissionInfo.permission)
                    == PackageManager.PERMISSION_GRANTED;

            // Create permission item view
            LinearLayout permissionItem = new LinearLayout(context);
            permissionItem.setOrientation(LinearLayout.VERTICAL);
            permissionItem.setPadding(0, 10, 0, 20);

            // Permission name and status
            TextView permissionNameView = new TextView(context);
            String statusIcon = isGranted ? "✅" : "❌";
            String statusText = isGranted ? "Granted" : "Denied";
            permissionNameView.setText(statusIcon + " " + permissionInfo.friendlyName + " - " + statusText);
            permissionNameView.setTextSize(16);
            permissionNameView.setTextColor(ContextCompat.getColor(context,
                    isGranted ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));

            // Permission description
            TextView permissionDescView = new TextView(context);
            permissionDescView.setText(permissionInfo.description);
            permissionDescView.setTextSize(12);
            permissionDescView.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray));
            permissionDescView.setPadding(0, 5, 0, 0);

            permissionItem.addView(permissionNameView);
            permissionItem.addView(permissionDescView);
            layout.addView(permissionItem);
        }

        // Summary
        List<String> missingPermissions = PermissionManager.getMissingPermissions(context);
        TextView summaryView = new TextView(context);
        if (missingPermissions.isEmpty()) {
            summaryView.setText("\n🎉 All permissions are granted! Your app has full functionality.");
            summaryView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark));
        } else {
            summaryView.setText("\n⚠️ " + missingPermissions.size() + " permission(s) missing. Some features may be limited.");
            summaryView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark));
        }
        summaryView.setTextSize(14);
        summaryView.setPadding(0, 20, 0, 0);
        layout.addView(summaryView);

        builder.setView(layout);

        // Buttons
        if (!missingPermissions.isEmpty()) {
            builder.setPositiveButton("Open Settings", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
            });

            builder.setNeutralButton("Request Again", (dialog, which) -> {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).requestMissingPermissions();
                }
            });
        }

        builder.setNegativeButton("Close", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Show a simplified permission status as a simple message dialog
     */
    public static void showSimple(Context context) {
        if (context == null) return;

        List<String> missingPermissions = PermissionManager.getMissingPermissions(context);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Permission Status");

        if (missingPermissions.isEmpty()) {
            builder.setMessage("✅ All required permissions are granted.\n\nYour app has full functionality for:\n• Bluetooth printing\n• BLE scale connectivity\n• Device scanning");
            builder.setIcon(android.R.drawable.ic_dialog_info);
        } else {
            StringBuilder message = new StringBuilder();
            message.append("⚠️ Missing ").append(missingPermissions.size()).append(" permission(s):\n\n");

            for (String permission : missingPermissions) {
                PermissionManager.PermissionInfo info = PermissionManager.getPermissionInfo(permission);
                if (info != null) {
                    message.append("• ").append(info.friendlyName).append("\n");
                }
            }

            message.append("\nSome features may not work properly until all permissions are granted.");

            builder.setMessage(message.toString());
            builder.setIcon(android.R.drawable.ic_dialog_alert);

            builder.setPositiveButton("Grant Permissions", (dialog, which) -> {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).requestMissingPermissions();
                }
            });

            builder.setNeutralButton("Settings", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
            });
        }

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    /**
     * Show permission rationale dialog explaining why permissions are needed
     */
    public static void showRationale(Context context, List<String> permissions, Runnable onProceed) {
        if (context == null || permissions == null || permissions.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Permissions Required");
        builder.setIcon(android.R.drawable.ic_dialog_info);

        StringBuilder message = new StringBuilder();
        message.append("MeruScrap needs these permissions to function properly:\n\n");

        for (String permission : permissions) {
            PermissionManager.PermissionInfo info = PermissionManager.getPermissionInfo(permission);
            if (info != null) {
                message.append("🔸 ").append(info.friendlyName).append("\n");
                message.append("   ").append(info.description).append("\n\n");
            }
        }

        message.append("These permissions allow the app to:\n");
        message.append("• Connect to Bluetooth printers for receipts\n");
        message.append("• Connect to BLE scales for accurate weighing\n");
        message.append("• Discover and manage device connections\n\n");
        message.append("Your privacy is important - we only use these permissions for their intended functionality.");

        builder.setMessage(message.toString());

        builder.setPositiveButton("Grant Permissions", (dialog, which) -> {
            if (onProceed != null) {
                onProceed.run();
            }
        });

        builder.setNegativeButton("Not Now", null);
        builder.setCancelable(false);
        builder.show();
    }

    /**
     * Show a quick status check as a toast-like dialog
     */
    public static void showQuickStatus(Context context) {
        if (context == null) return;

        List<String> missingPermissions = PermissionManager.getMissingPermissions(context);

        String message;
        int icon;

        if (missingPermissions.isEmpty()) {
            message = "✅ All permissions granted";
            icon = android.R.drawable.ic_dialog_info;
        } else {
            message = "⚠️ " + missingPermissions.size() + " permission(s) missing";
            icon = android.R.drawable.ic_dialog_alert;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message);
        builder.setIcon(icon);
        builder.setPositiveButton("OK", null);

        if (!missingPermissions.isEmpty()) {
            builder.setNeutralButton("Fix", (dialog, which) -> {
                show(context); // Show full dialog
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        // Auto-dismiss after 3 seconds if all permissions granted
        if (missingPermissions.isEmpty()) {
            new android.os.Handler().postDelayed(() -> {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            }, 3000);
        }
    }
}