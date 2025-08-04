package com.example.meruscrap;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Adapter for displaying transaction materials in the transaction summary list
 * Used in the transaction summary card to show all materials added to the current transaction
 */
public class TransactionMaterialAdapter extends RecyclerView.Adapter<TransactionMaterialAdapter.ViewHolder> {
    private List<TransactionMaterial> materials;
    private OnMaterialRemovedListener listener;
    private DecimalFormat weightFormat = new DecimalFormat("0.00");
    private DecimalFormat currencyFormat = new DecimalFormat("KSH #,##0.00");

    public interface OnMaterialRemovedListener {
        void onMaterialRemoved(int position);
    }

    public TransactionMaterialAdapter(List<TransactionMaterial> materials, OnMaterialRemovedListener listener) {
        this.materials = materials;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction_material, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TransactionMaterial material = materials.get(position);

        holder.tvMaterialName.setText(material.getMaterialName());
        holder.tvWeight.setText(weightFormat.format(material.getWeight()) + " kg");
        holder.tvValue.setText(currencyFormat.format(material.getValue()));
        holder.tvPrice.setText(currencyFormat.format(material.getPricePerKg()) + "/kg");

        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMaterialRemoved(holder.getAdapterPosition());
            }
        });

        // Set accessibility description
        holder.itemView.setContentDescription(
                material.getMaterialName() + ", " +
                        weightFormat.format(material.getWeight()) + " kilograms, " +
                        currencyFormat.format(material.getValue()) + ", tap remove button to delete"
        );
    }

    @Override
    public int getItemCount() {
        return materials.size();
    }

    /**
     * Update the materials list and refresh the adapter
     */
    public void updateMaterials(List<TransactionMaterial> newMaterials) {
        this.materials.clear();
        if (newMaterials != null) {
            this.materials.addAll(newMaterials);
        }
        notifyDataSetChanged();
    }

    /**
     * Add a new material to the list
     */
    public void addMaterial(TransactionMaterial material) {
        materials.add(material);
        notifyItemInserted(materials.size() - 1);
    }

    /**
     * Remove material at position
     */
    public void removeMaterial(int position) {
        if (position >= 0 && position < materials.size()) {
            materials.remove(position);
            notifyItemRemoved(position);
        }
    }

    /**
     * Update material at position
     */
    public void updateMaterial(int position, TransactionMaterial material) {
        if (position >= 0 && position < materials.size()) {
            materials.set(position, material);
            notifyItemChanged(position);
        }
    }

    /**
     * Get material at position
     */
    public TransactionMaterial getMaterialAt(int position) {
        if (position >= 0 && position < materials.size()) {
            return materials.get(position);
        }
        return null;
    }

    /**
     * Check if the list is empty
     */
    public boolean isEmpty() {
        return materials.isEmpty();
    }

    /**
     * Get total weight of all materials
     */
    public double getTotalWeight() {
        double total = 0.0;
        for (TransactionMaterial material : materials) {
            total += material.getWeight();
        }
        return total;
    }

    /**
     * Get total value of all materials
     */
    public double getTotalValue() {
        double total = 0.0;
        for (TransactionMaterial material : materials) {
            total += material.getValue();
        }
        return total;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMaterialName, tvWeight, tvValue, tvPrice;
        MaterialButton btnRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMaterialName = itemView.findViewById(R.id.tv_material_name);
            tvWeight = itemView.findViewById(R.id.tv_material_weight);
            tvValue = itemView.findViewById(R.id.tv_material_value);
            tvPrice = itemView.findViewById(R.id.tv_material_price);
            btnRemove = itemView.findViewById(R.id.btn_remove_material);
        }
    }
}