package com.example.meruscrap;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class BluetoothPrinterAdapter extends RecyclerView.Adapter<BluetoothPrinterAdapter.PrinterViewHolder> {

    private List<BluetoothDevice> printerList;
    private OnPrinterSelectedListener listener;
    private Context context;

    public interface OnPrinterSelectedListener {
        void onPrinterSelected(BluetoothDevice device);
    }

    public BluetoothPrinterAdapter(List<BluetoothDevice> printerList, OnPrinterSelectedListener listener) {
        this.printerList = printerList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PrinterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_bluetooth_printer, parent, false);
        return new PrinterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PrinterViewHolder holder, int position) {
        // FIX 1: Use printerList instead of devices, and correct holder type
        BluetoothDevice device = printerList.get(position);

        // FIX 2: Call the bind method properly
        holder.bind(device);
    }

    private String getDeviceNameSafely(BluetoothDevice device) {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                String name = device.getName();
                return name != null ? name : "Unknown Device";
            }
        } catch (SecurityException e) {
            android.util.Log.w("BluetoothPrinterAdapter", "SecurityException while getting device name", e);
        }
        return "Unknown Device";
    }

    @Override
    public int getItemCount() {
        return printerList.size();
    }

    class PrinterViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView cardPrinter;
        private ImageView ivPrinterIcon;
        private TextView tvPrinterName;
        private TextView tvPrinterAddress;
        private TextView tvPrinterStatus;

        public PrinterViewHolder(@NonNull View itemView) {
            super(itemView);
            cardPrinter = itemView.findViewById(R.id.card_printer);
            ivPrinterIcon = itemView.findViewById(R.id.iv_printer_icon);
            tvPrinterName = itemView.findViewById(R.id.tv_printer_name);
            tvPrinterAddress = itemView.findViewById(R.id.tv_printer_address);
            tvPrinterStatus = itemView.findViewById(R.id.tv_printer_status);
        }

        public void bind(BluetoothDevice device) {
            // FIX 3: Don't block the display due to permissions - show the device info anyway
            String deviceName = getDeviceNameSafely(device);

            tvPrinterName.setText(deviceName);

            // Safe access to device address (doesn't require additional permissions)
            try {
                String deviceAddress = device.getAddress();
                tvPrinterAddress.setText(deviceAddress != null ? deviceAddress : "Unknown Address");
            } catch (Exception e) {
                tvPrinterAddress.setText("Unknown Address");
            }

            // FIX 4: Use fallback icons if custom drawables don't exist
            try {
                if (deviceName.toLowerCase().contains("thermal")) {
                    ivPrinterIcon.setImageResource(R.drawable.ic_thermal_printer);
                } else if (deviceName.toLowerCase().contains("pos")) {
                    ivPrinterIcon.setImageResource(R.drawable.ic_pos_printer);
                } else {
                    ivPrinterIcon.setImageResource(R.drawable.ic_printer_generic);
                }
            } catch (Exception e) {
                // Fallback to system icon if custom drawables don't exist
                ivPrinterIcon.setImageResource(android.R.drawable.ic_menu_manage);
                android.util.Log.w("BluetoothPrinterAdapter", "Custom printer icons not found, using fallback");
            }

            // Set status based on bond state (with permission check)
            setBondStatus(device);

            // FIX 5: Always enable the card and set click listener
            cardPrinter.setEnabled(true);
            cardPrinter.setOnClickListener(v -> {
                // Add ripple effect
                cardPrinter.setPressed(true);
                cardPrinter.postDelayed(() -> {
                    cardPrinter.setPressed(false);
                    if (listener != null) {
                        listener.onPrinterSelected(device);
                    }
                }, 150);
            });
        }

        private void setStatusColor(int colorResId) {
            try {
                tvPrinterStatus.setTextColor(context.getResources().getColor(colorResId));
            } catch (Exception e) {
                // Fallback color if resource doesn't exist
                tvPrinterStatus.setTextColor(context.getResources().getColor(android.R.color.black));
            }
        }

        private void setBondStatus(BluetoothDevice device) {
            try {
                // Check if we have permission to get bond state
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {

                    switch (device.getBondState()) {
                        case BluetoothDevice.BOND_BONDED:
                            tvPrinterStatus.setText("Paired");
                            setStatusColor(R.color.success);
                            break;
                        case BluetoothDevice.BOND_BONDING:
                            tvPrinterStatus.setText("Pairing...");
                            setStatusColor(R.color.warning);
                            break;
                        default:
                            tvPrinterStatus.setText("Available");
                            setStatusColor(R.color.primary);
                            break;
                    }
                } else {
                    // No permission to check bond state
                    tvPrinterStatus.setText("Available");
                    setStatusColor(R.color.primary);
                }
            } catch (SecurityException e) {
                android.util.Log.w("BluetoothPrinterAdapter", "SecurityException getting bond state", e);
                tvPrinterStatus.setText("Available");
                setStatusColor(R.color.primary);
            } catch (Exception e) {
                android.util.Log.w("BluetoothPrinterAdapter", "Error getting bond state", e);
                tvPrinterStatus.setText("Unknown");
                setStatusColor(R.color.text_secondary);
            }
        }
    }
}