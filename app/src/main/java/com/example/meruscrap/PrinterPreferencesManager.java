package com.example.meruscrap;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class PrinterPreferencesManager {
    private static final String PREF_NAME = "printer_preferences";
    private static final String KEY_SAVED_PRINTER_ADDRESS = "saved_printer_address";
    private static final String KEY_SAVED_PRINTER_NAME = "saved_printer_name";
    private static final String KEY_AUTO_CONNECT = "auto_connect";
    private static final String KEY_PRINT_HEADER = "print_header";
    private static final String KEY_PRINT_FOOTER = "print_footer";
    private static final String KEY_BUSINESS_NAME = "business_name";
    private static final String KEY_BUSINESS_ADDRESS = "business_address";
    private static final String KEY_BUSINESS_PHONE = "business_phone";
    private static final String KEY_RECEIPT_WIDTH = "receipt_width";
    private static final String KEY_PRINT_QR_CODE = "print_qr_code";
    private static final String KEY_AUTO_CUT = "auto_cut";
    private static final String KEY_DRAWER_KICK = "drawer_kick";
    private static final String KEY_CONNECTION_TIMEOUT = "connection_timeout";
    private static final String KEY_PRINT_RETRIES = "print_retries";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_TRUSTED_DEVICES = "trusted_devices";
    private static final String KEY_DEBUG_PRINTING = "debug_printing";

    private SharedPreferences prefs;
    private Context context;

    public PrinterPreferencesManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // Saved printer settings
    public void savePrinterDevice(String address, String name) {
        prefs.edit()
                .putString(KEY_SAVED_PRINTER_ADDRESS, address)
                .putString(KEY_SAVED_PRINTER_NAME, name)
                .apply();
    }

    public String getSavedPrinterAddress() {
        return prefs.getString(KEY_SAVED_PRINTER_ADDRESS, null);
    }

    public String getSavedPrinterName() {
        return prefs.getString(KEY_SAVED_PRINTER_NAME, "Unknown Printer");
    }

    public void clearSavedPrinter() {
        prefs.edit()
                .remove(KEY_SAVED_PRINTER_ADDRESS)
                .remove(KEY_SAVED_PRINTER_NAME)
                .apply();
    }

    // Connection settings
    public void setAutoConnect(boolean autoConnect) {
        prefs.edit().putBoolean(KEY_AUTO_CONNECT, autoConnect).apply();
    }

    public boolean isAutoConnectEnabled() {
        return prefs.getBoolean(KEY_AUTO_CONNECT, true);
    }

    public void setConnectionTimeout(int timeoutSeconds) {
        prefs.edit().putInt(KEY_CONNECTION_TIMEOUT, timeoutSeconds).apply();
    }

    public int getConnectionTimeout() {
        return prefs.getInt(KEY_CONNECTION_TIMEOUT, 30);
    }

    // Print settings
    public void setPrintHeader(boolean enabled) {
        prefs.edit().putBoolean(KEY_PRINT_HEADER, enabled).apply();
    }

    public boolean isPrintHeaderEnabled() {
        return prefs.getBoolean(KEY_PRINT_HEADER, true);
    }

    public void setPrintFooter(boolean enabled) {
        prefs.edit().putBoolean(KEY_PRINT_FOOTER, enabled).apply();
    }

    public boolean isPrintFooterEnabled() {
        return prefs.getBoolean(KEY_PRINT_FOOTER, true);
    }

    public void setPrintQRCode(boolean enabled) {
        prefs.edit().putBoolean(KEY_PRINT_QR_CODE, enabled).apply();
    }

    public boolean isPrintQRCodeEnabled() {
        return prefs.getBoolean(KEY_PRINT_QR_CODE, true);
    }

    public void setAutoCut(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_CUT, enabled).apply();
    }

    public boolean isAutoCutEnabled() {
        return prefs.getBoolean(KEY_AUTO_CUT, true);
    }

    public void setDrawerKick(boolean enabled) {
        prefs.edit().putBoolean(KEY_DRAWER_KICK, enabled).apply();
    }

    public boolean isDrawerKickEnabled() {
        return prefs.getBoolean(KEY_DRAWER_KICK, false);
    }

    // Business information
    public void setBusinessName(String name) {
        prefs.edit().putString(KEY_BUSINESS_NAME, name).apply();
    }

    public String getBusinessName() {
        return prefs.getString(KEY_BUSINESS_NAME, "Meru Scrap Metal Market");
    }

    public void setBusinessAddress(String address) {
        prefs.edit().putString(KEY_BUSINESS_ADDRESS, address).apply();
    }

    public String getBusinessAddress() {
        return prefs.getString(KEY_BUSINESS_ADDRESS, "Meru County, Kenya");
    }

    public void setBusinessPhone(String phone) {
        prefs.edit().putString(KEY_BUSINESS_PHONE, phone).apply();
    }

    public String getBusinessPhone() {
        return prefs.getString(KEY_BUSINESS_PHONE, "+254 XXX XXX XXX");
    }

    // Receipt formatting
    public void setReceiptWidth(int width) {
        prefs.edit().putInt(KEY_RECEIPT_WIDTH, width).apply();
    }

    public int getReceiptWidth() {
        return prefs.getInt(KEY_RECEIPT_WIDTH, 32);
    }

    public void setFontSize(FontSize fontSize) {
        prefs.edit().putString(KEY_FONT_SIZE, fontSize.name()).apply();
    }

    public FontSize getFontSize() {
        String fontSizeStr = prefs.getString(KEY_FONT_SIZE, FontSize.NORMAL.name());
        try {
            return FontSize.valueOf(fontSizeStr);
        } catch (IllegalArgumentException e) {
            return FontSize.NORMAL;
        }
    }

    // Print reliability
    public void setPrintRetries(int retries) {
        prefs.edit().putInt(KEY_PRINT_RETRIES, retries).apply();
    }

    public int getPrintRetries() {
        return prefs.getInt(KEY_PRINT_RETRIES, 3);
    }

    // Trusted devices
    public void addTrustedDevice(String address) {
        Set<String> trustedDevices = getTrustedDevices();
        trustedDevices.add(address);
        prefs.edit().putStringSet(KEY_TRUSTED_DEVICES, trustedDevices).apply();
    }

    public void removeTrustedDevice(String address) {
        Set<String> trustedDevices = getTrustedDevices();
        trustedDevices.remove(address);
        prefs.edit().putStringSet(KEY_TRUSTED_DEVICES, trustedDevices).apply();
    }

    public Set<String> getTrustedDevices() {
        return new HashSet<>(prefs.getStringSet(KEY_TRUSTED_DEVICES, new HashSet<>()));
    }

    public boolean isTrustedDevice(String address) {
        return getTrustedDevices().contains(address);
    }

    // Debug settings
    public void setDebugPrinting(boolean enabled) {
        prefs.edit().putBoolean(KEY_DEBUG_PRINTING, enabled).apply();
    }

    public boolean isDebugPrintingEnabled() {
        return prefs.getBoolean(KEY_DEBUG_PRINTING, false);
    }

    // Enums
    public enum FontSize {
        SMALL(0),
        NORMAL(1),
        LARGE(2);

        private final int value;

        FontSize(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // Get all settings as a bundle for easy display/export
    public PrinterSettings getAllSettings() {
        PrinterSettings settings = new PrinterSettings();
        settings.savedPrinterAddress = getSavedPrinterAddress();
        settings.savedPrinterName = getSavedPrinterName();
        settings.autoConnect = isAutoConnectEnabled();
        settings.printHeader = isPrintHeaderEnabled();
        settings.printFooter = isPrintFooterEnabled();
        settings.businessName = getBusinessName();
        settings.businessAddress = getBusinessAddress();
        settings.businessPhone = getBusinessPhone();
        settings.receiptWidth = getReceiptWidth();
        settings.printQRCode = isPrintQRCodeEnabled();
        settings.autoCut = isAutoCutEnabled();
        settings.drawerKick = isDrawerKickEnabled();
        settings.connectionTimeout = getConnectionTimeout();
        settings.printRetries = getPrintRetries();
        settings.fontSize = getFontSize();
        settings.trustedDevices = getTrustedDevices();
        settings.debugPrinting = isDebugPrintingEnabled();
        return settings;
    }

    // Data class for all settings
    public static class PrinterSettings {
        public String savedPrinterAddress;
        public String savedPrinterName;
        public boolean autoConnect;
        public boolean printHeader;
        public boolean printFooter;
        public String businessName;
        public String businessAddress;
        public String businessPhone;
        public int receiptWidth;
        public boolean printQRCode;
        public boolean autoCut;
        public boolean drawerKick;
        public int connectionTimeout;
        public int printRetries;
        public FontSize fontSize;
        public Set<String> trustedDevices;
        public boolean debugPrinting;
    }
}