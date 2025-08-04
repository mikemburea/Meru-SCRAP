package com.example.meruscrap;

/**
 * Represents a single weighing batch in accumulative weighing mode
 * Used when weighing multiple pieces of the same material separately
 */
public class WeighingBatch {
    private long timestamp;
    private double weight;
    private double pricePerKg;
    private String materialName;

    public WeighingBatch(long timestamp, double weight, double pricePerKg, String materialName) {
        this.timestamp = timestamp;
        this.weight = weight;
        this.pricePerKg = pricePerKg;
        this.materialName = materialName;
    }

    // Getters
    public long getTimestamp() {
        return timestamp;
    }

    public double getWeight() {
        return weight;
    }

    public double getPricePerKg() {
        return pricePerKg;
    }

    public String getMaterialName() {
        return materialName;
    }

    public double getValue() {
        return weight * pricePerKg;
    }

    // Setters
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public void setPricePerKg(double pricePerKg) {
        this.pricePerKg = pricePerKg;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    @Override
    public String toString() {
        return "WeighingBatch{" +
                "timestamp=" + timestamp +
                ", weight=" + weight +
                ", pricePerKg=" + pricePerKg +
                ", materialName='" + materialName + '\'' +
                ", value=" + getValue() +
                '}';
    }
}