package com.example.meruscrap;

import java.text.DecimalFormat;

public class Material {
    private long id;
    private String name;
    private double pricePerKg;
    private String icon;
    private String description;
    private long createdAt;
    private long updatedAt;
    private boolean isActive;

    // Constructors
    public Material() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.isActive = true;
    }

    public Material(String name, double pricePerKg, String icon) {
        this();
        this.name = name;
        this.pricePerKg = pricePerKg;
        this.icon = icon;
    }

    public Material(String name, double pricePerKg, String icon, String description) {
        this(name, pricePerKg, icon);
        this.description = description;
    }

    public Material(long id, String name, double pricePerKg, String icon, String description,
                    long createdAt, long updatedAt, boolean isActive) {
        this.id = id;
        this.name = name;
        this.pricePerKg = pricePerKg;
        this.icon = icon;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isActive = isActive;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = System.currentTimeMillis();
    }

    public double getPricePerKg() {
        return pricePerKg;
    }

    public void setPricePerKg(double pricePerKg) {
        this.pricePerKg = pricePerKg;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
        this.updatedAt = System.currentTimeMillis();
    }

    // Utility Methods
    public String getFormattedPrice() {
        DecimalFormat currencyFormat = new DecimalFormat("KSH #,##0.00");
        return currencyFormat.format(pricePerKg);
    }

    public String getDisplayName() {
        return name != null ? name : "Unknown Material";
    }

    public String getDisplayIcon() {
        return icon != null && !icon.isEmpty() ? icon : "🔩";
    }

    public double calculateValue(double weight) {
        return weight * pricePerKg;
    }

    public String getFormattedValue(double weight) {
        DecimalFormat currencyFormat = new DecimalFormat("KSH #,##0.00");
        return currencyFormat.format(calculateValue(weight));
    }

    // Validation Methods
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() && pricePerKg > 0;
    }

    public String getValidationError() {
        if (name == null || name.trim().isEmpty()) {
            return "Material name is required";
        }
        if (pricePerKg <= 0) {
            return "Price must be greater than 0";
        }
        return null;
    }

    // Override methods
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Material material = (Material) obj;
        return id == material.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Material{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", pricePerKg=" + pricePerKg +
                ", icon='" + icon + '\'' +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", isActive=" + isActive +
                '}';
    }

    // Static factory methods for common materials
    public static Material createSteel() {
        return new Material("Steel", 450.0, "🔩", "Common construction and automotive steel");
    }

    public static Material createAluminum() {
        return new Material("Aluminum", 280.0, "🥫", "Lightweight metal from cans and sheets");
    }

    public static Material createCopper() {
        return new Material("Copper", 1200.0, "🔶", "High-value metal from wires and pipes");
    }

    public static Material createBrass() {
        return new Material("Brass", 650.0, "🟨", "Alloy metal from fittings and decorations");
    }

    public static Material createIron() {
        return new Material("Iron", 320.0, "⚫", "Heavy ferrous metal, common in machinery");
    }

    public static Material createLead() {
        return new Material("Lead", 380.0, "🔘", "Dense metal from batteries and roofing");
    }
}