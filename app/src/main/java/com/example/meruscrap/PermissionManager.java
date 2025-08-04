// Add this new class to handle permissions
package com.example.meruscrap;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermissionManager {

    private static final int PERMISSION_REQUEST_CODE = 1000;

    // Define all required permissions with version constraints
    public static class PermissionInfo {
        public String permission;
        public String friendlyName;
        public String description;
        public int minSdkVersion;
        public boolean isRequired;

        public PermissionInfo(String permission, String friendlyName, String description, int minSdkVersion, boolean isRequired) {
            this.permission = permission;
            this.friendlyName = friendlyName;
            this.description = description;
            this.minSdkVersion = minSdkVersion;
            this.isRequired = isRequired;
        }
    }

    // All app permissions with user-friendly descriptions
    private static final PermissionInfo[] ALL_PERMISSIONS = {
            new PermissionInfo(
                    Manifest.permission.BLUETOOTH,
                    "Bluetooth Access",
                    "Required to connect to Bluetooth printers and scales",
                    Build.VERSION_CODES.BASE,
                    true
            ),
            new PermissionInfo(
                    Manifest.permission.BLUETOOTH_ADMIN,
                    "Bluetooth Administration",
                    "Required to manage Bluetooth connections and scanning",
                    Build.VERSION_CODES.BASE,
                    true
            ),
            new PermissionInfo(
                    Manifest.permission.BLUETOOTH_SCAN,
                    "Bluetooth Scanning",
                    "Required to discover nearby Bluetooth devices (Android 12+)",
                    Build.VERSION_CODES.S,
                    true
            ),
            new PermissionInfo(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    "Bluetooth Connection",
                    "Required to connect to Bluetooth devices (Android 12+)",
                    Build.VERSION_CODES.S,
                    true
            ),
            new PermissionInfo(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    "Location Access",
                    "Required for Bluetooth device scanning and BLE scale connectivity",
                    Build.VERSION_CODES.BASE,
                    true
            ),
            new PermissionInfo(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    "Approximate Location",
                    "Required for Bluetooth device discovery",
                    Build.VERSION_CODES.BASE,
                    false // Fine location covers this
            )
    };

    public interface PermissionCallback {
        void onAllPermissionsGranted();
        void onPermissionsDenied(List<String> deniedPermissions);
        void onPermissionsPermanentlyDenied(List<String> permanentlyDeniedPermissions);
    }

    public static List<PermissionInfo> getRequiredPermissions() {
        List<PermissionInfo> required = new ArrayList<>();
        int currentSdk = Build.VERSION.SDK_INT;

        for (PermissionInfo permissionInfo : ALL_PERMISSIONS) {
            if (currentSdk >= permissionInfo.minSdkVersion && permissionInfo.isRequired) {
                required.add(permissionInfo);
            }
        }

        return required;
    }

    public static List<String> getMissingPermissions(Context context) {
        List<String> missing = new ArrayList<>();
        List<PermissionInfo> required = getRequiredPermissions();

        for (PermissionInfo permissionInfo : required) {
            if (ContextCompat.checkSelfPermission(context, permissionInfo.permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(permissionInfo.permission);
            }
        }

        return missing;
    }

    public static boolean hasAllRequiredPermissions(Context context) {
        return getMissingPermissions(context).isEmpty();
    }

    public static PermissionInfo getPermissionInfo(String permission) {
        for (PermissionInfo info : ALL_PERMISSIONS) {
            if (info.permission.equals(permission)) {
                return info;
            }
        }
        return null;
    }

    // REPLACE THIS METHOD IN YOUR PERMISSIONMANAGER.JAVA
    public static void showPermissionExplanationDialog(Context context,
                                                       List<String> permissions,
                                                       Runnable onProceed) {
        // Call the new method with null for onNotNow to maintain backward compatibility
        showPermissionExplanationDialog(context, permissions, onProceed, null);
    }
    // In PermissionManager.java, the showPermissionExplanationDialog method is already updated
// to support both onProceed and onNotNow callbacks. Here's the enhanced version if needed:

    public static void showPermissionExplanationDialog(Context context,
                                                       List<String> permissions,
                                                       Runnable onProceed,
                                                       Runnable onNotNow) {
        if (context == null || permissions == null || permissions.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Permissions Required");
        builder.setIcon(android.R.drawable.ic_dialog_info);

        StringBuilder message = new StringBuilder();
        message.append("MeruScrap needs these permissions to function properly:\n\n");

        for (String permission : permissions) {
            PermissionInfo info = getPermissionInfo(permission);
            if (info != null) {
                message.append("🔸 ").append(info.friendlyName).append("\n");
                message.append("   ").append(info.description).append("\n\n");
            }
        }

        message.append("These permissions allow the app to:\n");
        message.append("• Connect to Bluetooth printers for receipts\n");
        message.append("• Connect to BLE scales for accurate weighing\n");
        message.append("• Discover and manage device connections\n\n");

        if (onNotNow != null) {
            message.append("✅ You can grant these permissions now for full functionality\n");
            message.append("⚙️ Or continue with limited features and grant them later in Settings");
        } else {
            message.append("Your privacy is important - we only use these permissions for their intended functionality.");
        }

        builder.setMessage(message.toString());

        builder.setPositiveButton("Grant Permissions", (dialog, which) -> {
            if (onProceed != null) {
                onProceed.run();
            }
        });

        if (onNotNow != null) {
            builder.setNegativeButton("Not Now", (dialog, which) -> {
                onNotNow.run();
            });
        } else {
            builder.setNegativeButton("Not Now", null);
        }

        builder.setCancelable(false);
        builder.show();
    }
    // NEW: Detailed permission information dialog
    private static void showDetailedPermissionInfo(Context context, List<String> permissions) {
        StringBuilder detailedInfo = new StringBuilder();
        detailedInfo.append("🔍 Detailed Permission Information\n\n");

        for (String permission : permissions) {
            PermissionInfo info = getPermissionInfo(permission);
            if (info != null) {
                detailedInfo.append("🔸 ").append(info.friendlyName).append("\n");
                detailedInfo.append("Purpose: ").append(info.description).append("\n");
                detailedInfo.append("Required: ").append(info.isRequired ? "Yes" : "Optional").append("\n");
                detailedInfo.append("Android Version: ").append(info.minSdkVersion >= android.os.Build.VERSION_CODES.S ? "12+" : "All").append("\n\n");
            }
        }

        detailedInfo.append("📋 What happens without permissions:\n");
        detailedInfo.append("• Basic app functions will work normally\n");
        detailedInfo.append("• Printer connection will be disabled\n");
        detailedInfo.append("• BLE scale features will be unavailable\n");
        detailedInfo.append("• Device scanning will not work\n\n");
        detailedInfo.append("✅ All features work when permissions are granted in Settings.");

        new AlertDialog.Builder(context)
                .setTitle("Permission Details")
                .setMessage(detailedInfo.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("Grant Now", (dialog, which) -> {
                    // Return to permission request
                    showPermissionExplanationDialog(context, permissions, () -> {
                        if (context instanceof MainActivity) {
                            ((MainActivity) context).requestMissingPermissions();
                        }
                    });
                })
                .show();
    }

    public static void showPermissionDeniedDialog(Context context,
                                                  List<String> deniedPermissions,
                                                  boolean isPermanentlyDenied) {
        StringBuilder message = new StringBuilder();

        if (isPermanentlyDenied) {
            message.append("Some required permissions have been permanently denied:\n\n");

            for (String permission : deniedPermissions) {
                PermissionInfo info = getPermissionInfo(permission);
                if (info != null) {
                    message.append("• ").append(info.friendlyName).append("\n");
                }
            }

            message.append("\nTo use all app features, please:\n");
            message.append("1. Tap 'Open Settings'\n");
            message.append("2. Go to Permissions\n");
            message.append("3. Enable the required permissions\n");
            message.append("4. Return to the app");

            new AlertDialog.Builder(context)
                    .setTitle("Permissions Required")
                    .setMessage(message.toString())
                    .setPositiveButton("Open Settings", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + context.getPackageName()));
                        context.startActivity(intent);
                    })
                    .setNegativeButton("Continue with Limited Features", null)
                    .show();
        } else {
            message.append("Some permissions were denied. The app may not function properly without them.\n\n");
            message.append("Would you like to try again?");

            new AlertDialog.Builder(context)
                    .setTitle("Permissions Needed")
                    .setMessage(message.toString())
                    .setPositiveButton("Try Again", (dialog, which) -> {
                        // Trigger permission request again
                    })
                    .setNegativeButton("Continue", null)
                    .show();
        }
    }
    // Add these missing methods to PermissionManager.java

    public static void showPermissionRationale(Context context,
                                               List<String> permissions,
                                               Runnable onProceed) {
        if (context == null || permissions == null || permissions.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Bluetooth Permissions Required");
        builder.setIcon(android.R.drawable.ic_dialog_info);

        StringBuilder message = new StringBuilder();
        message.append("🔵 Bluetooth permissions are needed to:\n\n");

        for (String permission : permissions) {
            PermissionInfo info = getPermissionInfo(permission);
            if (info != null) {
                if (permission.contains("BLUETOOTH")) {
                    message.append("🖨️ Connect to thermal printers\n");
                    message.append("⚖️ Connect to BLE scales\n");
                    message.append("🔍 Discover nearby devices\n");
                } else if (permission.contains("LOCATION")) {
                    message.append("📍 Enable Bluetooth device scanning\n");
                    message.append("⚖️ Connect to BLE scales\n");
                }
            }
        }

        message.append("\n💡 These permissions are only used for device connectivity.\n");
        message.append("📍 We don't collect location data or track your movements.");

        builder.setMessage(message.toString());

        builder.setPositiveButton("Grant Permissions", (dialog, which) -> {
            if (onProceed != null) {
                onProceed.run();
            }
        });

        builder.setNegativeButton("Skip for Now", (dialog, which) -> {
            Toast.makeText(context, "Some features will be limited without permissions",
                    Toast.LENGTH_LONG).show();
        });

        builder.setCancelable(false);
        builder.show();
    }
}