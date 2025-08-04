package com.example.meruscrap;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meruscrap.MaterialsAdapter;
import com.example.meruscrap.MaterialsDBHelper;
import com.example.meruscrap.Material;
import com.example.meruscrap.GridSpacingItemDecoration;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class Materials extends Fragment implements MaterialsAdapter.OnMaterialClickListener {
    private static final String TAG = "MaterialsFragment";

    // UI Components
    private RecyclerView rvMaterials;
    private LinearLayout emptyState;
    private TextView tvMaterialCount;
    private TextInputEditText etSearchMaterials;
    private MaterialButton btnSortMaterials;
    private MaterialButton btnAddMaterialQuick;
    private MaterialButton btnAddFirstMaterial;
    private FloatingActionButton fabAddMaterial;

    // Data & Adapter
    private MaterialsAdapter materialsAdapter;
    private MaterialsDBHelper dbHelper;
    private List<Material> materials;

    // State
    private boolean isSortedByPrice = false;

    public Materials() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbHelper = MaterialsDBHelper.getInstance(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_materials, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupRecyclerView();
        setupClickListeners();
        setupSearchFunctionality();
        loadMaterials();
    }

    private void initializeViews(View view) {
        rvMaterials = view.findViewById(R.id.rv_materials);
        emptyState = view.findViewById(R.id.empty_state);
        tvMaterialCount = view.findViewById(R.id.tv_material_count);
        etSearchMaterials = view.findViewById(R.id.et_search_materials);
        btnSortMaterials = view.findViewById(R.id.btn_sort_materials);
        btnAddMaterialQuick = view.findViewById(R.id.btn_add_material_quick);
        btnAddFirstMaterial = view.findViewById(R.id.btn_add_first_material);
        fabAddMaterial = view.findViewById(R.id.fab_add_material);
    }

    private void setupRecyclerView() {
        materials = dbHelper.getAllMaterials();
        materialsAdapter = new MaterialsAdapter(getContext(), materials);
        materialsAdapter.setOnMaterialClickListener(this);

        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
        rvMaterials.setLayoutManager(gridLayoutManager);
        rvMaterials.setAdapter(materialsAdapter);

        // Add spacing between grid items
        int spacing = getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        rvMaterials.addItemDecoration(new GridSpacingItemDecoration(2, spacing, true));
    }

    private void setupClickListeners() {
        btnAddMaterialQuick.setOnClickListener(v -> showAddMaterialDialog(null));
        btnAddFirstMaterial.setOnClickListener(v -> showAddMaterialDialog(null));
        fabAddMaterial.setOnClickListener(v -> showAddMaterialDialog(null));

        btnSortMaterials.setOnClickListener(v -> toggleSort());
    }

    private void setupSearchFunctionality() {
        etSearchMaterials.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                materialsAdapter.filter(s.toString());
                updateEmptyState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadMaterials() {
        try {
            materials = dbHelper.getAllMaterials();
            materialsAdapter.updateMaterials(materials);
            updateMaterialCount();
            updateEmptyState();
            Log.d(TAG, "Loaded " + materials.size() + " materials");
        } catch (Exception e) {
            Log.e(TAG, "Error loading materials: " + e.getMessage());
            Toast.makeText(getContext(), "Error loading materials", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMaterialCount() {
        int count = materials.size();
        String countText = count == 1 ? "1 material configured" : count + " materials configured";
        tvMaterialCount.setText(countText);
    }

    private void updateEmptyState() {
        boolean isEmpty = materialsAdapter.isEmpty();
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvMaterials.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void toggleSort() {
        if (isSortedByPrice) {
            materialsAdapter.sortByName();
            btnSortMaterials.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_sort_alphabetically));
            Toast.makeText(getContext(), "Sorted by name", Toast.LENGTH_SHORT).show();
        } else {
            materialsAdapter.sortByPrice();
            btnSortMaterials.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_sort_by_size));
            Toast.makeText(getContext(), "Sorted by price", Toast.LENGTH_SHORT).show();
        }
        isSortedByPrice = !isSortedByPrice;
    }

    private void showAddMaterialDialog(@Nullable Material materialToEdit) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_material, null);

        // Get dialog views
        TextView tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextInputEditText etMaterialName = dialogView.findViewById(R.id.et_material_name);
        TextInputEditText etMaterialPrice = dialogView.findViewById(R.id.et_material_price);
        TextInputEditText etMaterialDescription = dialogView.findViewById(R.id.et_material_description);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnSaveMaterial = dialogView.findViewById(R.id.btn_save_material);

        // Icon selection views
        TextView iconSteel = dialogView.findViewById(R.id.icon_steel);
        TextView iconAluminum = dialogView.findViewById(R.id.icon_aluminum);
        TextView iconCopper = dialogView.findViewById(R.id.icon_copper);
        TextView iconBrass = dialogView.findViewById(R.id.icon_brass);
        TextView iconIron = dialogView.findViewById(R.id.icon_iron);
        TextView iconLead = dialogView.findViewById(R.id.icon_lead);
        TextView iconZinc = dialogView.findViewById(R.id.icon_zinc);
        TextView iconMixed = dialogView.findViewById(R.id.icon_mixed);

        TextView[] iconViews = {iconSteel, iconAluminum, iconCopper, iconBrass, iconIron, iconLead, iconZinc, iconMixed};
        String[] iconEmojis = {"🔩", "🥫", "🔶", "🟨", "⚫", "🔘", "⚪", "🔗"};

        // Selected icon tracking
        final String[] selectedIcon = {iconEmojis[0]}; // Default to steel

        // Set up icon selection
        for (int i = 0; i < iconViews.length; i++) {
            final int index = i;
            iconViews[i].setOnClickListener(v -> {
                // Clear previous selections
                for (TextView icon : iconViews) {
                    icon.setBackgroundResource(R.drawable.icon_selector_background);
                }
                // Select current icon
                iconViews[index].setBackgroundResource(R.drawable.icon_selector_selected);
                selectedIcon[0] = iconEmojis[index];
            });
        }

        // Pre-fill data if editing
        boolean isEditing = materialToEdit != null;
        if (isEditing) {
            tvDialogTitle.setText("Edit Material");
            btnSaveMaterial.setText("Update Material");

            etMaterialName.setText(materialToEdit.getName());
            etMaterialPrice.setText(String.valueOf(materialToEdit.getPricePerKg()));
            etMaterialDescription.setText(materialToEdit.getDescription());
            selectedIcon[0] = materialToEdit.getIcon();

            // Select the correct icon
            for (int i = 0; i < iconEmojis.length; i++) {
                if (iconEmojis[i].equals(materialToEdit.getIcon())) {
                    iconViews[i].setBackgroundResource(R.drawable.icon_selector_selected);
                    break;
                }
            }
        } else {
            // Select default icon (steel)
            iconSteel.setBackgroundResource(R.drawable.icon_selector_selected);
        }

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSaveMaterial.setOnClickListener(v -> {
            String name = etMaterialName.getText().toString().trim();
            String priceStr = etMaterialPrice.getText().toString().trim();
            String description = etMaterialDescription.getText().toString().trim();

            // Validation
            if (name.isEmpty()) {
                etMaterialName.setError("Material name is required");
                return;
            }

            if (priceStr.isEmpty()) {
                etMaterialPrice.setError("Price is required");
                return;
            }

            double price;
            try {
                price = Double.parseDouble(priceStr);
                if (price <= 0) {
                    etMaterialPrice.setError("Price must be greater than 0");
                    return;
                }
            } catch (NumberFormatException e) {
                etMaterialPrice.setError("Invalid price format");
                return;
            }

            // Check for duplicate name (excluding current material if editing)
            long excludeId = isEditing ? materialToEdit.getId() : 0;
            if (dbHelper.materialNameExists(name, excludeId)) {
                etMaterialName.setError("Material with this name already exists");
                return;
            }

            // Create or update material
            if (isEditing) {
                materialToEdit.setName(name);
                materialToEdit.setPricePerKg(price);
                materialToEdit.setIcon(selectedIcon[0]);
                materialToEdit.setDescription(description.isEmpty() ? null : description);

                int result = dbHelper.updateMaterial(materialToEdit);
                if (result > 0) {
                    Toast.makeText(getContext(), "Material updated successfully", Toast.LENGTH_SHORT).show();
                    loadMaterials(); // Reload to get fresh data
                } else {
                    Toast.makeText(getContext(), "Failed to update material", Toast.LENGTH_SHORT).show();
                }
            } else {
                Material newMaterial = new Material(name, price, selectedIcon[0],
                        description.isEmpty() ? null : description);

                long result = dbHelper.insertMaterial(newMaterial);
                if (result > 0) {
                    Toast.makeText(getContext(), "Material added successfully", Toast.LENGTH_SHORT).show();
                    materialsAdapter.addMaterial(newMaterial);
                    updateMaterialCount();
                    updateEmptyState();
                } else {
                    Toast.makeText(getContext(), "Failed to add material", Toast.LENGTH_SHORT).show();
                }
            }

            dialog.dismiss();
        });

        dialog.show();
    }

    private void showDeleteConfirmDialog(Material material, int position) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Material")
                .setMessage("Are you sure you want to delete \"" + material.getName() + "\"? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    int result = dbHelper.deleteMaterial(material.getId());
                    if (result > 0) {
                        materialsAdapter.removeMaterial(position);
                        updateMaterialCount();
                        updateEmptyState();
                        Toast.makeText(getContext(), material.getName() + " deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Failed to delete material", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void showMaterialOptionsDialog(Material material, int position) {
        String[] options = {"Edit", "Delete"};

        new AlertDialog.Builder(getContext())
                .setTitle(material.getName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Edit
                            showAddMaterialDialog(material);
                            break;
                        case 1: // Delete
                            showDeleteConfirmDialog(material, position);
                            break;
                    }
                })
                .show();
    }

    // MaterialsAdapter.OnMaterialClickListener implementation
    @Override
    public void onMaterialClick(Material material, int position) {
        // Simple click - could navigate to detail view or show info
        Toast.makeText(getContext(), material.getName() + " - " + material.getFormattedPrice() + "/kg",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMaterialLongClick(Material material, int position) {
        // Long click - show options menu
        showMaterialOptionsDialog(material, position);
    }

    @Override
    public void onMaterialEdit(Material material, int position) {
        showAddMaterialDialog(material);
    }

    @Override
    public void onMaterialDelete(Material material, int position) {
        showDeleteConfirmDialog(material, position);
    }

    // Public methods for external access (e.g., from Transactions fragment)
    public List<Material> getAllMaterials() {
        return dbHelper.getAllMaterials();
    }

    public Material getMaterialById(long id) {
        return dbHelper.getMaterialById(id);
    }

    public Material getMaterialByName(String name) {
        return dbHelper.getMaterialByName(name);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh data when fragment becomes visible
        loadMaterials();
    }
}