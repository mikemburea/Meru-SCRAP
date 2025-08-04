package com.example.meruscrap;

import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * Adapter for showing ALL Bluetooth devices (not filtered for printers only)
 * Used in debug mode to help identify devices that might be printers
 */
public class BluetoothAllDevicesAdapter extends RecyclerView.Adapter<BluetoothAllDevicesAdapter.DeviceViewHolder> {

    private List<BluetoothDevice> devices;
    private OnDeviceClickListener clickListener;

    public interface OnDeviceClickListener {
        void onDeviceClick(BluetoothDevice device);
    }

    public BluetoothAllDevicesAdapter(List<BluetoothDevice> devices, OnDeviceClickListener clickListener) {
        this.devices = devices;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bluetooth_device_all, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        holder.bind(device, clickListener);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        private TextView deviceName;
        private TextView deviceAddress;
        private TextView deviceType;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.tv_device_name);
            deviceAddress = itemView.findViewById(R.id.tv_device_address);
            deviceType = itemView.findViewById(R.id.tv_device_type);
        }

        public void bind(BluetoothDevice device, OnDeviceClickListener clickListener) {
            try {
                // Get device name using multiple fallback methods
                String name = getDeviceNameRobust(device);

                deviceName.setText(name);
                deviceAddress.setText(device.getAddress());

                // Try to determine device type with debug info
                String typeInfo = getDeviceTypeInfo(device) + " [DEBUG MODE]";
                deviceType.setText(typeInfo);

                // Set click listener
                itemView.setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onDeviceClick(device);
                    }
                });

            } catch (Exception e) {
                android.util.Log.e("BluetoothAllDevicesAdapter", "Error binding device", e);
                deviceName.setText("Error Loading Name");
                deviceAddress.setText(device.getAddress());
                deviceType.setText("Unknown [DEBUG MODE]");
            }
        }

        /**
         * Robust method to get device name using multiple fallbacks
         */
        private String getDeviceNameRobust(BluetoothDevice device) {
            String name = null;

            try {
                // Method 1: Try to get the name directly (requires BLUETOOTH_CONNECT permission)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // Android 12+ - check BLUETOOTH_CONNECT permission
                    if (androidx.core.app.ActivityCompat.checkSelfPermission(itemView.getContext(),
                            android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        name = device.getName();
                        android.util.Log.d("BluetoothAllDevicesAdapter", "Got name via getName(): " + name);
                    }
                } else {
                    // Android 11 and below - check BLUETOOTH permission
                    if (androidx.core.app.ActivityCompat.checkSelfPermission(itemView.getContext(),
                            android.Manifest.permission.BLUETOOTH) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        name = device.getName();
                        android.util.Log.d("BluetoothAllDevicesAdapter", "Got name via getName(): " + name);
                    }
                }

                // Method 2: If name is still null, try using reflection to get cached name
                if (name == null || name.trim().isEmpty()) {
                    try {
                        java.lang.reflect.Method getAliasMethod = BluetoothDevice.class.getDeclaredMethod("getAlias");
                        name = (String) getAliasMethod.invoke(device);
                        android.util.Log.d("BluetoothAllDevicesAdapter", "Got name via getAlias(): " + name);
                    } catch (Exception e) {
                        android.util.Log.w("BluetoothAllDevicesAdapter", "getAlias() failed", e);
                    }
                }

                // Method 3: Try to get remote name (this sometimes works when getName() fails)
                if (name == null || name.trim().isEmpty()) {
                    try {
                        // This is a hidden method but sometimes available
                        java.lang.reflect.Method getRemoteNameMethod = BluetoothDevice.class.getDeclaredMethod("getRemoteName");
                        name = (String) getRemoteNameMethod.invoke(device);
                        android.util.Log.d("BluetoothAllDevicesAdapter", "Got name via getRemoteName(): " + name);
                    } catch (Exception e) {
                        android.util.Log.w("BluetoothAllDevicesAdapter", "getRemoteName() failed", e);
                    }
                }

            } catch (SecurityException e) {
                android.util.Log.w("BluetoothAllDevicesAdapter", "Permission denied when getting device name", e);
                return "Permission Required";
            }

            // Fallback logic
            if (name == null || name.trim().isEmpty()) {
                // Check if this looks like a known device type by MAC address pattern
                String macAddress = device.getAddress();
                String deviceType = guessDeviceTypeByMac(macAddress);
                return "Unknown Device (" + deviceType + ")";
            }

            return name.trim();
        }

        /**
         * Try to guess device type based on MAC address patterns
         */
        private String guessDeviceTypeByMac(String macAddress) {
            if (macAddress == null) return "Unknown";

            String mac = macAddress.toUpperCase();

            // Some known MAC prefixes for printer manufacturers
            if (mac.startsWith("00:07:61") || mac.startsWith("00:22:15")) return "Possible Epson";
            if (mac.startsWith("00:80:92") || mac.startsWith("00:80:77")) return "Possible Star";
            if (mac.startsWith("00:12:F0") || mac.startsWith("00:15:99")) return "Possible Citizen";
            if (mac.startsWith("08:00:11") || mac.startsWith("00:0D:F5")) return "Possible Zebra";
            if (mac.startsWith("00:04:3E") || mac.startsWith("00:07:4D")) return "Possible Bixolon";

            // Generic patterns
            if (mac.contains("00:00:") || mac.contains("FF:FF:")) return "Generic";

            return "Hardware";
        }

        private String getDeviceTypeInfo(BluetoothDevice device) {
            try {
                // Get device class info if available
                if (device.getBluetoothClass() != null) {
                    int majorClass = device.getBluetoothClass().getMajorDeviceClass();
                    return "Class: " + majorClass + " | Type: " + getDeviceTypeName(majorClass);
                }
            } catch (Exception e) {
                android.util.Log.w("BluetoothAllDevicesAdapter", "Error getting device class", e);
            }
            return "Type: Unknown";
        }

        private String getDeviceTypeName(int majorClass) {
            switch (majorClass) {
                case 0x0200: return "Phone";
                case 0x0100: return "Computer";
                case 0x0500: return "Audio/Video";
                case 0x0600: return "Peripheral";
                case 0x0700: return "Imaging";
                case 0x0800: return "Wearable";
                case 0x0900: return "Toy";
                case 0x0A00: return "Health";
                case 0x1F00: return "Uncategorized";
                default: return "Other (" + Integer.toHexString(majorClass) + ")";
            }
        }
    }
}