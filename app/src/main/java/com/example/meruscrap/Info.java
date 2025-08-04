package com.example.meruscrap;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

public class Info extends Fragment {

    // UI Elements
    private TextView tvAppVersion;
    private TextView tvBuildInfo;
    private MaterialButton btnContactEmail;
    private MaterialButton btnContactSupport;
    private MaterialButton btnPrivacyPolicy;
    private MaterialButton btnTermsOfService;
    private MaterialButton btnOpenSourceLicenses;

    // Constants
    private static final String SUPPORT_EMAIL = "mikemburea@gmail.com";
    private static final String DEVELOPER_EMAIL = "mikemburea@gmail.com";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_info, container, false);

        // Initialize views
        initializeViews(view);

        // Set up dynamic content
        setupDynamicContent();

        // Set up click listeners
        setupClickListeners();

        return view;
    }

    private void initializeViews(View view) {
        // Initialize views - with null checks for debugging
        tvAppVersion = view.findViewById(R.id.tv_app_version);
        tvBuildInfo = view.findViewById(R.id.tv_build_info);
        btnContactEmail = view.findViewById(R.id.btn_contact_email);
        btnContactSupport = view.findViewById(R.id.btn_contact_support);
        btnPrivacyPolicy = view.findViewById(R.id.btn_privacy_policy);
        btnTermsOfService = view.findViewById(R.id.btn_terms_of_service);
        btnOpenSourceLicenses = view.findViewById(R.id.btn_open_source_licenses);

        // Debug: Log which views are null
        if (tvAppVersion == null) android.util.Log.e("Info", "tv_app_version not found");
        if (tvBuildInfo == null) android.util.Log.e("Info", "tv_build_info not found");
        if (btnContactEmail == null) android.util.Log.e("Info", "btn_contact_email not found");
        if (btnContactSupport == null) android.util.Log.e("Info", "btn_contact_support not found");
        if (btnPrivacyPolicy == null) android.util.Log.e("Info", "btn_privacy_policy not found");
        if (btnTermsOfService == null) android.util.Log.e("Info", "btn_terms_of_service not found");
        if (btnOpenSourceLicenses == null) android.util.Log.e("Info", "btn_open_source_licenses not found");
    }

    private void setupDynamicContent() {
        // Set app version dynamically - with null check
        if (tvAppVersion != null) {
            try {
                String versionName = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                tvAppVersion.setText("Version " + versionName);
            } catch (Exception e) {
                tvAppVersion.setText("Version 1.0.0");
            }
        }

        // Set build information dynamically - with null check
        if (tvBuildInfo != null) {
            try {
                String versionName = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                long versionCode = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).getLongVersionCode();

                String buildInfo = "Version: " + versionName + "\n" +
                        "Build: " + versionCode + "\n" +
                        "Target SDK: " + requireContext().getApplicationInfo().targetSdkVersion;
                tvBuildInfo.setText(buildInfo);
            } catch (Exception e) {
                tvBuildInfo.setText("Version: 1.0.0\nBuild: 2025.01.27\nTarget SDK: 34");
            }
        }
    }

    private void setupClickListeners() {
        // Contact Email Button - with null check
        if (btnContactEmail != null) {
            btnContactEmail.setOnClickListener(v -> openEmailClient(DEVELOPER_EMAIL, "MeruScrap - General Inquiry"));
        }

        // Contact Support Button - with null check
        if (btnContactSupport != null) {
            btnContactSupport.setOnClickListener(v -> openEmailClient(SUPPORT_EMAIL, "MeruScrap - Support Request"));
        }

        // Privacy Policy Button - with null check
        if (btnPrivacyPolicy != null) {
            btnPrivacyPolicy.setOnClickListener(v -> {
                // You can replace this with a web URL or show a dialog
                showFeatureNotImplemented("Privacy Policy");
            });
        }

        // Terms of Service Button - with null check
        if (btnTermsOfService != null) {
            btnTermsOfService.setOnClickListener(v -> {
                // You can replace this with a web URL or show a dialog
                showFeatureNotImplemented("Terms of Service");
            });
        }

        // Open Source Licenses Button - with null check
        if (btnOpenSourceLicenses != null) {
            btnOpenSourceLicenses.setOnClickListener(v -> {
                // You can replace this with a dialog showing licenses
                showFeatureNotImplemented("Open Source Licenses");
            });
        }
    }

    private void openEmailClient(String email, String subject) {
        try {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:" + email));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT, "Hello Nesis Team,\n\n");

            if (emailIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(Intent.createChooser(emailIntent, "Send Email"));
            } else {
                // Fallback - copy email to clipboard and show toast
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Email", email);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), "Email copied to clipboard: " + email, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to open email client", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFeatureNotImplemented(String feature) {
        Toast.makeText(requireContext(), feature + " coming soon!", Toast.LENGTH_SHORT).show();
        // TODO: Implement actual dialogs or web views for these features
    }

    // Optional: Method to open web URLs (for future use)
    private void openWebUrl(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show();
        }
    }
}