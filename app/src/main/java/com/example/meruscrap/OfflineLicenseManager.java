package com.example.meruscrap;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class OfflineLicenseManager {
    private static final String TAG = "OfflineLicenseManager";
    private static final String PREFS_NAME = "meru_scrap_license";
    // ADD THIS SECRET (keep this secret and don't change it!)
    private static final String SECRET_VALIDATION_KEY = "MeruScrap2024$SecretValidation#Key!";
    // License configuration
    private static final String LICENSE_KEY = "MERU-SCRAP-2024-PREMIUM"; // Your unique license key
    private static final int TRIAL_DAYS = 30;
    private static final String EXPECTED_PACKAGE = "com.example.meruscrap";

    // License types for MeruScrap
    public enum LicenseType {
        TRIAL(30, "Trial License"),
        BASIC(365, "Basic License"),
        PREMIUM(-1, "Premium License"),
        ENTERPRISE(-1, "Enterprise License");

        private final int validityDays;
        private final String displayName;

        LicenseType(int days, String displayName) {
            this.validityDays = days;
            this.displayName = displayName;
        }

        public int getValidityDays() { return validityDays; }
        public String getDisplayName() { return displayName; }
        public boolean isUnlimited() { return validityDays == -1; }
    }

    private static OfflineLicenseManager instance;
    private Context context;
    private SharedPreferences prefs;

    private OfflineLicenseManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized OfflineLicenseManager getInstance(Context context) {
        if (instance == null) {
            instance = new OfflineLicenseManager(context);
        }
        return instance;
    }

    /**
     * Main license validation method
     */
    public boolean validateLicense() {
        try {
            // First run initialization
            if (!prefs.contains("first_run_date")) {
                initializeFirstRun();
            }

            // Check if we have a valid license
            LicenseType currentLicense = getCurrentLicenseType();

            switch (currentLicense) {
                case TRIAL:
                    return validateTrialLicense();
                case BASIC:
                case PREMIUM:
                case ENTERPRISE:
                    return validatePaidLicense(currentLicense);
                default:
                    return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error validating license", e);
            return false;
        }
    }

    private void initializeFirstRun() {
        long currentTime = System.currentTimeMillis();
        String deviceId = getDeviceFingerprint();

        prefs.edit()
                .putLong("first_run_date", currentTime)
                .putLong("install_date", currentTime)
                .putString("device_id", deviceId)
                .putString("license_type", LicenseType.TRIAL.name())
                .apply();

        Log.d(TAG, "First run initialized with trial license");
    }

    private boolean validateTrialLicense() {
        long installDate = prefs.getLong("install_date", 0);
        if (installDate == 0) return false;

        long currentTime = System.currentTimeMillis();
        long daysSinceInstall = TimeUnit.MILLISECONDS.toDays(currentTime - installDate);

        boolean isValid = daysSinceInstall <= TRIAL_DAYS;

        if (!isValid) {
            Log.w(TAG, "Trial license expired. Days since install: " + daysSinceInstall);
        }

        return isValid;
    }

    private boolean validatePaidLicense(LicenseType licenseType) {
        String storedLicenseKey = prefs.getString("license_key", "");

        // Check if key is revoked
        if (isKeyRevoked(storedLicenseKey)) {
            Log.w(TAG, "License key has been revoked");
            return false;
        }
        String storedActivationCode = prefs.getString("activation_code", "");

        if (storedLicenseKey.isEmpty() || storedActivationCode.isEmpty()) {
            Log.w(TAG, "No license key or activation code found");
            return false;
        }

        // Validate the activation code against device and license
        String expectedActivationCode = generateActivationCode(storedLicenseKey, getDeviceFingerprint());

        if (!storedActivationCode.equals(expectedActivationCode)) {
            Log.w(TAG, "Invalid activation code");
            return false;
        }

        // Check license expiry for non-unlimited licenses
        if (!licenseType.isUnlimited()) {
            long activationDate = prefs.getLong("activation_date", 0);
            if (activationDate == 0) return false;

            long currentTime = System.currentTimeMillis();
            long daysSinceActivation = TimeUnit.MILLISECONDS.toDays(currentTime - activationDate);

            if (daysSinceActivation > licenseType.getValidityDays()) {
                Log.w(TAG, "License expired. Days since activation: " + daysSinceActivation);
                return false;
            }
        }

        return true;
    }

    /**
     * Activate a license with provided license key
     */
    public boolean activateLicense(String licenseKey, LicenseType licenseType) {
        try {
            // Validate license key format
            if (!isValidLicenseKeyFormat(licenseKey)) {
                Log.w(TAG, "Invalid license key format");
                return false;
            }

            // Generate activation code
            String deviceFingerprint = getDeviceFingerprint();
            String activationCode = generateActivationCode(licenseKey, deviceFingerprint);

            // Store license information
            long currentTime = System.currentTimeMillis();

            prefs.edit()
                    .putString("license_key", licenseKey)
                    .putString("license_type", licenseType.name())
                    .putString("activation_code", activationCode)
                    .putLong("activation_date", currentTime)
                    .apply();

            Log.d(TAG, "License activated successfully: " + licenseType.getDisplayName());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error activating license", e);
            return false;
        }
    }

    /**
     * Deactivate current license and revert to trial mode
     */
    public boolean deactivateLicense() {
        try {
            prefs.edit()
                    .remove("license_key")
                    .remove("activation_code")
                    .remove("activation_date")
                    .putString("license_type", LicenseType.TRIAL.name())
                    .apply();

            Log.d(TAG, "License deactivated - reverted to trial mode");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error deactivating license", e);
            return false;
        }
    }

    /**
     * Get the stored license key (masked for security)
     */
    public String getLicenseKey() {
        String licenseKey = prefs.getString("license_key", "");
        if (licenseKey.isEmpty()) {
            return "No license key";
        }
        // Return masked version for security
        if (licenseKey.length() > 15) {
            return licenseKey.substring(0, 15) + "...";
        }
        return licenseKey;
    }

    /**
     * Get the full license key (use with caution)
     */
    public String getFullLicenseKey() {
        return prefs.getString("license_key", "");
    }

    /**
     * Get activation date as formatted string
     */
    public String getActivationDate() {
        long activationDate = prefs.getLong("activation_date", 0);
        if (activationDate == 0) {
            return "Not activated";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(activationDate));
    }

    /**
     * Get days since activation
     */
    public int getDaysSinceActivation() {
        long activationDate = prefs.getLong("activation_date", 0);
        if (activationDate == 0) {
            // For trial, use install date
            long installDate = prefs.getLong("install_date", 0);
            if (installDate == 0) return 0;

            long daysSinceInstall = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - installDate);
            return (int) daysSinceInstall;
        }

        long daysSinceActivation = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - activationDate);
        return (int) daysSinceActivation;
    }

    /**
     * Get install date as formatted string
     */
    public String getInstallDate() {
        long installDate = prefs.getLong("install_date", 0);
        if (installDate == 0) {
            return "Unknown";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(installDate));
    }

    /**
     * Get device fingerprint for current device
     */
    public String getCurrentDeviceFingerprint() {
        return getDeviceFingerprint();
    }

    /**
     * Check if license is transferable to another device
     */
    public boolean isLicenseTransferable() {
        LicenseType currentType = getCurrentLicenseType();
        return currentType == LicenseType.PREMIUM || currentType == LicenseType.ENTERPRISE;
    }

    /**
     * Get detailed license status
     */
    public String getLicenseStatus() {
        if (!validateLicense()) {
            if (isTrialExpired()) {
                return "❌ Trial Expired";
            } else {
                return "❌ Invalid License";
            }
        }

        LicenseType currentType = getCurrentLicenseType();
        if (currentType == LicenseType.TRIAL) {
            int daysRemaining = getDaysRemaining();
            return "⏰ Trial Active (" + daysRemaining + " days left)";
        } else {
            if (currentType.isUnlimited()) {
                return "✅ Licensed (Lifetime)";
            } else {
                int daysRemaining = getDaysRemaining();
                return "✅ Licensed (" + daysRemaining + " days left)";
            }
        }
    }

    /**
     * REPLACE your existing isValidLicenseKeyFormat method with this one
     */
    private boolean isValidLicenseKeyFormat(String licenseKey) {
        // First check basic format
        if (licenseKey == null || !licenseKey.matches("MERU-SCRAP-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}")) {
            return false;
        }

        // Now verify it's actually generated by your system
        return isKeyGeneratedByYourSystem(licenseKey);
    }
    /**
     * Verify if the license key was actually generated by your key generator
     */
    private boolean isKeyGeneratedByYourSystem(String licenseKey) {
        try {
            // Extract the segments
            String[] parts = licenseKey.split("-");
            if (parts.length != 5) return false;

            String segment1 = parts[2]; // First 4 chars
            String segment2 = parts[3]; // Second 4 chars
            String segment3 = parts[4]; // Third 4 chars

            // Create validation hash using the same secret
            String dataToHash = segment1 + segment2 + segment3 + SECRET_VALIDATION_KEY;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(dataToHash.getBytes("UTF-8"));

            // Convert to hex and take first 8 characters
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if(hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            // Check if this hash matches a pattern only your generator would know
            String expectedPattern = generateValidationPattern(hexString.toString());
            String actualPattern = generateValidationPattern(segment1 + segment2 + segment3);

            return expectedPattern.equals(actualPattern);

        } catch (Exception e) {
            Log.e(TAG, "Error validating license key authenticity", e);
            return false;
        }
    }
    /**
     * Generate a validation pattern that only keys from your generator will have
     */
    private String generateValidationPattern(String input) {
        try {
            String combined = input + SECRET_VALIDATION_KEY + "MERU_VALIDATION";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes("UTF-8"));

            // Use specific bytes to create a checksum
            int checksum = (hash[0] + hash[1] + hash[2] + hash[3]) & 0xFF;
            return String.format("%02X", checksum);

        } catch (Exception e) {
            return "00";
        }
    }
    /**
     * ADD THIS: Method to validate against a list of revoked keys
     */
    private boolean isKeyRevoked(String licenseKey) {
        // You can maintain a list of revoked keys
        String[] revokedKeys = {
                // Add any keys you want to revoke
                "MERU-SCRAP-TEST-BASI-C365", // Example: revoke test keys
                "MERU-SCRAP-TEST-PREM-IUM0",
                "MERU-SCRAP-TEST-ENTR-PRIS"
        };

        for (String revokedKey : revokedKeys) {
            if (revokedKey.equals(licenseKey)) {
                Log.w(TAG, "License key is revoked: " + licenseKey);
                return true;
            }
        }
        return false;
    }

    private String generateActivationCode(String licenseKey, String deviceFingerprint) {
        try {
            String combined = licenseKey + deviceFingerprint + EXPECTED_PACKAGE;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes());

            // Convert to readable format
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) { // Use first 8 bytes
                sb.append(String.format("%02X", hash[i] & 0xFF));
            }
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Error generating activation code", e);
            return "";
        }
    }

    private String getDeviceFingerprint() {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            String packageName = context.getPackageName();

            // Create a unique but consistent fingerprint
            String combined = androidId + packageName + Build.MODEL + Build.MANUFACTURER;

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes());

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) { // Use first 4 bytes
                sb.append(String.format("%02X", hash[i] & 0xFF));
            }
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "Error generating device fingerprint", e);
            return "DEFAULT";
        }
    }

    public LicenseType getCurrentLicenseType() {
        String licenseTypeStr = prefs.getString("license_type", LicenseType.TRIAL.name());
        try {
            return LicenseType.valueOf(licenseTypeStr);
        } catch (IllegalArgumentException e) {
            return LicenseType.TRIAL;
        }
    }

    public String getLicenseInfo() {
        LicenseType licenseType = getCurrentLicenseType();

        if (licenseType == LicenseType.TRIAL) {
            long installDate = prefs.getLong("install_date", 0);
            if (installDate > 0) {
                long daysSinceInstall = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - installDate);
                long daysRemaining = TRIAL_DAYS - daysSinceInstall;
                return "Trial License - " + Math.max(0, daysRemaining) + " days remaining";
            }
            return "Trial License";
        } else {
            String licenseKey = prefs.getString("license_key", "");
            long activationDate = prefs.getLong("activation_date", 0);

            String info = licenseType.getDisplayName();
            if (!licenseKey.isEmpty()) {
                info += " - " + licenseKey.substring(0, 15) + "...";
            }
            if (activationDate > 0) {
                String dateStr = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(new Date(activationDate));
                info += " (Activated: " + dateStr + ")";
            }

            return info;
        }
    }

    public int getDaysRemaining() {
        LicenseType licenseType = getCurrentLicenseType();

        if (licenseType.isUnlimited()) {
            return -1; // Unlimited
        }

        long referenceDate;
        int totalDays;

        if (licenseType == LicenseType.TRIAL) {
            referenceDate = prefs.getLong("install_date", 0);
            totalDays = TRIAL_DAYS;
        } else {
            referenceDate = prefs.getLong("activation_date", 0);
            totalDays = licenseType.getValidityDays();
        }

        if (referenceDate == 0) return 0;

        long daysPassed = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - referenceDate);
        return Math.max(0, totalDays - (int)daysPassed);
    }

    public boolean isTrialExpired() {
        return getCurrentLicenseType() == LicenseType.TRIAL && getDaysRemaining() <= 0;
    }

    public boolean isLicenseValid() {
        return validateLicense();
    }
}