package com.example.meruscrap;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meruscrap.R;
import com.example.meruscrap.Material;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class MaterialsAdapter extends RecyclerView.Adapter<MaterialsAdapter.MaterialViewHolder> {
    private Context context;
    private List<Material> materials;
    private List<Material> materialsFiltered;
    private OnMaterialClickListener listener;
    private int selectedPosition = -1;

    public interface OnMaterialClickListener {
        void onMaterialClick(Material material, int position);
        void onMaterialLongClick(Material material, int position);
        void onMaterialEdit(Material material, int position);
        void onMaterialDelete(Material material, int position);
    }

    public MaterialsAdapter(Context context, List<Material> materials) {
        this.context = context;
        this.materials = materials != null ? materials : new ArrayList<>();
        this.materialsFiltered = new ArrayList<>(this.materials);
    }

    public void setOnMaterialClickListener(OnMaterialClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public MaterialViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_material, parent, false);
        return new MaterialViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MaterialViewHolder holder, int position) {
        Material material = materialsFiltered.get(position);
        holder.bind(material, position);
    }

    @Override
    public int getItemCount() {
        return materialsFiltered.size();
    }

    public void updateMaterials(List<Material> newMaterials) {
        this.materials.clear();
        this.materials.addAll(newMaterials);
        this.materialsFiltered.clear();
        this.materialsFiltered.addAll(newMaterials);
        notifyDataSetChanged();
    }

    public void addMaterial(Material material) {
        materials.add(material);
        materialsFiltered.add(material);
        notifyItemInserted(materialsFiltered.size() - 1);
    }

    public void updateMaterial(Material material, int position) {
        if (position >= 0 && position < materialsFiltered.size()) {
            // Update in both lists
            int originalPosition = materials.indexOf(materialsFiltered.get(position));
            if (originalPosition >= 0) {
                materials.set(originalPosition, material);
            }
            materialsFiltered.set(position, material);
            notifyItemChanged(position);
        }
    }

    public void removeMaterial(int position) {
        if (position >= 0 && position < materialsFiltered.size()) {
            Material removedMaterial = materialsFiltered.get(position);
            materials.remove(removedMaterial);
            materialsFiltered.remove(position);
            notifyItemRemoved(position);

            // Clear selection if removed item was selected
            if (selectedPosition == position) {
                selectedPosition = -1;
            } else if (selectedPosition > position) {
                selectedPosition--;
            }
        }
    }

    public void setSelectedPosition(int position) {
        int previousSelected = selectedPosition;
        selectedPosition = position;

        if (previousSelected >= 0) {
            notifyItemChanged(previousSelected);
        }
        if (selectedPosition >= 0) {
            notifyItemChanged(selectedPosition);
        }
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public Material getSelectedMaterial() {
        if (selectedPosition >= 0 && selectedPosition < materialsFiltered.size()) {
            return materialsFiltered.get(selectedPosition);
        }
        return null;
    }

    public void filter(String searchText) {
        materialsFiltered.clear();

        if (searchText == null || searchText.trim().isEmpty()) {
            materialsFiltered.addAll(materials);
        } else {
            String searchLower = searchText.toLowerCase().trim();
            for (Material material : materials) {
                if (material.getName().toLowerCase().contains(searchLower) ||
                        (material.getDescription() != null &&
                                material.getDescription().toLowerCase().contains(searchLower))) {
                    materialsFiltered.add(material);
                }
            }
        }

        selectedPosition = -1; // Clear selection when filtering
        notifyDataSetChanged();
    }

    public void sortByName() {
        materialsFiltered.sort((m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()));
        notifyDataSetChanged();
    }

    public void sortByPrice() {
        materialsFiltered.sort((m1, m2) -> Double.compare(m2.getPricePerKg(), m1.getPricePerKg()));
        notifyDataSetChanged();
    }

    public class MaterialViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView materialCard;
        private TextView tvMaterialIcon;
        private TextView tvMaterialName;
        private TextView tvMaterialPrice;
        private ImageView ivSelectedIndicator;
        private FrameLayout materialOverlay;

        public MaterialViewHolder(@NonNull View itemView) {
            super(itemView);
            materialCard = itemView.findViewById(R.id.material_card);
            tvMaterialIcon = itemView.findViewById(R.id.tv_material_icon);
            tvMaterialName = itemView.findViewById(R.id.tv_material_name);
            tvMaterialPrice = itemView.findViewById(R.id.tv_material_price);
            ivSelectedIndicator = itemView.findViewById(R.id.iv_selected_indicator);
            materialOverlay = itemView.findViewById(R.id.material_overlay);
        }

        public void bind(Material material, int position) {
            // Set material data
            tvMaterialIcon.setText(material.getDisplayIcon());
            tvMaterialName.setText(material.getDisplayName());
            tvMaterialPrice.setText(material.getFormattedPrice() + "/kg");

            // Handle selection state
            boolean isSelected = position == selectedPosition;
            materialCard.setChecked(isSelected);
            ivSelectedIndicator.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            // Set up click listeners
            materialCard.setOnClickListener(v -> {
                if (listener != null) {
                    setSelectedPosition(position);
                    listener.onMaterialClick(material, position);
                }
            });

            materialCard.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMaterialLongClick(material, position);
                }
                return true;
            });

            // Add some visual feedback
            materialCard.setOnClickListener(v -> {
                if (listener != null) {
                    // Visual feedback
                    materialCard.setChecked(true);

                    // Small delay to show the selection, then call listener
                    v.postDelayed(() -> {
                        setSelectedPosition(position);
                        listener.onMaterialClick(material, position);
                    }, 100);
                }
            });

            // Accessibility
            materialCard.setContentDescription(
                    material.getName() + ", " + material.getFormattedPrice() + " per kilogram"
            );

            // Hide overlay (it's shown in the Transactions fragment for already added materials)
            materialOverlay.setVisibility(View.GONE);
        }
    }

    // Helper methods for external usage
    public List<Material> getAllMaterials() {
        return new ArrayList<>(materials);
    }

    public List<Material> getFilteredMaterials() {
        return new ArrayList<>(materialsFiltered);
    }

    public boolean isEmpty() {
        return materialsFiltered.isEmpty();
    }

    public Material getMaterialAt(int position) {
        if (position >= 0 && position < materialsFiltered.size()) {
            return materialsFiltered.get(position);
        }
        return null;
    }

    public int findMaterialPosition(long materialId) {
        for (int i = 0; i < materialsFiltered.size(); i++) {
            if (materialsFiltered.get(i).getId() == materialId) {
                return i;
            }
        }
        return -1;
    }

    public void clearSelection() {
        setSelectedPosition(-1);
    }

    // Animation helper methods
    public void animateItemInsertion(int position) {
        notifyItemInserted(position);
    }

    public void animateItemRemoval(int position) {
        notifyItemRemoved(position);
    }

    public void animateItemChange(int position) {
        notifyItemChanged(position);
    }
}