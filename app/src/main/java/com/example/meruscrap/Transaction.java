package com.example.meruscrap;

import java.util.List;

public class Transaction {
    private long id;
    private String transactionId;
    private long timestamp;
    private double totalWeight;
    private double totalValue;
    private int materialCount;
    private String status; // "COMPLETED", "PENDING", "CANCELLED"
    private String notes;
    private List<TransactionItem> items;

    // Default constructor
    public Transaction() {
        this.timestamp = System.currentTimeMillis();
        this.status = "COMPLETED";
    }

    // Constructor with basic details
    public Transaction(String transactionId, double totalWeight, double totalValue, int materialCount) {
        this();
        this.transactionId = transactionId;
        this.totalWeight = totalWeight;
        this.totalValue = totalValue;
        this.materialCount = materialCount;
    }

    // Full constructor
    public Transaction(long id, String transactionId, long timestamp, double totalWeight,
                       double totalValue, int materialCount, String status, String notes) {
        this.id = id;
        this.transactionId = transactionId;
        this.timestamp = timestamp;
        this.totalWeight = totalWeight;
        this.totalValue = totalValue;
        this.materialCount = materialCount;
        this.status = status;
        this.notes = notes;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(double totalWeight) {
        this.totalWeight = totalWeight;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(double totalValue) {
        this.totalValue = totalValue;
    }

    public int getMaterialCount() {
        return materialCount;
    }

    public void setMaterialCount(int materialCount) {
        this.materialCount = materialCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<TransactionItem> getItems() {
        return items;
    }

    public void setItems(List<TransactionItem> items) {
        this.items = items;
    }

    // Utility methods
    public String getFormattedTimestamp() {
        return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(timestamp));
    }

    public String getFormattedTotalWeight() {
        return String.format(java.util.Locale.getDefault(), "%.2f kg", totalWeight);
    }

    public String getFormattedTotalValue() {
        return String.format(java.util.Locale.getDefault(), "KSH %.2f", totalValue);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", transactionId='" + transactionId + '\'' +
                ", timestamp=" + timestamp +
                ", totalWeight=" + totalWeight +
                ", totalValue=" + totalValue +
                ", materialCount=" + materialCount +
                ", status='" + status + '\'' +
                '}';
    }
}