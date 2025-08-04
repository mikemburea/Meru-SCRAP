package com.example.meruscrap;

public class BleDevice {
    private String id;
    private String name;
    private String address;
    private int rssi;
    private long lastSeen;

    public BleDevice(String address, String name, int rssi) {
        this.id = address;
        this.address = address;
        this.name = name != null && !name.trim().isEmpty() ? name : "Unknown Scale";
        this.rssi = rssi;
        this.lastSeen = System.currentTimeMillis();
    }

    public void updateRssi(int newRssi) {
        this.rssi = newRssi;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getSignalQuality() {
        if (rssi > -60) return "Excellent";
        if (rssi > -70) return "Good";
        if (rssi > -80) return "Fair";
        return "Poor";
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getRssi() { return rssi; }
    public long getLastSeen() { return lastSeen; }
}