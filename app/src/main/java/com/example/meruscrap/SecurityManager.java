package com.example.meruscrap;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

public class SecurityManager {
    private static final String TAG = "SecurityManager";
    private static final String EXPECTED_SIGNATURE = "YOUR_RELEASE_SIGNATURE_HASH"; // Replace with actual hash
    private static SecurityManager instance;

    private Context context;
    private boolean isSecurityActive = false;

    private SecurityManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized SecurityManager getInstance(Context context) {
        if (instance == null) {
            instance = new SecurityManager(context);
        }
        return instance;
    }

    /**
     * Main security initialization - call this in your MainActivity onCreate()
     */
    public boolean initializeSecurity() {
        Log.d(TAG, "Initializing security checks for MeruScrap");

        // Level 1: Basic integrity checks
        if (!performBasicSecurityChecks()) {
            handleSecurityViolation("Basic security checks failed");
            return false;
        }

        // Level 2: License validation
        if (!validateOfflineLicense()) {
            handleSecurityViolation("License validation failed");
            return false;
        }

        // Level 3: Runtime protection
        startRuntimeProtection();

        isSecurityActive = true;
        Log.d(TAG, "Security initialization completed successfully");
        return true;
    }

    private boolean performBasicSecurityChecks() {
        return verifyAppSignature() &&
                !isAppTampered() &&
                !isRunningInUnsafeEnvironment();
    }

    private boolean verifyAppSignature() {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);

            for (Signature signature : packageInfo.signatures) {
                String currentSignature = getSignatureHash(signature);
                if (!EXPECTED_SIGNATURE.equals(currentSignature)) {
                    Log.w(TAG, "Invalid signature detected");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error verifying signature", e);
            return false;
        }
    }

    private String getSignatureHash(Signature signature) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(signature.toByteArray());
            return Base64.encodeToString(md.digest(), Base64.DEFAULT);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isAppTampered() {
        return isDebuggable() || isRunningOnEmulator() || isRooted() || hasUnknownSources();
    }

    private boolean isDebuggable() {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private boolean isRunningOnEmulator() {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MANUFACTURER.contains("Genymotion");
    }

    private boolean isRooted() {
        String[] rootPaths = {
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
        };

        for (String path : rootPaths) {
            if (new File(path).exists()) {
                Log.w(TAG, "Root access detected: " + path);
                return true;
            }
        }
        return false;
    }

    private boolean hasUnknownSources() {
        try {
            return Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.INSTALL_NON_MARKET_APPS) == 1;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    private boolean isRunningInUnsafeEnvironment() {
        // Check for debugging tools
        if (android.os.Debug.isDebuggerConnected() || android.os.Debug.waitingForDebugger()) {
            Log.w(TAG, "Debugger detected");
            return true;
        }

        return false;
    }

    private void startRuntimeProtection() {
        // Continuous monitoring in background thread
        new Thread(() -> {
            while (isSecurityActive) {
                try {
                    if (isAppTampered() || isRunningInUnsafeEnvironment()) {
                        handleSecurityViolation("Runtime tampering detected");
                        break;
                    }
                    Thread.sleep(30000); // Check every 30 seconds
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void handleSecurityViolation(String reason) {
        Log.e(TAG, "Security violation: " + reason);

        // For development: show warning
        // For production: disable functionality or exit
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.w(TAG, "Security violation in debug mode: " + reason);
        } else {
            // In production, take appropriate action
            disableAppFunctionality();
        }
    }

    private void disableAppFunctionality() {
        // Clear sensitive data
        clearSensitiveData();

        // Exit application
        System.exit(0);
    }

    private void clearSensitiveData() {
        // Clear any cached sensitive information
        // This is where you'd clear your local database or files if needed
    }

    private boolean validateOfflineLicense() {
        return OfflineLicenseManager.getInstance(context).validateLicense();
    }

    public boolean isSecurityActive() {
        return isSecurityActive;
    }

    public void stopSecurity() {
        isSecurityActive = false;
    }
}

