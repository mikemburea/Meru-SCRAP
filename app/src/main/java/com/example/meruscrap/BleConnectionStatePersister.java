package com.example.meruscrap;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class BleConnectionStatePersister {
    private static final String TAG = "BleConnectionStatePersister";
    private static final String PREFS_NAME = "ble_connection_state";

    private final SharedPreferences prefs;

    // State keys
    private static final String KEY_DEVICE_ADDRESS = "device_address";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_CONNECTION_TIME = "connection_time";
    private static final String KEY_LAST_WEIGHT = "last_weight";
    private static final String KEY_WEIGHT_STABLE = "weight_stable";
    private static final String KEY_AUTO_RECONNECT = "auto_reconnect";

    public BleConnectionStatePersister(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveConnectionState(String deviceAddress, String deviceName, boolean autoReconnect) {
        prefs.edit()
                .putString(KEY_DEVICE_ADDRESS, deviceAddress)
                .putString(KEY_DEVICE_NAME, deviceName)
                .putLong(KEY_CONNECTION_TIME, System.currentTimeMillis())
                .putBoolean(KEY_AUTO_RECONNECT, autoReconnect)
                .apply();

        Log.d(TAG, "Connection state saved: " + deviceName + " (" + deviceAddress + ")");
    }

    public void saveWeightState(double weight, boolean isStable) {
        prefs.edit()
                .putFloat(KEY_LAST_WEIGHT, (float) weight)
                .putBoolean(KEY_WEIGHT_STABLE, isStable)
                .apply();
    }

    public void clearConnectionState() {
        prefs.edit()
                .remove(KEY_DEVICE_ADDRESS)
                .remove(KEY_DEVICE_NAME)
                .remove(KEY_CONNECTION_TIME)
                .remove(KEY_AUTO_RECONNECT)
                .apply();

        Log.d(TAG, "Connection state cleared");
    }

    public ConnectionState getConnectionState() {
        ConnectionState state = new ConnectionState();

        state.deviceAddress = prefs.getString(KEY_DEVICE_ADDRESS, null);
        state.deviceName = prefs.getString(KEY_DEVICE_NAME, null);
        state.connectionTime = prefs.getLong(KEY_CONNECTION_TIME, 0);
        state.lastWeight = prefs.getFloat(KEY_LAST_WEIGHT, 0.0f);
        state.weightStable = prefs.getBoolean(KEY_WEIGHT_STABLE, false);
        state.autoReconnect = prefs.getBoolean(KEY_AUTO_RECONNECT, true);

        return state;
    }

    public boolean hasPersistedConnection() {
        return prefs.getString(KEY_DEVICE_ADDRESS, null) != null;
    }

    public boolean shouldAutoReconnect() {
        return prefs.getBoolean(KEY_AUTO_RECONNECT, true) && hasPersistedConnection();
    }

    public static class ConnectionState {
        public String deviceAddress;
        public String deviceName;
        public long connectionTime;
        public float lastWeight;
        public boolean weightStable;
        public boolean autoReconnect;

        public boolean isValid() {
            return deviceAddress != null && !deviceAddress.isEmpty();
        }

        public long getConnectionAge() {
            return connectionTime > 0 ? System.currentTimeMillis() - connectionTime : Long.MAX_VALUE;
        }

        @Override
        public String toString() {
            return String.format(
                    "ConnectionState{device='%s (%s)', age=%dms, weight=%.2f, stable=%b, autoReconnect=%b}",
                    deviceName, deviceAddress, getConnectionAge(), lastWeight, weightStable, autoReconnect
            );
        }
    }
}