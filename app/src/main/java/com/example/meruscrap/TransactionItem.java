package com.example.meruscrap;

public class TransactionItem {
    private long id;
    private long transactionId;
    private String materialName;
    private double weight;
    private double pricePerKg;
    private double totalValue;
    private long timestamp;
    private String notes;

    // Default constructor
    public TransactionItem() {
        this.timestamp = System.currentTimeMillis();
    }

    // Constructor from TransactionMaterial
    public TransactionItem(long transactionId, TransactionMaterial transactionMaterial) {
        this();
        this.transactionId = transactionId;
        this.materialName = transactionMaterial.getMaterialName();
        this.weight = transactionMaterial.getWeight();
        this.pricePerKg = transactionMaterial.getPricePerKg();
        this.totalValue = transactionMaterial.getValue();
    }

    // Constructor with essential fields
    public TransactionItem(long transactionId, String materialName, double weight,
                           double pricePerKg, double totalValue) {
        this();
        this.transactionId = transactionId;
        this.materialName = materialName;
        this.weight = weight;
        this.pricePerKg = pricePerKg;
        this.totalValue = totalValue;
    }

    // Full constructor
    public TransactionItem(long id, long transactionId, String materialName, double weight,
                           double pricePerKg, double totalValue, long timestamp, String notes) {
        this.id = id;
        this.transactionId = transactionId;
        this.materialName = materialName;
        this.weight = weight;
        this.pricePerKg = pricePerKg;
        this.totalValue = totalValue;
        this.timestamp = timestamp;
        this.notes = notes;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(long transactionId) {
        this.transactionId = transactionId;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public double getPricePerKg() {
        return pricePerKg;
    }

    public void setPricePerKg(double pricePerKg) {
        this.pricePerKg = pricePerKg;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(double totalValue) {
        this.totalValue = totalValue;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    // Utility methods
    public String getFormattedWeight() {
        return String.format(java.util.Locale.getDefault(), "%.2f kg", weight);
    }

    public String getFormattedPrice() {
        return String.format(java.util.Locale.getDefault(), "KSH %.2f/kg", pricePerKg);
    }

    public String getFormattedValue() {
        return String.format(java.util.Locale.getDefault(), "KSH %.2f", totalValue);
    }

    public String getFormattedTimestamp() {
        return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(timestamp));
    }

    @Override
    public String toString() {
        return "TransactionItem{" +
                "id=" + id +
                ", transactionId=" + transactionId +
                ", materialName='" + materialName + '\'' +
                ", weight=" + weight +
                ", pricePerKg=" + pricePerKg +
                ", totalValue=" + totalValue +
                ", timestamp=" + timestamp +
                '}';
    }
}