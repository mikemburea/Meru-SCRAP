package com.example.meruscrap;

import android.Manifest;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.meruscrap.SmartTransactionDialog;
public class Transactions extends Fragment implements
        BleScaleConnectionDialog.ScaleConnectionListener,
        BlePermissionHandler.PermissionCallback {

    private static final String TAG = "Transactions";
    // Add this field declaration to your Transactions class (with other field declarations):
    private Dialog currentDialog; // Store dialog reference for integrated buttons
    // =================================================================
    // FIELD DECLARATIONS
    // =================================================================

    // UI Components
    private MaterialCardView scaleConnectionCard, scaleReadingCard, materialSelectionCard,
            singleWeighingCard, accumulativeWeighingCard, transactionSummaryCard;
    private MaterialButton btnConnectScale, btnTare, btnDisconnect, btnAddMaterial,
            btnUseBatchMode, btnAddBatch, btnFinishAccumulating, btnCompleteTransaction;
    private Chip chipStability, chipTareStatus, chipOverload;
    private View connectionStatusIndicator, scaleNotConnected;
    private TextView tvConnectionStatus, tvWeightReading, tvSignalStrength, tvScaleCapacity, tvBatteryStatus;
    private TextView tvSelectedMaterial, tvCurrentBatchWeight, tvTotalWeight, tvTotalValue, tvBatchCount;
    private TextView tvCurrentMaterialType, tvCurrentWeight, tvCurrentValue, tvTransactionTotal, tvMaterialCount;
    private LinearLayout noMaterialsState;

    // Input fields
    private TextInputEditText etSingleManualWeight;
    private TextInputEditText etBatchManualWeight;

    // RecyclerViews
    private RecyclerView rvMaterialSelection, rvBatches, rvTransactionMaterials;

    // ✅ FIXED: Use BleScaleViewModel instead of BleScaleManager
    private BleScaleViewModel bleScaleViewModel;
    private BlePermissionHandler permissionHandler;
    private boolean isStable = false;
    private double scaleCapacity = 500.0;
    private String signalStrength = "Strong";

    // Database Integration
    private MaterialsDBHelper materialsDBHelper;
    private TransactionsDBHelper transactionsDBHelper;

    // Data Management
    private List<Material> availableMaterials;
    private TransactionMaterialsAdapter materialSelectionAdapter;
    private TransactionMaterialAdapter transactionSummaryAdapter;
    private BatchAdapter batchAdapter;
    private Material selectedMaterial;
    private List<String> usedMaterialIds;
    private List<WeighingBatch> batches;
    private List<TransactionMaterial> transactionMaterials;
    private boolean isAccumulativeMode = false;

    // Formatters
    private DecimalFormat weightFormat;
    private DecimalFormat currencyFormat;

    // MainActivity reference
    private MainActivity mainActivity;
    // Smart Dialog Management
    private SmartTransactionDialog smartDialog;
    private PrintStatusManager printStatusManager;
    // =================================================================
    // LIFECYCLE METHODS
    // =================================================================

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "=== onCreate() CALLED ===");

        weightFormat = new DecimalFormat("0.00");
        currencyFormat = new DecimalFormat("KSH #,##0.00");

        // Initialize data structures first
        availableMaterials = new ArrayList<>();
        usedMaterialIds = new ArrayList<>();
        batches = new ArrayList<>();
        transactionMaterials = new ArrayList<>();

        Log.d(TAG, "Data structures initialized");
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            mainActivity = (MainActivity) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "=== onCreateView() CALLED ===");
        return inflater.inflate(R.layout.fragment_transactions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "=== onViewCreated() CALLED ===");

        initializeViews(view);
        setupClickListeners();

        // ✅ FIXED: Initialize BLE components SAME as Home fragment
        initializeBleComponents();

        // Initialize database, BLE components AFTER views are created
        initializeDatabaseAndComponents();
        setupRecyclerViews();

        // ✅ FIXED: Observe BLE state changes
        observeBleConnectionState();

        // Load materials with a delay to ensure everything is properly initialized
        view.postDelayed(() -> {
            Log.d(TAG, "=== DELAYED MATERIAL LOADING ===");
            forceLoadMaterials();
        }, 500); // 500ms delay
    }

    // ✅ ADDED: BLE Components initialization same as Home
    private void initializeBleComponents() {
        try {
            // ✅ SAME AS BEFORE - Get shared ViewModel instance
            bleScaleViewModel = new ViewModelProvider(requireActivity()).get(BleScaleViewModel.class);
            if (getContext() != null) {
                bleScaleViewModel.initialize(getContext());
            }

            permissionHandler = new BlePermissionHandler(this, this);
            Log.d(TAG, "BLE components initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize BLE components", e);
        }
    }

    // ✅ ADDED: Observe BLE connection state same as Home
    private void observeBleConnectionState() {
        if (bleScaleViewModel == null) return;

        // Observe connection state
        bleScaleViewModel.getIsConnected().observe(getViewLifecycleOwner(), isConnected -> {
            if (isConnected != null) {
                updateUI();

                if (isConnected) {
                    Log.d(TAG, "Scale connected in Transactions fragment");
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Scale connected", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.d(TAG, "Scale disconnected in Transactions fragment");
                }
            }
        });

        // Observe weight readings
        bleScaleViewModel.getCurrentWeight().observe(getViewLifecycleOwner(), weight -> {
            if (weight != null) {
                updateWeightDisplay();
                updateStatusChips();

                if (isAccumulativeMode) {
                    updateAddBatchButton();
                } else {
                    updateAddMaterialButton();
                    updateCurrentWeightDisplay();
                }
            }
        });

        // Observe weight stability
        bleScaleViewModel.getWeightStable().observe(getViewLifecycleOwner(), stable -> {
            if (stable != null) {
                isStable = stable;
                updateStatusChips();
                updateAddMaterialButton();
            }
        });

        // Observe device name
        bleScaleViewModel.getConnectedDeviceName().observe(getViewLifecycleOwner(), deviceName -> {
            if (deviceName != null && !deviceName.isEmpty()) {
                if (tvConnectionStatus != null) {
                    tvConnectionStatus.setText("Connected - " + deviceName);
                }
            }
        });

        // Observe errors
        bleScaleViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showSnackbar("Scale Error: " + error, Snackbar.LENGTH_LONG);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "=== onResume() ===");

        // Force reload materials
        if (getView() != null) {
            getView().postDelayed(this::forceLoadMaterials, 200);
        }

        // Optional: Get transaction stats when resuming
        if (transactionsDBHelper != null) {
            getView().postDelayed(this::getTransactionStats, 1000);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "=== onPause() ===");
        // Fragment paused
    }



    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "=== onDetach() - Fragment detached from activity ===");

        try {
            mainActivity = null;
        } catch (Exception e) {
            Log.e(TAG, "Error during fragment detach cleanup", e);
        }
    }

    // =================================================================
    // INITIALIZATION METHODS
    // =================================================================

    private void initializeDatabaseAndComponents() {
        Log.d(TAG, "=== Initializing Database and Components ===");

        // Initialize database helper with proper context checking
        try {
            if (getContext() != null) {
                materialsDBHelper = MaterialsDBHelper.getInstance(getContext());
                transactionsDBHelper = TransactionsDBHelper.getInstance(getContext());
                Log.d(TAG, "Database helpers initialized successfully");
            } else {
                Log.e(TAG, "Context is null, cannot initialize database helpers");
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize database helpers: " + e.getMessage(), e);
            return;
        }

        // Initialize adapters
        try {
            materialSelectionAdapter = new TransactionMaterialsAdapter(getContext(), availableMaterials, usedMaterialIds);
            materialSelectionAdapter.setOnMaterialClickListener(new TransactionMaterialsAdapter.OnMaterialClickListener() {
                @Override
                public void onMaterialClick(Material material, int position) {
                    handleMaterialClick(material, position);
                }

                @Override
                public void onMaterialLongClick(Material material, int position) {
                    handleMaterialLongClick(material, position);
                }
            });

            transactionSummaryAdapter = new TransactionMaterialAdapter(transactionMaterials, this::removeTransactionMaterial);
            batchAdapter = new BatchAdapter(batches, this::removeBatch);

            Log.d(TAG, "All adapters initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize adapters: " + e.getMessage(), e);
        }
    }

    private void initializeViews(View view) {
        Log.d(TAG, "=== Initializing Views ===");

        // Cards
        scaleConnectionCard = view.findViewById(R.id.scale_connection_card);
        scaleReadingCard = view.findViewById(R.id.scale_reading_card);
        materialSelectionCard = view.findViewById(R.id.material_selection_card);
        singleWeighingCard = view.findViewById(R.id.single_weighing_card);
        accumulativeWeighingCard = view.findViewById(R.id.accumulative_weighing_card);
        transactionSummaryCard = view.findViewById(R.id.transaction_summary_card);

        // Connection views
        scaleNotConnected = view.findViewById(R.id.scale_not_connected);
        connectionStatusIndicator = view.findViewById(R.id.connection_status_indicator);
        tvConnectionStatus = view.findViewById(R.id.tv_connection_status);

        // Buttons
        btnConnectScale = view.findViewById(R.id.btn_connect_scale);
        btnTare = view.findViewById(R.id.btn_tare);
        btnDisconnect = view.findViewById(R.id.btn_disconnect);
        btnAddMaterial = view.findViewById(R.id.btn_add_material);
        btnUseBatchMode = view.findViewById(R.id.btn_use_batch_mode);
        btnAddBatch = view.findViewById(R.id.btn_add_batch);
        btnFinishAccumulating = view.findViewById(R.id.btn_finish_accumulating);
        btnCompleteTransaction = view.findViewById(R.id.btn_complete_transaction);

        // Chips
        chipStability = view.findViewById(R.id.chip_stability);
        chipTareStatus = view.findViewById(R.id.chip_tare_status);
        chipOverload = view.findViewById(R.id.chip_overload);

        // Text views
        tvWeightReading = view.findViewById(R.id.tv_weight_reading);
        tvSignalStrength = view.findViewById(R.id.tv_signal_strength);
        tvScaleCapacity = view.findViewById(R.id.tv_scale_capacity);
        tvBatteryStatus = view.findViewById(R.id.tv_battery_status);
        tvSelectedMaterial = view.findViewById(R.id.tv_selected_material);
        tvCurrentBatchWeight = view.findViewById(R.id.tv_current_batch_weight);
        tvTotalWeight = view.findViewById(R.id.tv_total_weight);
        tvTotalValue = view.findViewById(R.id.tv_total_value);
        tvBatchCount = view.findViewById(R.id.tv_batch_count);
        tvMaterialCount = view.findViewById(R.id.tv_material_count);
        tvCurrentMaterialType = view.findViewById(R.id.tv_current_material_type);
        tvCurrentWeight = view.findViewById(R.id.tv_current_weight);
        tvCurrentValue = view.findViewById(R.id.tv_current_value);
        tvTransactionTotal = view.findViewById(R.id.tv_transaction_total);

        // Input fields
        etSingleManualWeight = view.findViewById(R.id.et_single_manual_weight);
        etBatchManualWeight = view.findViewById(R.id.et_batch_manual_weight);

        // RecyclerViews
        rvMaterialSelection = view.findViewById(R.id.rv_material_selection);
        rvBatches = view.findViewById(R.id.rv_batches);
        rvTransactionMaterials = view.findViewById(R.id.rv_transaction_materials);

        // No materials state
        noMaterialsState = view.findViewById(R.id.no_materials_state);

        Log.d(TAG, "Views initialized - RecyclerView null check: " + (rvMaterialSelection == null));
    }

    private void setupClickListeners() {
        Log.d(TAG, "Setting up click listeners");

        // ✅ FIXED: Scale connection buttons use new methods
        btnConnectScale.setOnClickListener(v -> showBleScanDialog());
        btnTare.setOnClickListener(v -> performTare());
        btnDisconnect.setOnClickListener(v -> disconnectScale());

        // Material and transaction buttons
        btnAddMaterial.setOnClickListener(v -> addCurrentMaterial());
        btnUseBatchMode.setOnClickListener(v -> switchToBatchMode());
        btnAddBatch.setOnClickListener(v -> addCurrentBatch());
        btnFinishAccumulating.setOnClickListener(v -> finishAccumulativeWeighing());
        btnCompleteTransaction.setOnClickListener(v -> completeTransaction());

        // Debug: Add click listener to material count text to force reload
        if (tvMaterialCount != null) {
            tvMaterialCount.setOnClickListener(v -> {
                Log.d(TAG, "=== MANUAL MATERIAL RELOAD TRIGGERED ===");
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Reloading materials...", Toast.LENGTH_SHORT).show();
                }
                forceLoadMaterials();
            });
        }

        // Manual weight entry listeners
        if (etSingleManualWeight != null) {
            etSingleManualWeight.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateAddMaterialButton();
                    updateCurrentWeightDisplay();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (etBatchManualWeight != null) {
            etBatchManualWeight.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateAddBatchButton();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void setupRecyclerViews() {
        Log.d(TAG, "=== Setting up RecyclerViews ===");

        if (rvMaterialSelection != null && materialSelectionAdapter != null) {
            Log.d(TAG, "Setting up material selection RecyclerView");
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
            rvMaterialSelection.setLayoutManager(gridLayoutManager);
            rvMaterialSelection.setAdapter(materialSelectionAdapter);

            try {
                int spacing = getResources().getDimensionPixelSize(R.dimen.grid_spacing);
                rvMaterialSelection.addItemDecoration(new GridSpacingItemDecoration(2, spacing, true));
            } catch (Exception e) {
                Log.w(TAG, "Could not add grid decoration: " + e.getMessage());
            }
        } else {
            Log.e(TAG, "Cannot setup material selection RecyclerView - null components");
        }

        if (rvBatches != null && batchAdapter != null) {
            rvBatches.setLayoutManager(new LinearLayoutManager(getContext()));
            rvBatches.setAdapter(batchAdapter);
        }

        if (rvTransactionMaterials != null && transactionSummaryAdapter != null) {
            rvTransactionMaterials.setLayoutManager(new LinearLayoutManager(getContext()));
            rvTransactionMaterials.setAdapter(transactionSummaryAdapter);
        }
    }

    // =================================================================
    // ✅ FIXED: SCALE CONNECTION METHODS - Same as Home fragment
    // =================================================================

    private void showBleScanDialog() {
        if (permissionHandler != null && !permissionHandler.hasAllPermissions()) {
            permissionHandler.requestPermissions();
            return;
        }

        showScaleConnectionDialog();
    }

    private void showScaleConnectionDialog() {
        if (getContext() == null || !isAdded()) {
            Log.w(TAG, "Cannot show scale connection dialog - fragment not attached");
            return;
        }

        if (bleScaleViewModel == null) {
            Log.e(TAG, "BleScaleViewModel is null, cannot show connection dialog");
            showSnackbar("Scale service not available", Snackbar.LENGTH_SHORT);
            return;
        }

        try {
            // ✅ FIXED: Use BleScaleConnectionDialog (same as Home)
            BleScaleConnectionDialog dialog = new BleScaleConnectionDialog();

            // Set the BLE scale view model
            dialog.setBleScaleViewModel(bleScaleViewModel);

            // Set the connection listener
            dialog.setScaleConnectionListener(this);

            // Show the dialog
            dialog.show(getParentFragmentManager(), "scale_connection");

            Log.d(TAG, "Scale connection dialog shown");

        } catch (Exception e) {
            Log.e(TAG, "Error showing scale connection dialog", e);
            showSnackbar("Error showing connection dialog: " + e.getMessage(), Snackbar.LENGTH_LONG);
        }
    }

    private void performTare() {
        if (bleScaleViewModel != null && bleScaleViewModel.isConnectedValue()) {
            bleScaleViewModel.tare();

            if (getContext() != null) {
                showSnackbar("Scale tared (zeroed)", Snackbar.LENGTH_SHORT);
                Toast.makeText(getContext(), "⚖️ Scale Tared", Toast.LENGTH_SHORT).show();
            }

            Log.d(TAG, "Scale tare command sent");
        } else {
            if (getContext() != null) {
                showSnackbar("Cannot tare - scale not connected", Snackbar.LENGTH_SHORT);
            }
            Log.w(TAG, "Cannot tare - scale not connected");
        }
    }

    private void disconnectScale() {
        if (bleScaleViewModel != null) {
            String deviceName = getConnectedScaleDeviceName();

            bleScaleViewModel.disconnect();

            if (getContext() != null) {
                String message = deviceName != null ?
                        "Disconnected from " + deviceName :
                        "Scale disconnected";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }

            Log.d(TAG, "Scale disconnection initiated");
        } else {
            Log.w(TAG, "Cannot disconnect - bleScaleViewModel is null");
        }
    }

    private String getConnectedScaleDeviceName() {
        if (bleScaleViewModel != null && bleScaleViewModel.isConnectedValue()) {
            try {
                String deviceName = bleScaleViewModel.getConnectedDeviceName().getValue();
                return deviceName != null && !deviceName.isEmpty() ? deviceName : "BLE Scale";
            } catch (Exception e) {
                Log.w(TAG, "Error getting connected device name: " + e.getMessage());
                return "BLE Scale";
            }
        }
        return null;
    }

    public boolean isScaleConnectionAvailable() {
        return bleScaleViewModel != null && permissionHandler != null && permissionHandler.hasAllPermissions();
    }



    private void updateWeightDisplay() {
        double currentWeight = bleScaleViewModel != null ? bleScaleViewModel.getCurrentWeightValue() : 0.0;
        if (tvWeightReading != null) {
            tvWeightReading.setText(weightFormat.format(currentWeight));
        }
        if (tvCurrentBatchWeight != null) {
            tvCurrentBatchWeight.setText(weightFormat.format(currentWeight) + " kg");
        }

        if (tvScaleCapacity != null) {
            tvScaleCapacity.setText(weightFormat.format(scaleCapacity) + " kg");
        }
        if (tvBatteryStatus != null) {
            tvBatteryStatus.setText("Good");
        }
        if (tvSignalStrength != null) {
            tvSignalStrength.setText(signalStrength);
        }
    }

    private void updateStatusChips() {
        // Check if fragment is properly attached before updating chips
        if (getContext() == null || !isAdded()) {
            Log.w(TAG, "Cannot update status chips - fragment not properly attached");
            return;
        }

        try {
            boolean isConnected = bleScaleViewModel != null && bleScaleViewModel.isConnectedValue();
            double currentWeight = bleScaleViewModel != null ? bleScaleViewModel.getCurrentWeightValue() : 0.0;
            boolean isOverloaded = currentWeight > scaleCapacity;

            if (chipStability != null && isConnected) {
                if (isStable) {
                    chipStability.setText("Stable");
                    chipStability.setTextColor(ContextCompat.getColor(getContext(), R.color.success));
                    chipStability.setChipBackgroundColorResource(R.color.success_light);
                } else {
                    chipStability.setText("Stabilizing");
                    chipStability.setTextColor(ContextCompat.getColor(getContext(), R.color.warning));
                    chipStability.setChipBackgroundColorResource(R.color.warning_light);
                }
            }

            if (chipTareStatus != null) {
                chipTareStatus.setVisibility(View.GONE);
            }

            if (chipOverload != null) {
                chipOverload.setVisibility(isOverloaded ? View.VISIBLE : View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating status chips", e);
        }
    }

    // =================================================================
    // MATERIAL HANDLING METHODS (Keep existing implementation)
    // =================================================================

    private void forceLoadMaterials() {
        Log.d(TAG, "=== FORCE LOADING MATERIALS ===");

        // Comprehensive null checks
        if (getContext() == null) {
            Log.e(TAG, "Context is null");
            return;
        }

        if (materialsDBHelper == null) {
            Log.e(TAG, "Database helper is null, attempting to reinitialize");
            try {
                materialsDBHelper = MaterialsDBHelper.getInstance(getContext());
            } catch (Exception e) {
                Log.e(TAG, "Failed to reinitialize database helper", e);
                return;
            }
        }

        if (materialSelectionAdapter == null) {
            Log.e(TAG, "Material selection adapter is null");
            return;
        }

        try {
            // Clear existing materials
            availableMaterials.clear();
            Log.d(TAG, "Cleared existing materials");

            // Get direct count from database
            int dbCount = materialsDBHelper.getMaterialsCount();
            Log.d(TAG, "Database reports " + dbCount + " materials");

            if (dbCount == 0) {
                Log.i(TAG, "Database is empty, creating default materials");
                createDefaultMaterials();
                dbCount = materialsDBHelper.getMaterialsCount();
                Log.d(TAG, "After creating defaults, database has " + dbCount + " materials");
            }

            // Load materials from database
            List<Material> dbMaterials = materialsDBHelper.getAllMaterials();
            Log.d(TAG, "getAllMaterials() returned: " + (dbMaterials != null ? dbMaterials.size() : "NULL"));

            if (dbMaterials != null && !dbMaterials.isEmpty()) {
                availableMaterials.addAll(dbMaterials);
                Log.d(TAG, "Added " + dbMaterials.size() + " materials to list");

                // Log first few materials
                for (int i = 0; i < Math.min(3, availableMaterials.size()); i++) {
                    Material m = availableMaterials.get(i);
                    Log.d(TAG, "Material " + i + ": " + m.getName() + " - ID: " + m.getId());
                }

                // Force update adapter
                materialSelectionAdapter.updateMaterials(availableMaterials);
                materialSelectionAdapter.updateUsedMaterials(usedMaterialIds);
                materialSelectionAdapter.notifyDataSetChanged();

                Log.d(TAG, "Adapter updated - item count: " + materialSelectionAdapter.getItemCount());

                // Update UI
                hideNoMaterialsState();
                updateMaterialCount();

                // FORCE SHOW MATERIAL SELECTION CARD
                if (materialSelectionCard != null) {
                    materialSelectionCard.setVisibility(View.VISIBLE);
                    Log.d(TAG, "Material selection card set to VISIBLE");
                }

            } else {
                Log.w(TAG, "No materials loaded from database");
                showNoMaterialsState();
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception in forceLoadMaterials", e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error loading materials: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            showNoMaterialsState();
        }

        // Force UI update
        updateUI();
        verifyAdapterAfterUpdate();
    }

    private void createDefaultMaterials() {
        Log.d(TAG, "=== Creating Default Materials ===");

        try {
            Material[] defaultMaterials = {
                    new Material("Steel", 450.0, "🔩", "Common construction and automotive steel"),
                    new Material("Aluminum", 280.0, "🥫", "Lightweight metal from cans and sheets"),
                    new Material("Copper", 1200.0, "🔶", "High-value metal from wires and pipes"),
                    new Material("Brass", 650.0, "🟨", "Alloy metal from fittings and decorations"),
                    new Material("Iron", 320.0, "⚫", "Heavy ferrous metal, common in machinery"),
                    new Material("Lead", 380.0, "🔘", "Dense metal from batteries and roofing")
            };

            int successCount = 0;
            for (Material material : defaultMaterials) {
                try {
                    long result = materialsDBHelper.insertMaterial(material);
                    if (result > 0) {
                        successCount++;
                        Log.d(TAG, "Created: " + material.getName() + " (ID: " + result + ")");
                    } else {
                        Log.w(TAG, "Failed to create: " + material.getName());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error creating material " + material.getName(), e);
                }
            }

            Log.d(TAG, "Successfully created " + successCount + " default materials");
            if (getContext() != null) {
                Toast.makeText(getContext(), "Created " + successCount + " default materials", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in createDefaultMaterials", e);
        }
    }

    private void handleMaterialClick(Material material, int position) {
        Log.d(TAG, "Material clicked: " + material.getName());
        if (isMaterialUsedInTransaction(material)) {
            if (getContext() != null) {
                Toast.makeText(getContext(),
                        material.getName() + " is already in this transaction",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        selectMaterial(material);
    }

    private void handleMaterialLongClick(Material material, int position) {
        Log.d(TAG, "Material long clicked: " + material.getName());
        if (getContext() != null) {
            Toast.makeText(getContext(),
                    material.getName() + " - " + material.getFormattedPrice() + "/kg\n" +
                            (material.getDescription() != null ? material.getDescription() : "No description"),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean isMaterialUsedInTransaction(Material material) {
        return findExistingTransactionMaterial(material.getName()) != null;
    }

    private void markMaterialAsUsed(Material material) {
        String materialId = String.valueOf(material.getId());
        if (!usedMaterialIds.contains(materialId)) {
            usedMaterialIds.add(materialId);
            if (materialSelectionAdapter != null) {
                materialSelectionAdapter.updateUsedMaterials(usedMaterialIds);
            }
            updateMaterialCount();
            Log.d(TAG, "Marked material as used: " + material.getName());
        }
    }

    private void markMaterialAsUnused(String materialName) {
        for (Material material : availableMaterials) {
            if (material.getName().equals(materialName)) {
                String materialId = String.valueOf(material.getId());
                usedMaterialIds.remove(materialId);
                if (materialSelectionAdapter != null) {
                    materialSelectionAdapter.updateUsedMaterials(usedMaterialIds);
                }
                updateMaterialCount();
                Log.d(TAG, "Marked material as unused: " + materialName);
                break;
            }
        }
    }

    private void selectMaterial(Material material) {
        selectedMaterial = material;
        Log.d(TAG, "Selected material: " + material.getName());

        if (tvCurrentMaterialType != null) {
            tvCurrentMaterialType.setText(material.getName() + " - " + material.getFormattedPrice() + "/kg");
        }

        if (materialSelectionCard != null) {
            materialSelectionCard.setVisibility(View.GONE);
        }
        if (singleWeighingCard != null) {
            singleWeighingCard.setVisibility(View.VISIBLE);
        }

        updateAddMaterialButton();
        updateCurrentWeightDisplay();
    }

    private void updateMaterialCount() {
        if (tvMaterialCount != null) {
            int totalMaterials = availableMaterials.size();
            int availableCount = materialSelectionAdapter != null ?
                    materialSelectionAdapter.getAvailableMaterialsCount() : totalMaterials;

            String countText = totalMaterials == 0 ?
                    "No materials configured" :
                    availableCount + " of " + totalMaterials + " materials available";

            tvMaterialCount.setText(countText);
            Log.d(TAG, "Material count updated: " + countText);
        }
    }

    private void showNoMaterialsState() {
        Log.d(TAG, "=== SHOWING NO MATERIALS STATE ===");
        if (noMaterialsState != null && rvMaterialSelection != null) {
            noMaterialsState.setVisibility(View.VISIBLE);
            rvMaterialSelection.setVisibility(View.GONE);
            Log.d(TAG, "No materials state is now visible");
        }
    }

    private void hideNoMaterialsState() {
        Log.d(TAG, "=== HIDING NO MATERIALS STATE ===");
        if (noMaterialsState != null && rvMaterialSelection != null) {
            noMaterialsState.setVisibility(View.GONE);
            rvMaterialSelection.setVisibility(View.VISIBLE);
            Log.d(TAG, "Materials RecyclerView is now visible");
        }
    }

    // =================================================================
    // UI UPDATE METHODS
    // =================================================================

    private void updateUI() {
        Log.d(TAG, "=== UPDATING UI ===");

        boolean isScaleConnected = bleScaleViewModel != null && bleScaleViewModel.isConnectedValue();
        Log.d(TAG, "Scale connected: " + isScaleConnected);
        Log.d(TAG, "Available materials: " + availableMaterials.size());

        if (isScaleConnected) {
            // Scale is connected - show connected state
            if (scaleNotConnected != null) scaleNotConnected.setVisibility(View.GONE);
            if (scaleReadingCard != null) scaleReadingCard.setVisibility(View.VISIBLE);

            // Update connection status indicator
            if (connectionStatusIndicator != null) {
                connectionStatusIndicator.setBackgroundResource(android.R.drawable.presence_online);
            }
            if (tvConnectionStatus != null) {
                String deviceName = bleScaleViewModel.getConnectedDeviceName().getValue();
                tvConnectionStatus.setText("Connected" + (deviceName != null ? " - " + deviceName : ""));
            }

            // Update scale reading display
            updateWeightDisplay();
            updateStatusChips();

            Log.d(TAG, "Scale UI updated - connected state");
        } else {
            // Scale is not connected - show disconnected state
            if (scaleNotConnected != null) scaleNotConnected.setVisibility(View.VISIBLE);
            if (scaleReadingCard != null) scaleReadingCard.setVisibility(View.GONE);

            // Hide weighing cards when disconnected
            if (accumulativeWeighingCard != null) accumulativeWeighingCard.setVisibility(View.GONE);

            // Update connection status indicator
            if (connectionStatusIndicator != null) {
                connectionStatusIndicator.setBackgroundResource(android.R.drawable.presence_offline);
            }
            if (tvConnectionStatus != null) tvConnectionStatus.setText("Not Connected");

            Log.d(TAG, "Scale UI updated - disconnected state");
        }

        // ALWAYS show material selection if we have materials (regardless of scale connection)
        if (materialSelectionCard != null && availableMaterials.size() > 0) {
            materialSelectionCard.setVisibility(View.VISIBLE);
            Log.d(TAG, "Material selection card shown (has materials)");
        } else {
            if (materialSelectionCard != null) materialSelectionCard.setVisibility(View.GONE);
            Log.d(TAG, "Material selection card hidden (no materials)");
        }

        // Show/hide transaction summary
        if (transactionSummaryCard != null) {
            transactionSummaryCard.setVisibility(transactionMaterials.size() > 0 ? View.VISIBLE : View.GONE);
        }

        // Show/hide complete transaction button
        if (btnCompleteTransaction != null) {
            btnCompleteTransaction.setVisibility(transactionMaterials.size() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void updateAddMaterialButton() {
        if (btnAddMaterial == null || selectedMaterial == null) {
            if (btnAddMaterial != null) {
                btnAddMaterial.setEnabled(false);
                btnAddMaterial.setText("Add Material");
                if (btnUseBatchMode != null) {
                    btnUseBatchMode.setVisibility(View.GONE);
                }
            }
            return;
        }

        double currentWeight = bleScaleViewModel != null ? bleScaleViewModel.getCurrentWeightValue() : 0.0;
        String manualText = etSingleManualWeight != null ? etSingleManualWeight.getText().toString().trim() : "";
        boolean hasWeight = currentWeight > 0 || !manualText.isEmpty();

        boolean isScaleConnected = bleScaleViewModel != null && bleScaleViewModel.isConnectedValue();
        boolean canAdd = false;

        if (hasWeight) {
            if (!manualText.isEmpty()) {
                try {
                    double manualWeight = Double.parseDouble(manualText);
                    canAdd = manualWeight > 0;
                } catch (NumberFormatException e) {
                    canAdd = false;
                }
            } else if (isScaleConnected) {
                canAdd = isStable || currentWeight > 0.1;
            }
        }

        btnAddMaterial.setEnabled(canAdd);

        if (hasWeight && canAdd) {
            double weight = getCurrentWeight();
            double value = weight * selectedMaterial.getPricePerKg();

            String stabilityIndicator = "";
            if (isScaleConnected && manualText.isEmpty()) {
                stabilityIndicator = isStable ? " ✓" : " ~";
            }

            String buttonText = "Add " + selectedMaterial.getName() + " (" +
                    weightFormat.format(weight) + " kg - " +
                    currencyFormat.format(value) + ")" + stabilityIndicator;
            btnAddMaterial.setText(buttonText);

            if (btnUseBatchMode != null) {
                btnUseBatchMode.setVisibility(View.VISIBLE);
                btnUseBatchMode.setText("Batch Mode");
            }
        } else {
            String buttonText = "Add Material";
            if (!hasWeight) {
                buttonText = "Add Material (No Weight)";
            } else if (!canAdd && isScaleConnected && manualText.isEmpty()) {
                buttonText = "Add Material (Stabilizing...)";
            }
            btnAddMaterial.setText(buttonText);

            if (btnUseBatchMode != null && selectedMaterial != null) {
                btnUseBatchMode.setVisibility(View.VISIBLE);
                btnUseBatchMode.setText("Batch Mode");
            }
        }
    }

    private void updateCurrentWeightDisplay() {
        if (tvCurrentWeight == null || selectedMaterial == null) {
            if (tvCurrentWeight != null) {
                tvCurrentWeight.setText("0.00 kg");
            }
            if (tvCurrentValue != null) {
                tvCurrentValue.setText("KSH 0.00");
            }
            return;
        }

        double weight = getCurrentWeight();
        double value = weight * selectedMaterial.getPricePerKg();

        tvCurrentWeight.setText(weightFormat.format(weight) + " kg");
        if (tvCurrentValue != null) {
            tvCurrentValue.setText(currencyFormat.format(value));
        }
    }

    private double getCurrentWeight() {
        if (etSingleManualWeight != null && !isAccumulativeMode) {
            String manualText = etSingleManualWeight.getText().toString().trim();
            if (!manualText.isEmpty()) {
                try {
                    return Math.max(0.0, Double.parseDouble(manualText));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid manual weight entry: " + manualText);
                }
            }
        }
        return bleScaleViewModel != null ? bleScaleViewModel.getCurrentWeightValue() : 0.0;
    }

    // =================================================================
    // TRANSACTION PROCESSING METHODS (Keep all existing methods)
    // =================================================================

    // Also check when adding materials (optional - for stricter control)
    private void addCurrentMaterial() {
        double weight = getCurrentWeight();
        if (weight <= 0 || selectedMaterial == null) return;

        // CHECK LICENSE HERE (optional - you might allow adding but not completing)
        if (!LicenseChecker.checkLicense(getContext(), "add materials to transaction")) {
            return;
        }

        Log.d(TAG, "Adding material: " + selectedMaterial.getName() + ", weight: " + weight);

        // Check if this material already exists in transaction
        TransactionMaterial existingMaterial = findExistingTransactionMaterial(selectedMaterial.getName());
        if (existingMaterial != null) {
            // Combine with existing material
            int existingIndex = transactionMaterials.indexOf(existingMaterial);
            TransactionMaterial combinedMaterial = new TransactionMaterial(
                    selectedMaterial.getName(),
                    existingMaterial.getWeight() + weight,
                    selectedMaterial.getPricePerKg(),
                    System.currentTimeMillis()
            );
            transactionMaterials.set(existingIndex, combinedMaterial);
            if (transactionSummaryAdapter != null) {
                transactionSummaryAdapter.notifyItemChanged(existingIndex);
            }

            if (getContext() != null) {
                Toast.makeText(getContext(),
                        "Added " + weightFormat.format(weight) + " kg to existing " + selectedMaterial.getName() +
                                " (Total: " + weightFormat.format(combinedMaterial.getWeight()) + " kg)",
                        Toast.LENGTH_LONG).show();
            }
        } else {
            // Create new transaction material
            TransactionMaterial transactionMaterial = new TransactionMaterial(
                    selectedMaterial.getName(),
                    weight,
                    selectedMaterial.getPricePerKg(),
                    System.currentTimeMillis()
            );

            transactionMaterials.add(transactionMaterial);
            if (transactionSummaryAdapter != null) {
                transactionSummaryAdapter.notifyItemInserted(transactionMaterials.size() - 1);
            }

            // Mark material as used
            markMaterialAsUsed(selectedMaterial);

            if (getContext() != null) {
                Toast.makeText(getContext(),
                        selectedMaterial.getName() + " added: " + weightFormat.format(weight) + " kg",
                        Toast.LENGTH_SHORT).show();
            }
        }

        // Clear manual input
        if (etSingleManualWeight != null) {
            etSingleManualWeight.setText("");
        }

        resetForNextMaterial();
        updateTransactionSummary();
    }

    private TransactionMaterial findExistingTransactionMaterial(String materialName) {
        for (TransactionMaterial material : transactionMaterials) {
            if (material.getMaterialName().equals(materialName)) {
                return material;
            }
        }
        return null;
    }

    private void resetForNextMaterial() {
        selectedMaterial = null;

        if (singleWeighingCard != null) {
            singleWeighingCard.setVisibility(View.GONE);
        }
        if (materialSelectionCard != null) {
            materialSelectionCard.setVisibility(View.VISIBLE);
        }
        if (transactionSummaryCard != null) {
            transactionSummaryCard.setVisibility(transactionMaterials.size() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void removeTransactionMaterial(int position) {
        if (position >= 0 && position < transactionMaterials.size()) {
            TransactionMaterial removed = transactionMaterials.get(position);
            transactionMaterials.remove(position);
            if (transactionSummaryAdapter != null) {
                transactionSummaryAdapter.notifyItemRemoved(position);
            }

            markMaterialAsUnused(removed.getMaterialName());

            if (getContext() != null) {
                Toast.makeText(getContext(),
                        removed.getMaterialName() + " removed from transaction",
                        Toast.LENGTH_SHORT).show();
            }

            updateTransactionSummary();

            if (transactionMaterials.isEmpty() && transactionSummaryCard != null) {
                transactionSummaryCard.setVisibility(View.GONE);
            }
        }
    }

    private void updateTransactionSummary() {
        if (tvTransactionTotal == null) return;

        double totalWeight = 0;
        double totalValue = 0;

        for (TransactionMaterial material : transactionMaterials) {
            totalWeight += material.getWeight();
            totalValue += material.getValue();
        }

        tvTransactionTotal.setText(
                transactionMaterials.size() + " materials • " +
                        weightFormat.format(totalWeight) + " kg • " +
                        currencyFormat.format(totalValue)
        );

        if (btnCompleteTransaction != null) {
            btnCompleteTransaction.setVisibility(transactionMaterials.size() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    // =================================================================
    // BATCH MODE METHODS (Keep all existing methods)
    // =================================================================

    private void switchToBatchMode() {
        if (selectedMaterial == null) return;

        isAccumulativeMode = true;
        Log.d(TAG, "Switched to batch mode for: " + selectedMaterial.getName());

        if (tvSelectedMaterial != null) {
            tvSelectedMaterial.setText(selectedMaterial.getName() + " - " + selectedMaterial.getFormattedPrice() + "/kg");
        }

        if (getContext() != null) {
            Toast.makeText(getContext(),
                    "Batch mode enabled for " + selectedMaterial.getName() + ". Add multiple pieces separately.",
                    Toast.LENGTH_LONG).show();
        }

        if (singleWeighingCard != null) {
            singleWeighingCard.setVisibility(View.GONE);
        }
        if (accumulativeWeighingCard != null) {
            accumulativeWeighingCard.setVisibility(View.VISIBLE);
        }

        updateAddBatchButton();
    }

    private void updateAddBatchButton() {
        if (btnAddBatch == null || selectedMaterial == null) {
            if (btnAddBatch != null) {
                btnAddBatch.setEnabled(false);
                btnAddBatch.setText("Add Batch");
            }
            return;
        }

        double currentWeight = bleScaleViewModel != null ? bleScaleViewModel.getCurrentWeightValue() : 0.0;
        String manualText = etBatchManualWeight != null ? etBatchManualWeight.getText().toString().trim() : "";
        boolean hasWeight = currentWeight > 0 || !manualText.isEmpty();

        btnAddBatch.setEnabled(hasWeight);

        if (hasWeight) {
            btnAddBatch.setText("Add Batch (" + weightFormat.format(getCurrentBatchWeight()) + " kg)");
        } else {
            btnAddBatch.setText("Add Batch");
        }
    }

    private double getCurrentBatchWeight() {
        if (etBatchManualWeight != null) {
            String manualText = etBatchManualWeight.getText().toString().trim();
            if (!manualText.isEmpty()) {
                try {
                    return Double.parseDouble(manualText);
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            }
        }
        return bleScaleViewModel != null ? bleScaleViewModel.getCurrentWeightValue() : 0.0;
    }

    private void addCurrentBatch() {
        double weight = getCurrentBatchWeight();
        if (weight <= 0 || selectedMaterial == null) return;

        WeighingBatch batch = new WeighingBatch(
                System.currentTimeMillis(),
                weight,
                selectedMaterial.getPricePerKg(),
                selectedMaterial.getName()
        );

        batches.add(batch);
        if (batchAdapter != null) {
            batchAdapter.notifyItemInserted(batches.size() - 1);
        }

        if (etBatchManualWeight != null) {
            etBatchManualWeight.setText("");
        }

        updateBatchSummary();

        if (getContext() != null) {
            Toast.makeText(getContext(), "Batch added: " + weightFormat.format(weight) + " kg", Toast.LENGTH_SHORT).show();
        }

        if (batches.size() == 1 && rvBatches != null) {
            rvBatches.setVisibility(View.VISIBLE);
        }
    }

    private void removeBatch(int position) {
        if (position >= 0 && position < batches.size()) {
            batches.remove(position);
            if (batchAdapter != null) {
                batchAdapter.notifyItemRemoved(position);
            }
            updateBatchSummary();

            if (batches.isEmpty() && rvBatches != null) {
                rvBatches.setVisibility(View.GONE);
            }
        }
    }

    private void updateBatchSummary() {
        double totalWeight = 0;
        double totalValue = 0;

        for (WeighingBatch batch : batches) {
            totalWeight += batch.getWeight();
            totalValue += batch.getValue();
        }

        if (tvTotalWeight != null) tvTotalWeight.setText(weightFormat.format(totalWeight) + " kg");
        if (tvTotalValue != null) tvTotalValue.setText(currencyFormat.format(totalValue));
        if (tvBatchCount != null) tvBatchCount.setText(String.valueOf(batches.size()));
        if (tvCurrentBatchWeight != null) {
            tvCurrentBatchWeight.setText(weightFormat.format(getCurrentBatchWeight()) + " kg");
        }
    }

    private void finishAccumulativeWeighing() {
        if (batches.isEmpty()) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "No batches added", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        double totalBatchWeight = 0;
        double totalBatchValue = 0;
        String materialName = selectedMaterial.getName();

        for (WeighingBatch batch : batches) {
            totalBatchWeight += batch.getWeight();
            totalBatchValue += batch.getValue();
        }

        TransactionMaterial existingMaterial = findExistingTransactionMaterial(materialName);
        if (existingMaterial != null) {
            int existingIndex = transactionMaterials.indexOf(existingMaterial);
            TransactionMaterial combinedMaterial = new TransactionMaterial(
                    materialName,
                    existingMaterial.getWeight() + totalBatchWeight,
                    selectedMaterial.getPricePerKg(),
                    System.currentTimeMillis()
            );
            transactionMaterials.set(existingIndex, combinedMaterial);
            if (transactionSummaryAdapter != null) {
                transactionSummaryAdapter.notifyItemChanged(existingIndex);
            }

            if (getContext() != null) {
                Toast.makeText(getContext(),
                        "Added " + weightFormat.format(totalBatchWeight) + " kg to existing " + materialName +
                                " (Total: " + weightFormat.format(combinedMaterial.getWeight()) + " kg)",
                        Toast.LENGTH_LONG).show();
            }
        } else {
            TransactionMaterial batchTransactionMaterial = new TransactionMaterial(
                    materialName,
                    totalBatchWeight,
                    selectedMaterial.getPricePerKg(),
                    System.currentTimeMillis()
            );
            transactionMaterials.add(batchTransactionMaterial);
            if (transactionSummaryAdapter != null) {
                transactionSummaryAdapter.notifyItemInserted(transactionMaterials.size() - 1);
            }

            markMaterialAsUsed(selectedMaterial);

            if (getContext() != null) {
                Toast.makeText(getContext(),
                        "Batch completed: " + materialName + " - " +
                                weightFormat.format(totalBatchWeight) + " kg (" +
                                currencyFormat.format(totalBatchValue) + ")",
                        Toast.LENGTH_LONG).show();
            }
        }

        batches.clear();
        if (batchAdapter != null) {
            batchAdapter.notifyDataSetChanged();
        }

        isAccumulativeMode = false;
        selectedMaterial = null;

        if (accumulativeWeighingCard != null) {
            accumulativeWeighingCard.setVisibility(View.GONE);
        }
        if (materialSelectionCard != null) {
            materialSelectionCard.setVisibility(View.VISIBLE);
        }
        if (transactionSummaryCard != null) {
            transactionSummaryCard.setVisibility(View.VISIBLE);
        }
        updateTransactionSummary();
        updateUI();
    }

    // =================================================================
    // TRANSACTION COMPLETION METHODS (Keep all existing methods)
    // =================================================================

    // Check before completing a transaction
    private void completeTransaction() {
        if (transactionMaterials.isEmpty()) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "No materials to complete", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // CHECK LICENSE HERE
        if (!LicenseChecker.checkLicense(getContext(), "complete transactions")) {
            return;
        }

        // Show preview dialog instead of immediately completing
        showTransactionPreviewDialog();
    }

    // ==============================================================
// SOLUTION 1: Fix the showTransactionPreviewDialog method
// ==============================================================

    private void showTransactionPreviewDialog() {
        if (getContext() == null || !isAdded()) {
            Log.w(TAG, "Cannot show preview dialog - fragment not attached");
            return;
        }

        // 1. CRITICAL: Dismiss and cleanup any existing dialog completely
        if (currentDialog != null) {
            try {
                if (currentDialog.isShowing()) {
                    currentDialog.dismiss();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error dismissing previous dialog", e);
            } finally {
                currentDialog = null;
            }
        }

        // 2. Wait for cleanup to complete before creating new dialog
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            createAndShowNewDialog();
        }, 100); // Small delay ensures cleanup is complete
    }

    private void createAndShowNewDialog() {
        if (getContext() == null || !isAdded()) return;

        try {
            // Calculate totals and create summary
            double totalWeight = 0;
            double totalValue = 0;
            Map<String, TransactionMaterialSummary> materialSummary = new HashMap<>();

            for (TransactionMaterial material : transactionMaterials) {
                totalWeight += material.getWeight();
                totalValue += material.getValue();

                String materialName = material.getMaterialName();
                if (materialSummary.containsKey(materialName)) {
                    TransactionMaterialSummary existing = materialSummary.get(materialName);
                    existing.weight += material.getWeight();
                    existing.value += material.getValue();
                } else {
                    materialSummary.put(materialName, new TransactionMaterialSummary(
                            materialName,
                            material.getWeight(),
                            material.getPricePerKg(),
                            material.getValue()
                    ));
                }
            }

            // Create completely fresh dialog layout
            View dialogView = createEnhancedDialogLayout(materialSummary, totalWeight, totalValue);

            // Create and configure dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();
            currentDialog = dialog;

            // Configure dialog window
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().setGravity(Gravity.CENTER);

                WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
                params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                params.y = 0;
                dialog.getWindow().setAttributes(params);

                dialog.getWindow().setDimAmount(0.65f);
                dialog.getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                );
            }

            showDialogWithAnimation(dialog);
            Log.d(TAG, "Enhanced transaction preview dialog shown");

        } catch (Exception e) {
            Log.e(TAG, "Error creating dialog", e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error showing dialog: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    // ==============================================================
// SOLUTION 5: Add proper cleanup on dialog dismiss
// ==============================================================

    private void showDialogWithAnimation(AlertDialog dialog) {
        try {
            dialog.show();

            // Set dismiss listener to cleanup properly
            dialog.setOnDismissListener(d -> {
                currentDialog = null;
                Log.d(TAG, "Dialog dismissed and reference cleared");
            });

            // Add fade-in animation
            if (dialog.getWindow() != null && dialog.getWindow().getDecorView() != null) {
                View decorView = dialog.getWindow().getDecorView();
                decorView.setAlpha(0f);
                decorView.setScaleX(0.9f);
                decorView.setScaleY(0.9f);

                decorView.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing dialog with animation", e);
            currentDialog = null;
        }
    }
    private View createEnhancedDialogLayout(Map<String, TransactionMaterialSummary> materialSummary,
                                            double totalWeight, double totalValue) {

        // Create main container with enhanced rounded background and elevation
        LinearLayout mainContainer = new LinearLayout(getContext());
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setPadding(20, 16, 20, 0); // No bottom padding - buttons will handle it

        // Apply custom background with rounded corners and elevation
        mainContainer.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.dialog_background));

        // Add subtle elevation programmatically (additional to drawable shadow)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            mainContainer.setElevation(12f);
            mainContainer.setTranslationZ(4f);
        }

        // Header section with icon and title
        LinearLayout headerLayout = createDialogHeader();
        mainContainer.addView(headerLayout);

        // Transaction info summary (compact)
        LinearLayout infoLayout = createTransactionInfoSummary(totalWeight, totalValue);
        mainContainer.addView(infoLayout);

        // Materials breakdown (scrollable, compact)
        View materialsSection = createMaterialsBreakdown(materialSummary);
        mainContainer.addView(materialsSection);

        // Total summary footer
        View totalSummary = createTotalSummaryFooter(totalWeight, totalValue);
        mainContainer.addView(totalSummary);

        // Add integrated custom buttons
        LinearLayout buttonsLayout = createIntegratedButtons(materialSummary, totalWeight, totalValue);
        mainContainer.addView(buttonsLayout);

        return mainContainer;
    }

    private LinearLayout createDialogHeader() {
        LinearLayout headerLayout = new LinearLayout(getContext());
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(0, 0, 0, 12); // Reduced padding

        // Transaction icon
        ImageView iconView = new ImageView(getContext());
        iconView.setImageResource(R.drawable.ic_scale_24);
        iconView.setColorFilter(ContextCompat.getColor(getContext(), R.color.primary));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(32, 32);
        iconParams.setMarginEnd(12);
        iconView.setLayoutParams(iconParams);

        // Title and subtitle
        LinearLayout textLayout = new LinearLayout(getContext());
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView titleView = new TextView(getContext());
        titleView.setText("Transaction Preview");
        titleView.setTextSize(18); // Reduced from 20
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        titleView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));

        TextView subtitleView = new TextView(getContext());
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        subtitleView.setText("📅 " + dateFormat.format(new Date()));
        subtitleView.setTextSize(12);
        subtitleView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        subtitleView.setPadding(0, 2, 0, 0);

        textLayout.addView(titleView);
        textLayout.addView(subtitleView);

        headerLayout.addView(iconView);
        headerLayout.addView(textLayout);

        return headerLayout;
    }

    private LinearLayout createTransactionInfoSummary(double totalWeight, double totalValue) {
        LinearLayout infoLayout = new LinearLayout(getContext());
        infoLayout.setOrientation(LinearLayout.HORIZONTAL);
        infoLayout.setPadding(12, 8, 12, 8); // Compact padding
        infoLayout.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.transaction_info_background));

        // Materials count
        LinearLayout materialsInfo = createInfoItem("📦", String.valueOf(transactionMaterials.size()),
                transactionMaterials.size() == 1 ? "Material" : "Materials");

        // Weight info
        LinearLayout weightInfo = createInfoItem("⚖️", weightFormat.format(totalWeight) + " kg", "Total Weight");

        // Value info
        LinearLayout valueInfo = createInfoItem("💰", currencyFormat.format(totalValue), "Total Value");

        // Add dividers between items
        infoLayout.addView(materialsInfo);
        infoLayout.addView(createVerticalDivider());
        infoLayout.addView(weightInfo);
        infoLayout.addView(createVerticalDivider());
        infoLayout.addView(valueInfo);

        return infoLayout;
    }

    private LinearLayout createInfoItem(String emoji, String value, String label) {
        LinearLayout itemLayout = new LinearLayout(getContext());
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setGravity(Gravity.CENTER);
        itemLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        itemLayout.setPadding(4, 0, 4, 0);

        TextView emojiView = new TextView(getContext());
        emojiView.setText(emoji);
        emojiView.setTextSize(16);
        emojiView.setGravity(Gravity.CENTER);

        TextView valueView = new TextView(getContext());
        valueView.setText(value);
        valueView.setTextSize(14);
        valueView.setTypeface(valueView.getTypeface(), Typeface.BOLD);
        valueView.setTextColor(ContextCompat.getColor(getContext(), R.color.primary));
        valueView.setGravity(Gravity.CENTER);

        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextSize(10);
        labelView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        labelView.setGravity(Gravity.CENTER);

        itemLayout.addView(emojiView);
        itemLayout.addView(valueView);
        itemLayout.addView(labelView);

        return itemLayout;
    }

    private View createVerticalDivider() {
        View divider = new View(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(1, 40);
        params.setMargins(8, 0, 8, 0);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.divider));
        return divider;
    }

    // ==============================================================
// SOLUTION 3: Fix createMaterialsBreakdown to ensure fresh ScrollView
// ==============================================================

    private View createMaterialsBreakdown(Map<String, TransactionMaterialSummary> materialSummary) {
        // ALWAYS create fresh container - never reuse
        LinearLayout materialsContainer = new LinearLayout(getContext());
        materialsContainer.setOrientation(LinearLayout.VERTICAL);
        materialsContainer.setPadding(0, 8, 0, 8);

        // Create fresh title TextView
        TextView breakdownTitle = new TextView(getContext());
        breakdownTitle.setText("Materials Breakdown:");
        breakdownTitle.setTextSize(14);
        breakdownTitle.setTypeface(breakdownTitle.getTypeface(), Typeface.BOLD);
        breakdownTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        breakdownTitle.setPadding(0, 0, 0, 8);

        // ALWAYS create fresh ScrollView and container
        ScrollView scrollView = new ScrollView(getContext());
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.25)
        );
        scrollView.setLayoutParams(scrollParams);

        // Create fresh inner container
        LinearLayout materialsLayout = new LinearLayout(getContext());
        materialsLayout.setOrientation(LinearLayout.VERTICAL);

        // Create fresh material cards for each material
        for (TransactionMaterialSummary summary : materialSummary.values()) {
            View materialCard = createCompactMaterialCard(summary);
            materialsLayout.addView(materialCard);
        }

        // Assemble the hierarchy with fresh views
        scrollView.addView(materialsLayout);
        materialsContainer.addView(breakdownTitle);
        materialsContainer.addView(scrollView);

        return materialsContainer;
    }

    // ==============================================================
// SOLUTION 4: Ensure createCompactMaterialCard always creates fresh views
// ==============================================================

    private View createCompactMaterialCard(TransactionMaterialSummary summary) {
        // ALWAYS create fresh container and all child views
        LinearLayout cardLayout = new LinearLayout(getContext());
        cardLayout.setOrientation(LinearLayout.HORIZONTAL);
        cardLayout.setGravity(Gravity.CENTER_VERTICAL);
        cardLayout.setPadding(12, 8, 12, 8);

        try {
            cardLayout.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.material_card_background));
        } catch (Exception e) {
            // Fallback background
            cardLayout.setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.white));
        }

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 2, 0, 2);
        cardLayout.setLayoutParams(cardParams);

        // Create fresh name section
        LinearLayout nameLayout = new LinearLayout(getContext());
        nameLayout.setOrientation(LinearLayout.HORIZONTAL);
        nameLayout.setGravity(Gravity.CENTER_VERTICAL);
        nameLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView emojiView = new TextView(getContext());
        String emoji = getMaterialEmoji(summary.materialName);
        emojiView.setText(emoji);
        emojiView.setTextSize(16);

        TextView nameView = new TextView(getContext());
        nameView.setText(summary.materialName);
        nameView.setTextSize(14);
        nameView.setTypeface(nameView.getTypeface(), Typeface.BOLD);
        nameView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        nameView.setPadding(8, 0, 0, 0);

        nameLayout.addView(emojiView);
        nameLayout.addView(nameView);

        // Create fresh details section
        LinearLayout detailsLayout = new LinearLayout(getContext());
        detailsLayout.setOrientation(LinearLayout.VERTICAL);
        detailsLayout.setGravity(Gravity.END);

        TextView weightView = new TextView(getContext());
        weightView.setText(weightFormat.format(summary.weight) + " kg");
        weightView.setTextSize(12);
        weightView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        weightView.setGravity(Gravity.END);

        TextView valueView = new TextView(getContext());
        valueView.setText(currencyFormat.format(summary.value));
        valueView.setTextSize(12);
        valueView.setTypeface(valueView.getTypeface(), Typeface.BOLD);
        valueView.setTextColor(ContextCompat.getColor(getContext(), R.color.success));
        valueView.setGravity(Gravity.END);

        detailsLayout.addView(weightView);
        detailsLayout.addView(valueView);

        // Add all fresh views to card
        cardLayout.addView(nameLayout);
        cardLayout.addView(detailsLayout);

        return cardLayout;
    }

    // ==============================================================
// SOLUTION 2: Fix createTotalSummaryFooter to always create fresh views
// ==============================================================

    private View createTotalSummaryFooter(double totalWeight, double totalValue) {
        // ALWAYS create completely fresh LinearLayout - never reuse
        LinearLayout footerLayout = new LinearLayout(getContext());
        footerLayout.setOrientation(LinearLayout.VERTICAL);
        footerLayout.setPadding(12, 12, 12, 8);

        // Create fresh background
        try {
            footerLayout.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.total_summary_background));
        } catch (Exception e) {
            // Fallback if drawable not found
            footerLayout.setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.darker_gray));
        }

        // Create fresh divider view
        View divider = new View(getContext());
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2
        );
        dividerParams.setMargins(0, 0, 0, 8);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.primary));

        // Create fresh TextView
        TextView totalSummary = new TextView(getContext());
        totalSummary.setText(
                "🏷️ TRANSACTION TOTAL\n" +
                        "Weight: " + weightFormat.format(totalWeight) + " kg  •  " +
                        "Value: " + currencyFormat.format(totalValue)
        );
        totalSummary.setTextSize(14);
        totalSummary.setTypeface(totalSummary.getTypeface(), Typeface.BOLD);
        totalSummary.setTextColor(ContextCompat.getColor(getContext(), R.color.primary));
        totalSummary.setGravity(Gravity.CENTER);
        totalSummary.setLineSpacing(4, 1);

        // Add views to fresh parent (this should never cause parent conflicts now)
        footerLayout.addView(divider);
        footerLayout.addView(totalSummary);

        return footerLayout;
    }

    private LinearLayout createIntegratedButtons(Map<String, TransactionMaterialSummary> materialSummary,
                                                 double totalWeight, double totalValue) {
        LinearLayout buttonsContainer = new LinearLayout(getContext());
        buttonsContainer.setOrientation(LinearLayout.VERTICAL);
        buttonsContainer.setPadding(0, 16, 0, 20);

        // Main action button (Complete Transaction)
        MaterialButton completeButton = new MaterialButton(getContext());
        completeButton.setText("✅ Complete Transaction");
        completeButton.setTextColor(ContextCompat.getColor(getContext(), android.R.color.white));
        completeButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.success)));
        completeButton.setCornerRadius(16);
        completeButton.setTextSize(16f);
        completeButton.setAllCaps(false);
        completeButton.setPadding(24, 20, 24, 20);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            completeButton.setElevation(4f);
        }

        // Set button layout params
        LinearLayout.LayoutParams completeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        completeParams.setMargins(0, 0, 0, 12);
        completeButton.setLayoutParams(completeParams);

        // Complete button click handler - MAINTAIN ORIGINAL FUNCTIONALITY
        completeButton.setOnClickListener(v -> {
            // Dismiss dialog
            if (currentDialog != null) {
                currentDialog.dismiss();
            }
            // User confirmed - proceed with transaction (ORIGINAL IMPLEMENTATION)
            proceedWithTransaction(materialSummary, totalWeight, totalValue);
        });

        // Secondary actions layout (Cancel and Edit side by side)
        LinearLayout secondaryButtonsLayout = new LinearLayout(getContext());
        secondaryButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        secondaryButtonsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Cancel button
        MaterialButton cancelButton = new MaterialButton(getContext());
        cancelButton.setText("❌ Cancel");
        cancelButton.setTextColor(ContextCompat.getColor(getContext(), R.color.error));
        cancelButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), android.R.color.transparent)));
        cancelButton.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.error)));
        cancelButton.setStrokeWidth(2);
        cancelButton.setCornerRadius(12);
        cancelButton.setTextSize(14f);
        cancelButton.setAllCaps(false);

        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        cancelParams.setMargins(0, 0, 8, 0);
        cancelButton.setLayoutParams(cancelParams);

        // Cancel button click handler - MAINTAIN ORIGINAL FUNCTIONALITY
        cancelButton.setOnClickListener(v -> {
            // Dismiss dialog
            if (currentDialog != null) {
                currentDialog.dismiss();
            }
            // User cancelled (ORIGINAL IMPLEMENTATION)
            if (getContext() != null) {
                Toast.makeText(getContext(), "Transaction cancelled", Toast.LENGTH_SHORT).show();
            }
        });

        // Edit button
        MaterialButton editButton = new MaterialButton(getContext());
        editButton.setText("📝 Edit");
        editButton.setTextColor(ContextCompat.getColor(getContext(), R.color.warning));
        editButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), android.R.color.transparent)));
        editButton.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.warning)));
        editButton.setStrokeWidth(2);
        editButton.setCornerRadius(12);
        editButton.setTextSize(14f);
        editButton.setAllCaps(false);

        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        editParams.setMargins(8, 0, 0, 0);
        editButton.setLayoutParams(editParams);

        // Edit button click handler - MAINTAIN ORIGINAL FUNCTIONALITY
        editButton.setOnClickListener(v -> {
            // Dismiss dialog
            if (currentDialog != null) {
                currentDialog.dismiss();
            }
            // Allow user to go back and edit (ORIGINAL IMPLEMENTATION)
            if (getContext() != null) {
                Toast.makeText(getContext(), "You can add/remove materials and try again", Toast.LENGTH_LONG).show();
            }
        });

        // Add buttons to layouts
        secondaryButtonsLayout.addView(cancelButton);
        secondaryButtonsLayout.addView(editButton);

        buttonsContainer.addView(completeButton);
        buttonsContainer.addView(secondaryButtonsLayout);

        return buttonsContainer;
    }

    private String getMaterialEmoji(String materialName) {
        if (materialName == null) return "🔧";

        switch (materialName.toLowerCase().trim()) {
            case "steel":
            case "stainless steel":
                return "🔩";
            case "aluminum":
            case "aluminium":
                return "🥫";
            case "copper":
                return "🔶";
            case "brass":
                return "🟨";
            case "iron":
            case "cast iron":
                return "⚫";
            case "lead":
                return "🔘";
            case "tin":
                return "🥫";
            case "zinc":
                return "⚪";
            case "nickel":
                return "💍";
            case "titanium":
                return "⚙️";
            case "silver":
                return "🥈";
            case "gold":
                return "🥇";
            case "platinum":
                return "💎";
            default:
                return "🔧";
        }
    }

    // Helper class for material summary
    private static class TransactionMaterialSummary {
        String materialName;
        double weight;
        double pricePerKg;
        double value;

        TransactionMaterialSummary(String materialName, double weight, double pricePerKg, double value) {
            this.materialName = materialName;
            this.weight = weight;
            this.pricePerKg = pricePerKg;
            this.value = value;
        }
    }



    // Check before processing the transaction after confirmation
    private void proceedWithTransaction(Map<String, TransactionMaterialSummary> materialSummary,
                                        double totalWeight, double totalValue) {

        // DOUBLE CHECK LICENSE before final processing
        LicenseChecker.checkLicenseWithCallback(getContext(), "save transaction",
                new LicenseChecker.LicenseCheckCallback() {
                    @Override
                    public void onLicenseValid() {
                        // Continue with transaction processing
                        processFinalTransaction(materialSummary, totalWeight, totalValue);
                    }

                    @Override
                    public void onLicenseDenied() {
                        // Transaction cancelled due to license
                        Log.d(TAG, "Transaction cancelled - no valid license");
                        if (currentDialog != null && currentDialog.isShowing()) {
                            currentDialog.dismiss();
                        }
                    }
                });
    }

    // Move the actual processing to a separate method
    private void processFinalTransaction(Map<String, TransactionMaterialSummary> materialSummary,
                                         double totalWeight, double totalValue) {
        Log.d(TAG, "User confirmed transaction - proceeding with processing");

        // Generate unique transaction ID
        String transactionId = "TXN_" + System.currentTimeMillis();

        // Create Transaction object for database
        Transaction transaction = new Transaction(
                transactionId,
                totalWeight,
                totalValue,
                transactionMaterials.size()
        );

        // Add any additional notes
        String connectionInfo = bleScaleViewModel != null && bleScaleViewModel.isConnectedValue() ? "BLE Scale" : "Manual Entry";
        transaction.setNotes("Completed via " + connectionInfo + " - User Confirmed");

        Log.d(TAG, "Saving confirmed transaction: " + transactionId + " with " + transactionMaterials.size() + " materials");

        // Show processing indicator
        if (getContext() != null) {
            Toast.makeText(getContext(), "Processing transaction...", Toast.LENGTH_SHORT).show();
        }

        // Save transaction to database in background thread
        new Thread(() -> {
            try {
                long savedTransactionId = -1;

                if (transactionsDBHelper != null) {
                    savedTransactionId = transactionsDBHelper.saveTransaction(transaction, transactionMaterials);
                }

                // Return to main thread for UI updates
                final long finalSavedTransactionId = savedTransactionId;
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        // Convert materialSummary to the format expected by handleTransactionSaved
                        Map<String, Double> simpleSummary = new HashMap<>();
                        for (TransactionMaterialSummary summary : materialSummary.values()) {
                            simpleSummary.put(summary.materialName, summary.weight);
                        }

                        handleTransactionSaved(finalSavedTransactionId, transaction, simpleSummary, totalWeight, totalValue);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error saving confirmed transaction to database", e);

                // Return to main thread for error handling
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        // Convert materialSummary for error handling
                        Map<String, Double> simpleSummary = new HashMap<>();
                        for (TransactionMaterialSummary summary : materialSummary.values()) {
                            simpleSummary.put(summary.materialName, summary.weight);
                        }

                        handleTransactionSaveError(e, transaction, simpleSummary, totalWeight, totalValue);
                    });
                }
            }
        }).start();
    }
    private void handleTransactionSaved(long savedTransactionId, Transaction transaction,
                                        Map<String, Double> materialSummary, double totalWeight, double totalValue) {

        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "Fragment not attached, cannot show transaction completion");
            return;
        }

        Log.d(TAG, "Transaction saved successfully - showing smart dialog");

        // Create transaction data for dialog
        SmartTransactionDialog.TransactionData transactionData =
                new SmartTransactionDialog.TransactionData(
                        transaction.getTransactionId(),
                        totalValue,
                        totalWeight,
                        transactionMaterials.size()
                );

        // Generate receipt content
        String receiptContent = generateReceipt(materialSummary, totalWeight, totalValue);

        // Initialize print status manager if needed
        if (printStatusManager == null) {
            printStatusManager = new PrintStatusManager();
        }

        // Initialize and show smart dialog
        try {
            smartDialog = new SmartTransactionDialog(getContext());
            smartDialog.show(transactionData, receiptContent, printStatusManager);

            Log.d(TAG, "Smart dialog shown successfully");

            // Show success toast
            String toastMessage = savedTransactionId > 0 ?
                    "Transaction saved successfully!" :
                    "Transaction completed (database save failed)";
            Toast.makeText(getContext(), toastMessage,
                    savedTransactionId > 0 ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();

            // Reset transaction for next one
            resetTransaction();

        } catch (Exception e) {
            Log.e(TAG, "Failed to show smart dialog", e);

            // Fallback to original dialog
            showFallbackCompletionDialog(savedTransactionId, transaction, materialSummary, totalWeight, totalValue, receiptContent);
        }
    }

// Add this fallback method in case smart dialog fails:

    private void showFallbackCompletionDialog(long savedTransactionId, Transaction transaction,
                                              Map<String, Double> materialSummary, double totalWeight, double totalValue, String receiptContent) {

        Log.w(TAG, "Using fallback completion dialog");

        // Build basic success message (same as original)
        StringBuilder summary = new StringBuilder();

        if (savedTransactionId > 0) {
            summary.append("✅ Transaction Saved Successfully!\n");
        } else {
            summary.append("⚠️ Transaction Completed (Save Failed)\n");
        }

        summary.append("Transaction ID: ").append(transaction.getTransactionId()).append("\n\n");

        // Add material details
        for (Map.Entry<String, Double> entry : materialSummary.entrySet()) {
            summary.append(entry.getKey()).append(": ").append(weightFormat.format(entry.getValue())).append(" kg\n");
        }
        summary.append("\nTotal: ").append(currencyFormat.format(totalValue));

        // Show basic dialog with print option
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle("Transaction Complete")
                .setMessage(summary.toString())
                .setPositiveButton("Print Receipt", (dialog, which) -> {
                    if (mainActivity != null && mainActivity.isPrinterConnected()) {
                        mainActivity.printReceipt(receiptContent);
                        showSnackbar("Receipt sent to printer", Snackbar.LENGTH_SHORT);
                    } else {
                        showSnackbar("Printer not connected", Snackbar.LENGTH_SHORT);
                    }
                })
                .setNegativeButton("Done", null);

        builder.show();

        // Reset for next transaction
        resetTransaction();
    }

    private void handleTransactionSaveError(Exception error, Transaction transaction,
                                            Map<String, Double> materialSummary, double totalWeight, double totalValue) {

        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "Fragment not attached, cannot show error dialog");
            return;
        }

        Log.e(TAG, "Transaction save error: " + error.getMessage(), error);

        // Build error message
        StringBuilder summary = new StringBuilder("❌ Transaction Save Failed\n");
        summary.append("Error: ").append(error.getMessage()).append("\n");
        summary.append("Transaction ID: ").append(transaction.getTransactionId()).append("\n\n");

        // Add material details
        for (Map.Entry<String, Double> entry : materialSummary.entrySet()) {
            summary.append(entry.getKey()).append(": ").append(weightFormat.format(entry.getValue())).append(" kg\n");
        }
        summary.append("\nTotal Weight: ").append(weightFormat.format(totalWeight)).append(" kg");
        summary.append("\nTotal Value: ").append(currencyFormat.format(totalValue));

        // Generate receipt for printing
        String receiptContent = generateReceipt(materialSummary, totalWeight, totalValue);

        // Show error dialog with options
        new AlertDialog.Builder(getContext())
                .setTitle("Save Error")
                .setMessage(summary.toString())
                .setPositiveButton("Print Receipt Anyway", (dialog, which) -> {
                    if (mainActivity != null && mainActivity.isPrinterConnected()) {
                        mainActivity.printReceipt(receiptContent);
                        showSnackbar("Receipt sent to printer", Snackbar.LENGTH_SHORT);
                    } else {
                        showSnackbar("Printer not connected", Snackbar.LENGTH_SHORT);
                    }
                })
                .setNegativeButton("Retry Save", (dialog, which) -> {
                    // Retry the save operation
                    completeTransaction();
                })
                .setNeutralButton("Continue", (dialog, which) -> {
                    // Continue without saving
                    resetTransaction();
                })
                .show();

        // Show error toast
        Toast.makeText(getContext(), "Failed to save transaction: " + error.getMessage(), Toast.LENGTH_LONG).show();
    }

    public void getTransactionStats() {
        if (transactionsDBHelper == null) return;

        new Thread(() -> {
            try {
                TransactionsDBHelper.TransactionStats stats = transactionsDBHelper.getTransactionStats();

                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        Log.d(TAG, "Transaction Stats - Count: " + stats.totalTransactions +
                                ", Weight: " + stats.getFormattedTotalWeight() +
                                ", Value: " + stats.getFormattedTotalValue());
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Error getting transaction stats", e);
            }
        }).start();
    }

    private String generateReceipt(Map<String, Double> materialSummary, double totalWeight, double totalValue) {
        StringBuilder receipt = new StringBuilder();

        // Header with business info
        receipt.append("================================\n");
        receipt.append("    MERU SCRAP METAL MARKET\n");
        receipt.append("      Meru County, Kenya\n");
        receipt.append("================================\n");

        // Date and transaction info
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        receipt.append("Date: ").append(dateFormat.format(new Date())).append("\n");
        receipt.append("Transaction: ").append(System.currentTimeMillis()).append("\n");
        if (transactionMaterials.size() > 0) {
            receipt.append("Materials: ").append(transactionMaterials.size()).append("\n");
        }
        receipt.append("================================\n");

        // Materials section
        receipt.append("MATERIALS:\n");
        receipt.append("--------------------------------\n");

        for (Map.Entry<String, Double> entry : materialSummary.entrySet()) {
            String materialName = entry.getKey();
            double weight = entry.getValue();

            // Find price per kg for this material
            double pricePerKg = 0.0;
            for (TransactionMaterial tm : transactionMaterials) {
                if (tm.getMaterialName().equals(materialName)) {
                    pricePerKg = tm.getPricePerKg();
                    break;
                }
            }

            double value = weight * pricePerKg;

            receipt.append(String.format("%-15s %8s kg\n",
                    truncateString(materialName, 15),
                    weightFormat.format(weight)));
            receipt.append(String.format("  @ %s/kg = %s\n",
                    currencyFormat.format(pricePerKg).replace("KSH ", ""),
                    currencyFormat.format(value)));
            receipt.append("\n");
        }

        receipt.append("--------------------------------\n");
        receipt.append(String.format("TOTAL WEIGHT: %10s kg\n", weightFormat.format(totalWeight)));
        receipt.append(String.format("TOTAL VALUE:  %s\n", currencyFormat.format(totalValue)));
        receipt.append("================================\n");
        receipt.append("Thank you for your business!\n");
        receipt.append("Come back soon!\n");
        receipt.append("================================\n");
        receipt.append("\n\n"); // Extra spacing for cut

        return receipt.toString();
    }

    /**
     * Helper method to truncate strings for receipt formatting
     */
    private String truncateString(String str, int maxLength) {
        if (str == null) return "";
        return str.length() <= maxLength ? str : str.substring(0, maxLength - 3) + "...";
    }

    private void resetTransaction() {
        Log.d(TAG, "Resetting transaction");

        batches.clear();
        if (batchAdapter != null) {
            batchAdapter.notifyDataSetChanged();
        }

        transactionMaterials.clear();
        if (transactionSummaryAdapter != null) {
            transactionSummaryAdapter.notifyDataSetChanged();
        }

        usedMaterialIds.clear();
        if (materialSelectionAdapter != null) {
            materialSelectionAdapter.updateUsedMaterials(usedMaterialIds);
        }
        updateMaterialCount();

        selectedMaterial = null;
        isAccumulativeMode = false;

        if (etSingleManualWeight != null) etSingleManualWeight.setText("");
        if (etBatchManualWeight != null) etBatchManualWeight.setText("");

        if (accumulativeWeighingCard != null) {
            accumulativeWeighingCard.setVisibility(View.GONE);
        }
        if (singleWeighingCard != null) {
            singleWeighingCard.setVisibility(View.GONE);
        }
        if (transactionSummaryCard != null) {
            transactionSummaryCard.setVisibility(View.GONE);
        }
        if (materialSelectionCard != null) {
            materialSelectionCard.setVisibility(bleScaleViewModel != null && bleScaleViewModel.isConnectedValue() ? View.VISIBLE : View.GONE);
        }

        updateUI();
    }

    // =================================================================
    // ✅ FIXED: INTERFACE IMPLEMENTATIONS - Same as Home fragment
    // =================================================================

    // BleScaleConnectionDialog.ScaleConnectionListener implementations
    @Override
    public void onScaleConnected() {
        Log.d(TAG, "Scale connection dialog reports successful connection");

        if (getContext() != null && isAdded()) {
            // Update UI to reflect connection
            updateUI();

            // Show success message
            showSnackbar("Scale connected successfully!", Snackbar.LENGTH_SHORT);

            // Optional: Show a toast as well
            Toast.makeText(getContext(), "✅ Scale Ready for Weighing", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConnectionCancelled() {
        Log.d(TAG, "Scale connection cancelled by user");

        if (getContext() != null && isAdded()) {
            showSnackbar("Scale connection cancelled", Snackbar.LENGTH_SHORT);
        }
    }

    // BlePermissionHandler.PermissionCallback implementations
    @Override
    public void onPermissionsGranted() {
        if (getContext() != null && isAdded()) {
            showScaleConnectionDialog();
        }
    }

    @Override
    public void onPermissionsDenied(String[] deniedPermissions) {
        if (getContext() != null && isAdded()) {
            StringBuilder message = new StringBuilder("The following permissions are required for BLE scanning:\n");
            for (String permission : deniedPermissions) {
                message.append("• ").append(BlePermissionHandler.getPermissionDisplayName(permission)).append("\n");
            }
            message.append("\nPlease grant these permissions in Settings to use scale features.");

            Toast.makeText(getContext(), message.toString(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onPermissionsExplanationNeeded(String[] permissions) {
        if (getContext() != null && isAdded()) {
            new androidx.appcompat.app.AlertDialog.Builder(getContext())
                    .setTitle("Permissions Required")
                    .setMessage(BlePermissionHandler.getPermissionExplanation())
                    .setPositiveButton("Grant Permissions", (dialog, which) -> {
                        if (permissionHandler != null) {
                            permissionHandler.requestPermissions();
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Permissions are required to connect to scales", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionHandler != null) {
            permissionHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // =================================================================
    // UTILITY METHODS
    // =================================================================

    private void showSnackbar(String message, int duration) {
        if (getView() != null && getContext() != null && isAdded()) {
            try {
                Snackbar.make(getView(), message, duration).show();
            } catch (Exception e) {
                Log.w(TAG, "Could not show snackbar: " + e.getMessage());
                // Fallback to Toast if Snackbar fails
                if (getContext() != null) {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Log.w(TAG, "Cannot show snackbar - fragment not properly attached. Message: " + message);
        }
    }

    private void verifyAdapterAfterUpdate() {
        Log.d(TAG, "=== ADAPTER VERIFICATION ===");
        Log.d(TAG, "availableMaterials.size(): " + availableMaterials.size());

        if (materialSelectionAdapter != null) {
            Log.d(TAG, "Adapter getItemCount(): " + materialSelectionAdapter.getItemCount());
            Log.d(TAG, "Adapter isEmpty(): " + materialSelectionAdapter.isEmpty());
            Log.d(TAG, "Adapter hasAvailableMaterials(): " + materialSelectionAdapter.hasAvailableMaterials());

            // Test getting materials from adapter
            List<Material> adapterMaterials = materialSelectionAdapter.getAllMaterials();
            Log.d(TAG, "Adapter getAllMaterials().size(): " + adapterMaterials.size());

            if (!adapterMaterials.isEmpty()) {
                Log.d(TAG, "First adapter material: " + adapterMaterials.get(0).getName());
            }
        } else {
            Log.e(TAG, "materialSelectionAdapter is NULL!");
        }

        // Check RecyclerView
        if (rvMaterialSelection != null) {
            Log.d(TAG, "RecyclerView adapter: " + (rvMaterialSelection.getAdapter() != null));
            Log.d(TAG, "RecyclerView visibility: " + rvMaterialSelection.getVisibility());
            if (rvMaterialSelection.getAdapter() != null) {
                Log.d(TAG, "RecyclerView adapter item count: " + rvMaterialSelection.getAdapter().getItemCount());
            }
        }

        Log.d(TAG, "=== END VERIFICATION ===");
    }



    // =================================================================
    // END OF CLASS
    // =================================================================
    private class PrintStatusManager implements SmartTransactionDialog.SmartDialogListener {
        private static final String TAG = "PrintStatusManager";

        // Queue for receipt content if printer is busy
        private String queuedReceiptContent;
        private int currentAttempt = 0;

        @Override
        public void onPrintRequested(String receiptContent, int attemptNumber) {
            Log.d(TAG, "Print requested - Attempt: " + attemptNumber);

            currentAttempt = attemptNumber;
            queuedReceiptContent = receiptContent;

            // Check printer availability
            if (!isPrinterConnected()) {
                // Simulate brief delay then report failure
                new Handler().postDelayed(() -> {
                    if (smartDialog != null) {
                        smartDialog.onPrintFailure("Printer not connected");
                    }
                }, 500);
                return;
            }

            // Start print job in background
            new Thread(() -> {
                try {
                    boolean success = executePrintJob(receiptContent);

                    // Return to main thread for UI updates
                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(() -> {
                            if (smartDialog != null) {
                                if (success) {
                                    smartDialog.onPrintSuccess();
                                } else {
                                    smartDialog.onPrintFailure("Print job failed");
                                }
                            }
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Print execution error", e);

                    // Return to main thread for error handling
                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(() -> {
                            if (smartDialog != null) {
                                smartDialog.onPrintFailure("Print error: " + e.getMessage());
                            }
                        });
                    }
                }
            }).start();
        }

        @Override
        public void onPrintLater(String receiptContent) {
            Log.d(TAG, "Print later requested");

            // Save receipt for later printing
            saveReceiptForLater(receiptContent);

            if (getContext() != null) {
                showSnackbar("Receipt saved for later printing", Snackbar.LENGTH_SHORT);
            }
        }

        @Override
        public void onViewReceipt(String receiptContent) {
            Log.d(TAG, "View receipt requested");

            // Show receipt in a dialog or new activity
            showReceiptPreview(receiptContent);
        }

        @Override
        public void onDialogDismissed() {
            Log.d(TAG, "Smart dialog dismissed");

            // Clear any pending operations
            queuedReceiptContent = null;
            currentAttempt = 0;

            // Reset transaction state for next transaction
            // This is called after successful completion or cancellation
        }

        @Override
        public boolean isPrinterConnected() {
            // Check via MainActivity if possible
            if (mainActivity != null) {
                return mainActivity.isPrinterConnected();
            }

            // Fallback: simulate connection for testing
            // In production, you'd check your actual printer connection
            return true; // Set to false to test "printer not connected" scenario
        }

        /**
         * Execute the actual print job
         */
        private boolean executePrintJob(String receiptContent) {
            try {
                Log.d(TAG, "Executing print job...");

                // Simulate print time (remove this in production)
                Thread.sleep(2000 + (currentAttempt * 1000)); // Longer delay for retries

                // Check if printer is still connected
                if (!isPrinterConnected()) {
                    Log.w(TAG, "Printer disconnected during print job");
                    return false;
                }

                // Send to actual printer via MainActivity
                if (mainActivity != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        mainActivity.printReceipt(receiptContent);
                    });

                    // For now, assume success
                    // In production, you'd get actual result from print manager
                    boolean printSuccess = simulatePrintResult();

                    Log.d(TAG, "Print job completed: " + (printSuccess ? "SUCCESS" : "FAILED"));
                    return printSuccess;
                }

                return false;

            } catch (InterruptedException e) {
                Log.w(TAG, "Print job interrupted", e);
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Print job execution failed", e);
                return false;
            }
        }

        /**
         * Simulate print result (remove in production)
         * Replace this with actual printer result checking
         */
        private boolean simulatePrintResult() {
            // Simulate different scenarios for testing

            // 90% success rate on first attempt
            if (currentAttempt == 1) {
                return Math.random() < 0.9;
            }

            // 70% success rate on retry attempts
            return Math.random() < 0.7;
        }

        /**
         * Save receipt content for later printing
         */
        private void saveReceiptForLater(String receiptContent) {
            try {
                // Save to SharedPreferences or database
                SharedPreferences prefs = getContext().getSharedPreferences("pending_receipts", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();

                String key = "receipt_" + System.currentTimeMillis();
                editor.putString(key, receiptContent);
                editor.apply();

                Log.d(TAG, "Receipt saved for later: " + key);

            } catch (Exception e) {
                Log.e(TAG, "Failed to save receipt for later", e);
            }
        }

        /**
         * Show receipt preview in a dialog
         */
        private void showReceiptPreview(String receiptContent) {
            if (getContext() == null || !isAdded()) return;

            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

                // Create scrollable text view for receipt
                TextView textView = new TextView(getContext());
                textView.setText(receiptContent);
                textView.setTypeface(android.graphics.Typeface.MONOSPACE);
                textView.setPadding(32, 32, 32, 32);
                textView.setTextSize(12);

                ScrollView scrollView = new ScrollView(getContext());
                scrollView.addView(textView);

                builder.setView(scrollView)
                        .setTitle("Receipt Preview")
                        .setPositiveButton("Close", null)
                        .setNeutralButton("Copy", (dialog, which) -> {
                            // Copy receipt to clipboard
                            android.content.ClipboardManager clipboard =
                                    (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            android.content.ClipData clip = android.content.ClipData.newPlainText("Receipt", receiptContent);
                            clipboard.setPrimaryClip(clip);

                            showSnackbar("Receipt copied to clipboard", Snackbar.LENGTH_SHORT);
                        })
                        .show();

            } catch (Exception e) {
                Log.e(TAG, "Failed to show receipt preview", e);
                showSnackbar("Failed to show receipt preview", Snackbar.LENGTH_SHORT);
            }
        }
    }

    /**
     * Get pending receipts that were saved for later printing
     */
    public void getPendingReceipts() {
        if (getContext() == null) return;

        try {
            SharedPreferences prefs = getContext().getSharedPreferences("pending_receipts", Context.MODE_PRIVATE);
            Map<String, ?> allReceipts = prefs.getAll();

            if (allReceipts.isEmpty()) {
                showSnackbar("No pending receipts", Snackbar.LENGTH_SHORT);
                return;
            }

            // Show list of pending receipts
            String[] receiptKeys = allReceipts.keySet().toArray(new String[0]);
            String[] receiptTitles = new String[receiptKeys.length];

            for (int i = 0; i < receiptKeys.length; i++) {
                // Extract timestamp from key and format it
                String key = receiptKeys[i];
                try {
                    long timestamp = Long.parseLong(key.replace("receipt_", ""));
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
                    receiptTitles[i] = "Receipt - " + sdf.format(new Date(timestamp));
                } catch (Exception e) {
                    receiptTitles[i] = "Receipt - " + key;
                }
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Pending Receipts (" + receiptKeys.length + ")")
                    .setItems(receiptTitles, (dialog, which) -> {
                        String selectedKey = receiptKeys[which];
                        String receiptContent = prefs.getString(selectedKey, "");
                        showPendingReceiptOptions(selectedKey, receiptContent);
                    })
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("Clear All", (dialog, which) -> clearAllPendingReceipts())
                    .show();

        } catch (Exception e) {
            Log.e(TAG, "Error getting pending receipts", e);
            showSnackbar("Error loading pending receipts", Snackbar.LENGTH_SHORT);
        }
    }

    /**
     * Show options for a pending receipt
     */
    private void showPendingReceiptOptions(String receiptKey, String receiptContent) {
        if (getContext() == null || receiptContent.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Pending Receipt")
                .setMessage("What would you like to do with this receipt?")
                .setPositiveButton("Print Now", (dialog, which) -> {
                    if (printStatusManager.isPrinterConnected()) {
                        mainActivity.printReceipt(receiptContent);
                        removePendingReceipt(receiptKey);
                        showSnackbar("Receipt sent to printer", Snackbar.LENGTH_SHORT);
                    } else {
                        showSnackbar("Printer not connected", Snackbar.LENGTH_SHORT);
                    }
                })
                .setNeutralButton("View", (dialog, which) -> {
                    showReceiptPreview(receiptContent);
                })
                .setNegativeButton("Delete", (dialog, which) -> {
                    removePendingReceipt(receiptKey);
                    showSnackbar("Receipt deleted", Snackbar.LENGTH_SHORT);
                })
                .show();
    }


    /**
     * Remove a specific pending receipt
     */
    private void removePendingReceipt(String receiptKey) {
        if (getContext() == null) return;

        try {
            SharedPreferences prefs = getContext().getSharedPreferences("pending_receipts", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(receiptKey);
            editor.apply();

            Log.d(TAG, "Removed pending receipt: " + receiptKey);
        } catch (Exception e) {
            Log.e(TAG, "Error removing pending receipt", e);
        }
    }

    /**
     * Clear all pending receipts
     */
    private void clearAllPendingReceipts() {
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("Clear All Pending Receipts")
                .setMessage("Are you sure you want to delete all pending receipts? This cannot be undone.")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    try {
                        SharedPreferences prefs = getContext().getSharedPreferences("pending_receipts", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.clear();
                        editor.apply();

                        showSnackbar("All pending receipts cleared", Snackbar.LENGTH_SHORT);
                    } catch (Exception e) {
                        Log.e(TAG, "Error clearing pending receipts", e);
                        showSnackbar("Error clearing receipts", Snackbar.LENGTH_SHORT);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Show receipt preview in a dialog
     */
    private void showReceiptPreview(String receiptContent) {
        if (getContext() == null || !isAdded()) return;

        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

            // Create scrollable text view for receipt
            TextView textView = new TextView(getContext());
            textView.setText(receiptContent);
            textView.setTypeface(android.graphics.Typeface.MONOSPACE);
            textView.setPadding(32, 32, 32, 32);
            textView.setTextSize(12);
            textView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));

            ScrollView scrollView = new ScrollView(getContext());
            scrollView.addView(textView);

            // Set max height for the scroll view
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.6)
            );
            scrollView.setLayoutParams(params);

            builder.setView(scrollView)
                    .setTitle("Receipt Preview")
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Copy", (dialog, which) -> {
                        // Copy receipt to clipboard
                        android.content.ClipboardManager clipboard =
                                (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            android.content.ClipData clip = android.content.ClipData.newPlainText("Receipt", receiptContent);
                            clipboard.setPrimaryClip(clip);
                            showSnackbar("Receipt copied to clipboard", Snackbar.LENGTH_SHORT);
                        }
                    })
                    .show();

        } catch (Exception e) {
            Log.e(TAG, "Failed to show receipt preview", e);
            showSnackbar("Failed to show receipt preview", Snackbar.LENGTH_SHORT);
        }
    }

    private void centerDialogOnScreen(AlertDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;

        try {
            Window window = dialog.getWindow();

            // Get screen dimensions
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int screenHeight = displayMetrics.heightPixels;
            int screenWidth = displayMetrics.widthPixels;

            // Calculate dialog size
            int dialogWidth = (int) (screenWidth * 0.92);

            // Set window attributes
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = dialogWidth;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            params.x = 0;
            params.y = 0;

            // Ensure dialog doesn't extend beyond screen bounds
            params.verticalMargin = 0.1f; // 10% margin from top/bottom

            window.setAttributes(params);

            // Add smooth animation
            window.getAttributes().windowAnimations = android.R.style.Animation_Dialog;

        } catch (Exception e) {
            Log.w(TAG, "Could not center dialog: " + e.getMessage());
        }
    }

    /**
     * Optional: Enhanced error handling for dialog creation
     */
    private boolean isContextValidForDialog() {
        return getContext() != null &&
                isAdded() &&
                !isDetached() &&
                !getActivity().isFinishing() &&
                !getActivity().isDestroyed();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "=== onDestroy() - Cleaning up resources ===");
// Clean up smart dialog
        if (smartDialog != null && smartDialog.isShowing()) {
            smartDialog.dismiss();
            smartDialog = null;
        }
// Clean up dialog reference
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;
        printStatusManager = null;
        try {
            // ✅ FIXED: Clean up ViewModel references (don't destroy shared ViewModel)
            bleScaleViewModel = null;

            // Clean up database helpers (they use singleton pattern, but clear references)
            materialsDBHelper = null;
            transactionsDBHelper = null;

            Log.d(TAG, "Fragment cleanup completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during fragment cleanup", e);
        }
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Ensure dialog is properly dismissed
        if (currentDialog != null && currentDialog.isShowing()) {
            try {
                currentDialog.dismiss();
            } catch (Exception e) {
                Log.w(TAG, "Error dismissing dialog in onDestroyView", e);
            } finally {
                currentDialog = null;
            }
        }

        Log.d(TAG, "onDestroyView completed");
    }
}