package com.example.meruscrap;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * FIXED: Specialized adapter for material selection in transactions
 * Handles visual states for used materials and selection feedback
 * Used in the material selection grid in Transactions fragment
 */
public class TransactionMaterialsAdapter extends RecyclerView.Adapter<TransactionMaterialsAdapter.MaterialViewHolder> {
    private static final String TAG = "TransactionMaterialsAdapter";

    private Context context;
    private List<Material> materials;
    private List<String> usedMaterialIds;
    private OnMaterialClickListener listener;
    private DecimalFormat currencyFormat;

    public interface OnMaterialClickListener {
        void onMaterialClick(Material material, int position);
        void onMaterialLongClick(Material material, int position);
    }

    public TransactionMaterialsAdapter(Context context, List<Material> materials, List<String> usedMaterialIds) {
        this.context = context;
        this.materials = materials != null ? new ArrayList<>(materials) : new ArrayList<>();
        this.usedMaterialIds = usedMaterialIds != null ? new ArrayList<>(usedMaterialIds) : new ArrayList<>();
        this.currencyFormat = new DecimalFormat("KSH #,##0.00");

        Log.d(TAG, "Adapter created with " + this.materials.size() + " materials and " + this.usedMaterialIds.size() + " used materials");
    }

    public void setOnMaterialClickListener(OnMaterialClickListener listener) {
        this.listener = listener;
        Log.d(TAG, "Click listener set: " + (listener != null));
    }

    @NonNull
    @Override
    public MaterialViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_material_button, parent, false);
        return new MaterialViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MaterialViewHolder holder, int position) {
        if (position < materials.size()) {
            Material material = materials.get(position);
            holder.bind(material, position);
        } else {
            Log.w(TAG, "Invalid position: " + position + ", materials size: " + materials.size());
        }
    }

    @Override
    public int getItemCount() {
        int count = materials.size();
        Log.d(TAG, "getItemCount: " + count);
        return count;
    }

    /**
     * FIXED: Update the materials list and refresh the adapter
     */
    public void updateMaterials(List<Material> newMaterials) {
        Log.d(TAG, "updateMaterials called with " + (newMaterials != null ? newMaterials.size() : "NULL") + " materials");

        // EMERGENCY FIX: Create new list if current one is broken
        if (this.materials == null) {
            this.materials = new ArrayList<>();
            Log.d(TAG, "Creating new materials list - was null");
        }

        // Clear existing
        this.materials.clear();
        Log.d(TAG, "Cleared materials list, size now: " + this.materials.size());

        // Add new materials with extra checking
        if (newMaterials != null && !newMaterials.isEmpty()) {
            try {
                // Add one by one with verification
                for (Material material : newMaterials) {
                    if (material != null) {
                        this.materials.add(material);
                        Log.d(TAG, "Added material: " + material.getName() + " (Total now: " + this.materials.size() + ")");
                    } else {
                        Log.w(TAG, "Skipped null material");
                    }
                }

                Log.d(TAG, "Successfully added " + this.materials.size() + " materials");

                // Verify the list
                if (this.materials.size() != newMaterials.size()) {
                    Log.e(TAG, "SIZE MISMATCH! Expected: " + newMaterials.size() + ", Got: " + this.materials.size());
                }

            } catch (Exception e) {
                Log.e(TAG, "Error adding materials: " + e.getMessage(), e);
                // Fallback: recreate list
                this.materials = new ArrayList<>(newMaterials);
                Log.d(TAG, "Fallback: recreated list with size: " + this.materials.size());
            }
        } else {
            Log.w(TAG, "newMaterials is null or empty");
        }

        Log.d(TAG, "FINAL: Materials updated, new count: " + this.materials.size());

        // Force notify change
        notifyDataSetChanged();

        // Additional verification
        Log.d(TAG, "After notifyDataSetChanged(), getItemCount(): " + getItemCount());
    }

    /**
     * FIXED: Update the list of used material IDs and refresh the adapter
     */
    public void updateUsedMaterials(List<String> newUsedMaterialIds) {
        Log.d(TAG, "updateUsedMaterials called with " + (newUsedMaterialIds != null ? newUsedMaterialIds.size() : 0) + " used materials");

        this.usedMaterialIds.clear();
        if (newUsedMaterialIds != null) {
            this.usedMaterialIds.addAll(newUsedMaterialIds);
        }

        Log.d(TAG, "Used materials updated, new count: " + this.usedMaterialIds.size());
        notifyDataSetChanged();
    }

    /**
     * Check if a material is already used in the current transaction
     */
    public boolean isMaterialUsed(long materialId) {
        boolean isUsed = usedMaterialIds.contains(String.valueOf(materialId));
        Log.d(TAG, "Material ID " + materialId + " is used: " + isUsed);
        return isUsed;
    }

    /**
     * Get the count of available (not used) materials
     */
    public int getAvailableMaterialsCount() {
        int count = 0;
        for (Material material : materials) {
            if (!isMaterialUsed(material.getId())) {
                count++;
            }
        }
        Log.d(TAG, "Available materials count: " + count + " out of " + materials.size());
        return count;
    }

    /**
     * Get all materials (for external access)
     */
    public List<Material> getAllMaterials() {
        return new ArrayList<>(materials);
    }

    /**
     * Get material at specific position
     */
    public Material getMaterialAt(int position) {
        if (position >= 0 && position < materials.size()) {
            return materials.get(position);
        }
        return null;
    }

    /**
     * Check if adapter is empty
     */
    public boolean isEmpty() {
        return materials.isEmpty();
    }

    /**
     * Check if there are any available materials
     */
    public boolean hasAvailableMaterials() {
        return getAvailableMaterialsCount() > 0;
    }

    public class MaterialViewHolder extends RecyclerView.ViewHolder {
        private MaterialCardView materialCard;
        private TextView tvMaterialIcon;
        private TextView tvMaterialName;
        private TextView tvMaterialPrice;
        private ImageView ivSelectedIndicator;
        private FrameLayout materialOverlay;
        private TextView tvOverlayText;

        public MaterialViewHolder(@NonNull View itemView) {
            super(itemView);

            // Initialize views
            materialCard = itemView.findViewById(R.id.material_card);
            tvMaterialIcon = itemView.findViewById(R.id.tv_material_icon);
            tvMaterialName = itemView.findViewById(R.id.tv_material_name);
            tvMaterialPrice = itemView.findViewById(R.id.tv_material_price);
            ivSelectedIndicator = itemView.findViewById(R.id.iv_selected_indicator);
            materialOverlay = itemView.findViewById(R.id.material_overlay);

            // Get the overlay text view (should be in the overlay layout)
            if (materialOverlay != null && materialOverlay.getChildCount() > 0) {
                View overlayChild = materialOverlay.getChildAt(0);
                if (overlayChild instanceof TextView) {
                    tvOverlayText = (TextView) overlayChild;
                }
            }

            Log.d(TAG, "ViewHolder created, views initialized: " +
                    "card=" + (materialCard != null) +
                    ", icon=" + (tvMaterialIcon != null) +
                    ", name=" + (tvMaterialName != null) +
                    ", price=" + (tvMaterialPrice != null));
        }

        public void bind(Material material, int position) {
            if (material == null) {
                Log.w(TAG, "Attempting to bind null material at position " + position);
                return;
            }

            Log.d(TAG, "Binding material: " + material.getName() + " at position " + position);

            try {
                // Set material data with null checks
                if (tvMaterialIcon != null) {
                    tvMaterialIcon.setText(material.getDisplayIcon());
                }

                if (tvMaterialName != null) {
                    tvMaterialName.setText(material.getDisplayName());
                }

                if (tvMaterialPrice != null) {
                    String priceText = material.getFormattedPrice().replace("₦", "KSH") + "/kg";
                    tvMaterialPrice.setText(priceText);
                }

                // Check if material is used in current transaction
                boolean isUsed = isMaterialUsed(material.getId());
                Log.d(TAG, "Material " + material.getName() + " is used: " + isUsed);

                // Handle used state visual feedback
                handleUsedState(isUsed);

                // Set up click listeners
                setupClickListeners(material, position, isUsed);

                // Set accessibility description
                setAccessibilityDescription(material, isUsed);

            } catch (Exception e) {
                Log.e(TAG, "Error binding material " + material.getName() + ": " + e.getMessage(), e);
            }
        }

        private void handleUsedState(boolean isUsed) {
            if (materialOverlay != null) {
                if (isUsed) {
                    // Show overlay for used materials
                    materialOverlay.setVisibility(View.VISIBLE);
                    if (tvOverlayText != null) {
                        tvOverlayText.setText("Already\nAdded");
                    }

                    if (materialCard != null) {
                        materialCard.setAlpha(0.6f);
                        materialCard.setEnabled(false);
                    }

                    if (ivSelectedIndicator != null) {
                        ivSelectedIndicator.setVisibility(View.GONE);
                    }
                } else {
                    // Hide overlay for available materials
                    materialOverlay.setVisibility(View.GONE);

                    if (materialCard != null) {
                        materialCard.setAlpha(1.0f);
                        materialCard.setEnabled(true);
                    }

                    if (ivSelectedIndicator != null) {
                        ivSelectedIndicator.setVisibility(View.GONE);
                    }
                }
            }
        }

        private void setupClickListeners(Material material, int position, boolean isUsed) {
            if (materialCard != null) {
                if (!isUsed && listener != null) {
                    // Set up click listeners only for available materials
                    materialCard.setOnClickListener(v -> {
                        Log.d(TAG, "Material clicked: " + material.getName());

                        // Visual feedback
                        if (ivSelectedIndicator != null) {
                            ivSelectedIndicator.setVisibility(View.VISIBLE);
                        }

                        // Small delay to show the selection, then call listener
                        v.postDelayed(() -> {
                            if (ivSelectedIndicator != null) {
                                ivSelectedIndicator.setVisibility(View.GONE);
                            }
                            listener.onMaterialClick(material, position);
                        }, 150);
                    });

                    materialCard.setOnLongClickListener(v -> {
                        Log.d(TAG, "Material long clicked: " + material.getName());
                        listener.onMaterialLongClick(material, position);
                        return true;
                    });
                } else {
                    // Remove click listeners for used materials
                    materialCard.setOnClickListener(null);
                    materialCard.setOnLongClickListener(null);
                }
            }
        }

        private void setAccessibilityDescription(Material material, boolean isUsed) {
            if (materialCard != null) {
                if (isUsed) {
                    materialCard.setContentDescription(
                            material.getName() + " is already added to this transaction"
                    );
                } else {
                    materialCard.setContentDescription(
                            material.getName() + ", " + material.getFormattedPrice() + " per kilogram, tap to select"
                    );
                }
            }
        }
    }
}