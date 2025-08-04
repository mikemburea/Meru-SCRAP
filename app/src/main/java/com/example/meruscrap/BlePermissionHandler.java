package com.example.meruscrap;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class BlePermissionHandler {
    private static final int BLE_PERMISSION_REQUEST_CODE = 1001;

    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied(String[] deniedPermissions);
        void onPermissionsExplanationNeeded(String[] permissions);
    }

    private Context context;
    private Fragment fragment;
    private Activity activity;
    private PermissionCallback callback;

    // Constructor for Fragment
    public BlePermissionHandler(Fragment fragment, PermissionCallback callback) {
        this.fragment = fragment;
        this.context = fragment.getContext();
        this.callback = callback;
    }

    // Constructor for Activity
    public BlePermissionHandler(Activity activity, PermissionCallback callback) {
        this.activity = activity;
        this.context = activity;
        this.callback = callback;
    }

    /**
     * Get required permissions based on Android version
     */
    public String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);

            // Optional: Only add if you need advertising
            // permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else {
            // Android 11 and below
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        return permissions.toArray(new String[0]);
    }

    /**
     * Check if all required permissions are granted
     */
    public boolean hasAllPermissions() {
        String[] requiredPermissions = getRequiredPermissions();

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    /**
     * Request all required permissions
     */
    public void requestPermissions() {
        String[] requiredPermissions = getRequiredPermissions();
        List<String> permissionsToRequest = new ArrayList<>();
        List<String> permissionsNeedingExplanation = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {

                permissionsToRequest.add(permission);

                // Check if we should show explanation
                boolean shouldShow = false;
                if (fragment != null) {
                    shouldShow = fragment.shouldShowRequestPermissionRationale(permission);
                } else if (activity != null) {
                    shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
                }

                if (shouldShow) {
                    permissionsNeedingExplanation.add(permission);
                }
            }
        }

        if (permissionsToRequest.isEmpty()) {
            // All permissions already granted
            callback.onPermissionsGranted();
            return;
        }

        if (!permissionsNeedingExplanation.isEmpty()) {
            // Show explanation first
            callback.onPermissionsExplanationNeeded(
                    permissionsNeedingExplanation.toArray(new String[0])
            );
            return;
        }

        // Request permissions directly
        requestPermissionsInternal(permissionsToRequest.toArray(new String[0]));
    }

    /**
     * Internal method to request permissions
     */
    private void requestPermissionsInternal(String[] permissions) {
        if (fragment != null) {
            fragment.requestPermissions(permissions, BLE_PERMISSION_REQUEST_CODE);
        } else if (activity != null) {
            ActivityCompat.requestPermissions(activity, permissions, BLE_PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Call this from your Fragment's or Activity's onRequestPermissionsResult
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != BLE_PERMISSION_REQUEST_CODE) {
            return;
        }

        List<String> deniedPermissions = new ArrayList<>();

        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permissions[i]);
            }
        }

        if (deniedPermissions.isEmpty()) {
            callback.onPermissionsGranted();
        } else {
            callback.onPermissionsDenied(deniedPermissions.toArray(new String[0]));
        }
    }

    /**
     * Get user-friendly permission names for display
     */
    public static String getPermissionDisplayName(String permission) {
        switch (permission) {
            case Manifest.permission.BLUETOOTH_SCAN:
                return "Bluetooth Scan";
            case Manifest.permission.BLUETOOTH_CONNECT:
                return "Bluetooth Connect";
            case Manifest.permission.BLUETOOTH_ADVERTISE:
                return "Bluetooth Advertise";
            case Manifest.permission.ACCESS_FINE_LOCATION:
                return "Location Access";
            case Manifest.permission.BLUETOOTH:
                return "Bluetooth";
            case Manifest.permission.BLUETOOTH_ADMIN:
                return "Bluetooth Admin";
            default:
                return permission;
        }
    }

    /**
     * Get permission explanation text
     */
    public static String getPermissionExplanation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return "This app needs Bluetooth permissions to scan for and connect to scales. " +
                    "Your location data is not collected.";
        } else {
            return "This app needs Bluetooth and Location permissions to scan for nearby scales. " +
                    "Location is required by Android for Bluetooth scanning but your location data is not collected.";
        }
    }
}