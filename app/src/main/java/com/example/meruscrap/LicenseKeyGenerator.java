package com.example.meruscrap;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * License Key Generator for MeruScrap
 * This class should be kept SECURE and not included in your distributed APK
 * Use this only on your development machine to generate keys for customers
 */
public class LicenseKeyGenerator {

    private static final String PREFIX = "MERU-SCRAP";
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom random = new SecureRandom();

    /**
     * Generate a single license key
     */
    public static String generateLicenseKey() {
        StringBuilder key = new StringBuilder(PREFIX);

        // Generate 3 groups of 4 characters
        for (int group = 0; group < 3; group++) {
            key.append("-");
            for (int i = 0; i < 4; i++) {
                int index = random.nextInt(CHARACTERS.length());
                key.append(CHARACTERS.charAt(index));
            }
        }

        return key.toString();
    }

    /**
     * Generate multiple license keys
     */
    public static List<String> generateMultipleLicenseKeys(int count) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(generateLicenseKey());
        }
        return keys;
    }

    /**
     * Generate a license key with customer info embedded (for tracking)
     */
    public static String generateTrackedLicenseKey(String customerEmail) {
        String baseKey = generateLicenseKey();

        // Create a hash of customer email for tracking
        String trackingCode = generateTrackingCode(customerEmail);

        // Store this mapping in your secure database
        System.out.println("License Key: " + baseKey);
        System.out.println("Customer: " + customerEmail);
        System.out.println("Tracking Code: " + trackingCode);

        return baseKey;
    }

    /**
     * Generate tracking code from customer info
     */
    private static String generateTrackingCode(String customerInfo) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(customerInfo.getBytes());

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02X", hash[i] & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /**
     * Validate license key format (basic check)
     */
    public static boolean isValidFormat(String licenseKey) {
        if (licenseKey == null) return false;
        return licenseKey.matches("MERU-SCRAP-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}");
    }

    /**
     * Main method for testing and generating keys
     */
    public static void main(String[] args) {
        System.out.println("=== MeruScrap License Key Generator ===\n");

        // Generate sample keys for different license types
        System.out.println("TRIAL KEYS (30 days):");
        System.out.println("Note: Trial is automatic on first install\n");

        System.out.println("BASIC LICENSE KEYS (365 days):");
        for (int i = 0; i < 3; i++) {
            System.out.println((i+1) + ". " + generateLicenseKey());
        }

        System.out.println("\nPREMIUM LICENSE KEYS (Unlimited):");
        for (int i = 0; i < 5; i++) {
            System.out.println((i+1) + ". " + generateLicenseKey());
        }

        System.out.println("\nENTERPRISE LICENSE KEYS (Unlimited):");
        for (int i = 0; i < 2; i++) {
            System.out.println((i+1) + ". " + generateLicenseKey());
        }

        System.out.println("\n=== Sample Customer License ===");
        String customerKey = generateTrackedLicenseKey("customer@example.com");

        System.out.println("\n=== Validation Test ===");
        System.out.println("Valid format: " + isValidFormat(customerKey));
    }
}

/**
 * Secure License Database Manager
 * Keep this on your server/secure machine
 */
class LicenseDatabase {
    // In production, use a real database
    private static final List<LicenseRecord> licenses = new ArrayList<>();

    static class LicenseRecord {
        String licenseKey;
        String customerEmail;
        String customerName;
        String licenseType;
        String issueDate;
        String deviceFingerprint; // Will be set on first activation
        boolean isActivated;

        public LicenseRecord(String key, String email, String name, String type) {
            this.licenseKey = key;
            this.customerEmail = email;
            this.customerName = name;
            this.licenseType = type;
            this.issueDate = new java.util.Date().toString();
            this.isActivated = false;
        }
    }

    /**
     * Generate and save a new license
     */
    public static String generateAndSaveLicense(String customerEmail, String customerName,
                                                String licenseType) {
        String key = LicenseKeyGenerator.generateLicenseKey();
        LicenseRecord record = new LicenseRecord(key, customerEmail, customerName, licenseType);
        licenses.add(record);

        // In production, save to database
        System.out.println("\n=== New License Created ===");
        System.out.println("Key: " + key);
        System.out.println("Customer: " + customerName + " (" + customerEmail + ")");
        System.out.println("Type: " + licenseType);
        System.out.println("Issue Date: " + record.issueDate);

        return key;
    }

    /**
     * Export license list to CSV (for backup)
     */
    public static void exportLicenses() {
        System.out.println("\n=== License Export (CSV Format) ===");
        System.out.println("LicenseKey,CustomerEmail,CustomerName,Type,IssueDate,Activated");

        for (LicenseRecord record : licenses) {
            System.out.println(String.format("%s,%s,%s,%s,%s,%s",
                    record.licenseKey,
                    record.customerEmail,
                    record.customerName,
                    record.licenseType,
                    record.issueDate,
                    record.isActivated ? "Yes" : "No"
            ));
        }
    }
}