// LicenseStorage.java
package com.example.meruscrap;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public class LicenseStorage {
    private static final String TAG = "LicenseStorage";
    private static final String LICENSE_FILE = "licenses.dat";
    private static final String USED_KEYS_FILE = "used_keys.dat";

    // Predefined license keys - these are your 20-30 keys
    private static final Map<String, LicenseInfo> PREDEFINED_LICENSES = new HashMap<>();

    static {
        // Premium Keys
        PREDEFINED_LICENSES.put("MERU-PREM-2024-0001", new LicenseInfo("PREMIUM", false));
        PREDEFINED_LICENSES.put("MERU-PREM-2024-0002", new LicenseInfo("PREMIUM", false));
        PREDEFINED_LICENSES.put("MERU-PREM-2024-0003", new LicenseInfo("PREMIUM", false));
        PREDEFINED_LICENSES.put("MERU-PREM-2024-0004", new LicenseInfo("PREMIUM", false));
        PREDEFINED_LICENSES.put("MERU-PREM-2024-0005", new LicenseInfo("PREMIUM", false));

        // Basic Keys
        PREDEFINED_LICENSES.put("MERU-BASIC-2024-0001", new LicenseInfo("BASIC", false));
        PREDEFINED_LICENSES.put("MERU-BASIC-2024-0002", new LicenseInfo("BASIC", false));
        PREDEFINED_LICENSES.put("MERU-BASIC-2024-0003", new LicenseInfo("BASIC", false));
        PREDEFINED_LICENSES.put("MERU-BASIC-2024-0004", new LicenseInfo("BASIC", false));
        PREDEFINED_LICENSES.put("MERU-BASIC-2024-0005", new LicenseInfo("BASIC", false));

        // Enterprise Keys
        PREDEFINED_LICENSES.put("MERU-ENTR-2024-0001", new LicenseInfo("ENTERPRISE", false));
        PREDEFINED_LICENSES.put("MERU-ENTR-2024-0002", new LicenseInfo("ENTERPRISE", false));
        PREDEFINED_LICENSES.put("MERU-ENTR-2024-0003", new LicenseInfo("ENTERPRISE", false));

        // Test Keys
        PREDEFINED_LICENSES.put("MERU-TEST-2024-DEMO", new LicenseInfo("PREMIUM", false));
    }

    static class LicenseInfo {
        String type;
        boolean used;
        String usedBy; // Device fingerprint that used it
        long usedDate;

        LicenseInfo(String type, boolean used) {
            this.type = type;
            this.used = used;
        }
    }

    private final Context context;
    private Map<String, LicenseInfo> runtimeLicenses;

    public LicenseStorage(Context context) {
        this.context = context.getApplicationContext();
        loadRuntimeLicenses();
    }

    // Load the current state of licenses
    private void loadRuntimeLicenses() {
        runtimeLicenses = new HashMap<>(PREDEFINED_LICENSES);

        // Load used keys from multiple sources
        Set<String> usedKeys = new HashSet<>();

        // 1. Load from internal file
        usedKeys.addAll(loadUsedKeysFromFile());

        // 2. Load from SharedPreferences (backup)
        usedKeys.addAll(loadUsedKeysFromPrefs());

        // 3. Load from hidden file in external storage (if permitted)
        usedKeys.addAll(loadUsedKeysFromExternal());

        // Mark all used keys
        for (String key : usedKeys) {
            if (runtimeLicenses.containsKey(key)) {
                runtimeLicenses.get(key).used = true;
            }
        }

        Log.d(TAG, "Loaded licenses. Used keys: " + usedKeys.size());
    }

    // Check if a license key is valid and available
    public boolean isLicenseAvailable(String licenseKey) {
        LicenseInfo info = runtimeLicenses.get(licenseKey);
        return info != null && !info.used;
    }

    // Use a license key
    public boolean useLicense(String licenseKey, String deviceFingerprint) {
        LicenseInfo info = runtimeLicenses.get(licenseKey);

        if (info == null) {
            Log.w(TAG, "License key not found: " + licenseKey);
            return false;
        }

        if (info.used) {
            Log.w(TAG, "License already used: " + licenseKey);
            return false;
        }

        // Mark as used
        info.used = true;
        info.usedBy = deviceFingerprint;
        info.usedDate = System.currentTimeMillis();

        // Save to multiple locations for persistence
        saveUsedKey(licenseKey, deviceFingerprint);

        Log.d(TAG, "License used successfully: " + licenseKey);
        return true;
    }

    // Save used key to multiple locations
    private void saveUsedKey(String licenseKey, String deviceFingerprint) {
        String record = licenseKey + "|" + deviceFingerprint + "|" + System.currentTimeMillis();

        // 1. Save to internal file
        saveToInternalFile(record);

        // 2. Save to SharedPreferences
        saveToPreferences(licenseKey);

        // 3. Save to a hidden system file (multiple locations)
        saveToMultipleLocations(record);

        // 4. Create a marker file with hash
        createMarkerFile(licenseKey, deviceFingerprint);
    }

    private void saveToInternalFile(String record) {
        try {
            File file = new File(context.getFilesDir(), USED_KEYS_FILE);
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(record + "\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving to internal file", e);
        }
    }

    private void saveToPreferences(String licenseKey) {
        try {
            Set<String> usedKeys = context.getSharedPreferences("license_data", Context.MODE_PRIVATE)
                    .getStringSet("used_keys", new HashSet<>());
            Set<String> newSet = new HashSet<>(usedKeys);
            newSet.add(licenseKey);

            context.getSharedPreferences("license_data", Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet("used_keys", newSet)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving to preferences", e);
        }
    }

    private void saveToMultipleLocations(String record) {
        // Save to multiple hidden locations to make it harder to clear
        String[] locations = {
                ".meruscrap_lic",
                ".android/data/.ms_lic",
                ".config/.ms_keys"
        };

        for (String location : locations) {
            try {
                File dir = new File(context.getFilesDir().getParentFile(), location);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                File file = new File(dir, "keys.dat");
                try (FileWriter writer = new FileWriter(file, true)) {
                    writer.write(record + "\n");
                }
            } catch (Exception e) {
                // Silently fail - some locations might not be writable
            }
        }
    }

    private void createMarkerFile(String licenseKey, String deviceFingerprint) {
        try {
            // Create a hash of the license + device
            String data = licenseKey + deviceFingerprint + "MeruScrap2024";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes());

            // Save hash as a marker
            String hashHex = bytesToHex(hash);
            File markerFile = new File(context.getFilesDir(), ".marker_" + hashHex.substring(0, 16));
            markerFile.createNewFile();

        } catch (Exception e) {
            Log.e(TAG, "Error creating marker file", e);
        }
    }

    private Set<String> loadUsedKeysFromFile() {
        Set<String> usedKeys = new HashSet<>();

        try {
            File file = new File(context.getFilesDir(), USED_KEYS_FILE);
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("\\|");
                        if (parts.length > 0) {
                            usedKeys.add(parts[0]);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading from file", e);
        }

        return usedKeys;
    }

    private Set<String> loadUsedKeysFromPrefs() {
        try {
            return context.getSharedPreferences("license_data", Context.MODE_PRIVATE)
                    .getStringSet("used_keys", new HashSet<>());
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    private Set<String> loadUsedKeysFromExternal() {
        Set<String> usedKeys = new HashSet<>();

        // Check multiple hidden locations
        String[] locations = {
                ".meruscrap_lic/keys.dat",
                ".android/data/.ms_lic/keys.dat",
                ".config/.ms_keys/keys.dat"
        };

        for (String location : locations) {
            try {
                File file = new File(context.getFilesDir().getParentFile(), location);
                if (file.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] parts = line.split("\\|");
                            if (parts.length > 0) {
                                usedKeys.add(parts[0]);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Silently fail
            }
        }

        // Also check for marker files
        try {
            File filesDir = context.getFilesDir();
            File[] markerFiles = filesDir.listFiles((dir, name) -> name.startsWith(".marker_"));

            if (markerFiles != null) {
                for (File marker : markerFiles) {
                    // Marker exists = some license was used
                    Log.d(TAG, "Found marker file: " + marker.getName());
                }
            }
        } catch (Exception e) {
            // Silently fail
        }

        return usedKeys;
    }

    // Get license type
    public String getLicenseType(String licenseKey) {
        LicenseInfo info = runtimeLicenses.get(licenseKey);
        return info != null ? info.type : null;
    }

    // Get all available (unused) licenses
    public List<String> getAvailableLicenses() {
        List<String> available = new ArrayList<>();
        for (Map.Entry<String, LicenseInfo> entry : runtimeLicenses.entrySet()) {
            if (!entry.getValue().used) {
                available.add(entry.getKey());
            }
        }
        return available;
    }

    // Check if ANY license has been used (for detecting shared apps)
    public boolean hasAnyLicenseBeenUsed() {
        for (LicenseInfo info : runtimeLicenses.values()) {
            if (info.used) {
                return true;
            }
        }
        return false;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}