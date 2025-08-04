package com.example.meruscrap;

/**
 * Represents a complete material entry in a transaction
 * This is the final material entry that gets added to the transaction summary
 */
public class TransactionMaterial {
    private String materialName;
    private double weight;
    private double pricePerKg;
    private long timestamp;

    public TransactionMaterial(String materialName, double weight, double pricePerKg, long timestamp) {
        this.materialName = materialName;
        this.weight = weight;
        this.pricePerKg = pricePerKg;
        this.timestamp = timestamp;
    }

    // Getters
    public String getMaterialName() {
        return materialName;
    }

    public double getWeight() {
        return weight;
    }

    public double getPricePerKg() {
        return pricePerKg;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getValue() {
        return weight * pricePerKg;
    }

    // Setters
    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public void setPricePerKg(double pricePerKg) {
        this.pricePerKg = pricePerKg;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // Utility methods
    public String getFormattedWeight() {
        return String.format("%.2f kg", weight);
    }

    public String getFormattedValue() {
        return String.format("KSH %.2f", getValue());
    }

    public String getFormattedPrice() {
        return String.format("KSH %.2f/kg", pricePerKg);
    }

    @Override
    public String toString() {
        return "TransactionMaterial{" +
                "materialName='" + materialName + '\'' +
                ", weight=" + weight +
                ", pricePerKg=" + pricePerKg +
                ", timestamp=" + timestamp +
                ", value=" + getValue() +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TransactionMaterial that = (TransactionMaterial) obj;
        return Double.compare(that.weight, weight) == 0 &&
                Double.compare(that.pricePerKg, pricePerKg) == 0 &&
                timestamp == that.timestamp &&
                materialName.equals(that.materialName);
    }

    @Override
    public int hashCode() {
        return materialName.hashCode() + (int) (timestamp % Integer.MAX_VALUE);
    }
}