package com.example.meruscrap;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meruscrap.R;
import com.example.meruscrap.OfflineLicenseManager;

public class LicenseDialog extends Dialog {
    private OfflineLicenseManager licenseManager;
    private OnLicenseStatusChangeListener listener;

    public interface OnLicenseStatusChangeListener {
        void onLicenseActivated();
        void onLicenseExpired();
    }

    public LicenseDialog(Context context, OnLicenseStatusChangeListener listener) {
        super(context);
        this.licenseManager = OfflineLicenseManager.getInstance(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_license);
        setTitle("MeruScrap License");

        setupViews();
    }

    private void setupViews() {
        TextView tvLicenseInfo = findViewById(R.id.tv_license_info);
        TextView tvDaysRemaining = findViewById(R.id.tv_days_remaining);
        EditText etLicenseKey = findViewById(R.id.et_license_key);
        Button btnActivate = findViewById(R.id.btn_activate);
        Button btnPurchase = findViewById(R.id.btn_purchase);
        Button btnClose = findViewById(R.id.btn_close);

        // Display current license info
        tvLicenseInfo.setText(licenseManager.getLicenseInfo());

        int daysRemaining = licenseManager.getDaysRemaining();
        if (daysRemaining == -1) {
            tvDaysRemaining.setText("Unlimited License");
            tvDaysRemaining.setTextColor(getContext().getColor(R.color.success));
        } else if (daysRemaining > 7) {
            tvDaysRemaining.setText(daysRemaining + " days remaining");
            tvDaysRemaining.setTextColor(getContext().getColor(R.color.success));
        } else if (daysRemaining > 0) {
            tvDaysRemaining.setText(daysRemaining + " days remaining");
            tvDaysRemaining.setTextColor(getContext().getColor(R.color.warning));
        } else {
            tvDaysRemaining.setText("License Expired");
            tvDaysRemaining.setTextColor(getContext().getColor(R.color.error));
        }

        btnActivate.setOnClickListener(v -> {
            String licenseKey = etLicenseKey.getText().toString().trim();
            if (licenseKey.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a license key", Toast.LENGTH_SHORT).show();
                return;
            }

            // For simplicity, assuming PREMIUM license type
            // In a real app, you might determine this from the license key
            boolean success = licenseManager.activateLicense(licenseKey,
                    OfflineLicenseManager.LicenseType.PREMIUM);

            if (success) {
                Toast.makeText(getContext(), "License activated successfully!", Toast.LENGTH_LONG).show();
                if (listener != null) {
                    listener.onLicenseActivated();
                }
                dismiss();
            } else {
                Toast.makeText(getContext(), "Invalid license key", Toast.LENGTH_LONG).show();
            }
        });

        btnPurchase.setOnClickListener(v -> {
            // Handle purchase - could open a web browser, email, etc.
            Toast.makeText(getContext(), "Contact: meruscrap@gmail.com for license purchase",
                    Toast.LENGTH_LONG).show();
        });

        btnClose.setOnClickListener(v -> dismiss());
    }
}