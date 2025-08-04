package com.example.meruscrap;

/**
 * Interface to abstract BLE scale operations for the AccumulativeWeighingManager
 * This allows the manager to work with different BLE implementations
 */
public interface BleScaleInterface {

    /**
     * Check if scale is currently connected
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Get current weight reading from scale
     * @return current weight in kg
     */
    double getCurrentWeight();

    /**
     * Check if current weight reading is stable
     * @return true if weight is stable, false if still stabilizing
     */
    boolean isStable();

    /**
     * Get the last stable weight reading
     * @return last stable weight in kg
     */
    double getLastStableWeight();

    /**
     * Perform tare operation (zero the scale)
     */
    void tare();

    /**
     * Disconnect from the scale
     */
    void disconnect();
}

// Usage in AccumulativeWeighingManager constructor:
/*
public AccumulativeWeighingManager(Context context, BleScaleInterface bleScaleInterface,
                                 AccumulativeWeighingCallback callback) {
    this.context = context;
    this.bleScaleInterface = bleScaleInterface;
    this.callback = callback;
    // ... rest of initialization
}

// Then in your methods, use:
// bleScaleInterface.getCurrentWeight() instead of bleScaleManager.getCurrentWeight()
// bleScaleInterface.isConnected() instead of bleScaleManager.isConnected()
// etc.
*/