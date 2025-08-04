package com.example.meruscrap;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;

import java.text.DecimalFormat;

public class SmartTransactionDialog {
    private static final String TAG = "SmartTransactionDialog";

    // State constants
    public enum DialogState {
        INITIAL,        // Ready to print
        PRINTING,       // Currently printing
        SUCCESS,        // Print successful
        FAILED          // Print failed
    }

    // Configuration constants
    private static final int CANCEL_BUTTON_DELAY_MS = 5000;  // 5 seconds
    private static final int AUTO_DISMISS_SUCCESS_DELAY_MS = 2000;  // 2 seconds
    private static final int MAX_RETRY_ATTEMPTS = 2;

    // Dialog components
    private AlertDialog dialog;
    private Context context;
    private View dialogView;

    // Views
    private TextView tvDialogIcon;
    private TextView tvTransactionSummary;
    private TextView tvStatusIcon;
    private TextView tvPrintStatus;
    private TextView tvRetryInfo;
    private ProgressBar progressSpinner;
    private MaterialButton btnPrintReceipt;
    private MaterialButton btnPrintLater;
    private MaterialButton btnViewReceipt;
    private MaterialButton btnContinue;
    private MaterialButton btnCancelPrint;
    private MaterialButton btnRetryPrint;
    private MaterialButton btnDone;
    private View layoutRetryActions;

    // State management
    private DialogState currentState = DialogState.INITIAL;
    private int retryAttempts = 0;
    private String receiptContent;
    private TransactionData transactionData;

    // Handlers and runnables
    private Handler mainHandler;
    private Runnable cancelButtonRunnable;
    private Runnable autoDismissRunnable;

    // Listener interface
    public interface SmartDialogListener {
        void onPrintRequested(String receiptContent, int attemptNumber);
        void onPrintLater(String receiptContent);
        void onViewReceipt(String receiptContent);
        void onDialogDismissed();
        boolean isPrinterConnected();
    }

    private SmartDialogListener listener;

    // Transaction data holder
    public static class TransactionData {
        public String transactionId;
        public double totalValue;
        public double totalWeight;
        public int materialCount;
        public String formattedValue;
        public String formattedWeight;

        public TransactionData(String transactionId, double totalValue, double totalWeight, int materialCount) {
            this.transactionId = transactionId;
            this.totalValue = totalValue;
            this.totalWeight = totalWeight;
            this.materialCount = materialCount;

            DecimalFormat currencyFormat = new DecimalFormat("KSh #,##0.00");
            DecimalFormat weightFormat = new DecimalFormat("0.00");

            this.formattedValue = currencyFormat.format(totalValue);
            this.formattedWeight = weightFormat.format(totalWeight) + " kg";
        }

        public String getSummaryText() {
            return formattedValue + " saved successfully";
        }

        public String getDetailText() {
            return materialCount + " materials • " + formattedWeight + " • " + formattedValue;
        }
    }

    public SmartTransactionDialog(@NonNull Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        initializeDialog();
    }

    private void initializeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // Inflate custom layout
        LayoutInflater inflater = LayoutInflater.from(context);
        dialogView = inflater.inflate(R.layout.dialog_smart_transaction_complete, null);

        // Initialize views
        initializeViews();

        // Setup click listeners
        setupClickListeners();

        // Build dialog
        builder.setView(dialogView);
        builder.setCancelable(false); // Prevent accidental dismissal

        dialog = builder.create();

        // Set dialog window properties
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Log.d(TAG, "Smart dialog initialized successfully");
    }

    private void initializeViews() {
        tvDialogIcon = dialogView.findViewById(R.id.tv_dialog_icon);
        tvTransactionSummary = dialogView.findViewById(R.id.tv_transaction_summary);
        tvStatusIcon = dialogView.findViewById(R.id.tv_status_icon);
        tvPrintStatus = dialogView.findViewById(R.id.tv_print_status);
        tvRetryInfo = dialogView.findViewById(R.id.tv_retry_info);

        progressSpinner = dialogView.findViewById(R.id.progress_spinner);

        btnPrintReceipt = dialogView.findViewById(R.id.btn_print_receipt);
        btnPrintLater = dialogView.findViewById(R.id.btn_print_later);
        btnViewReceipt = dialogView.findViewById(R.id.btn_view_receipt);
        btnContinue = dialogView.findViewById(R.id.btn_continue);
        btnCancelPrint = dialogView.findViewById(R.id.btn_cancel_print);
        btnRetryPrint = dialogView.findViewById(R.id.btn_retry_print);
        btnDone = dialogView.findViewById(R.id.btn_done);

        layoutRetryActions = dialogView.findViewById(R.id.layout_retry_actions);
    }

    private void setupClickListeners() {
        btnPrintReceipt.setOnClickListener(v -> startPrinting());
        btnPrintLater.setOnClickListener(v -> handlePrintLater());
        btnViewReceipt.setOnClickListener(v -> handleViewReceipt());
        btnContinue.setOnClickListener(v -> dismissDialog());
        btnCancelPrint.setOnClickListener(v -> handleCancelPrint());
        btnRetryPrint.setOnClickListener(v -> handleRetryPrint());
        btnDone.setOnClickListener(v -> dismissDialog());
    }

    public void show(TransactionData transactionData, String receiptContent, SmartDialogListener listener) {
        this.transactionData = transactionData;
        this.receiptContent = receiptContent;
        this.listener = listener;

        // Update transaction summary
        if (tvTransactionSummary != null) {
            tvTransactionSummary.setText(transactionData.getSummaryText());
        }

        // Set initial state
        setState(DialogState.INITIAL);

        // Show dialog
        dialog.show();

        Log.d(TAG, "Smart dialog shown for transaction: " + transactionData.transactionId);
    }

    private void startPrinting() {
        if (listener == null || !listener.isPrinterConnected()) {
            handlePrintFailure("Printer not connected", false);
            return;
        }

        retryAttempts++;
        setState(DialogState.PRINTING);

        // Start cancel button timer
        startCancelButtonTimer();

        // Request print from listener
        listener.onPrintRequested(receiptContent, retryAttempts);

        Log.d(TAG, "Print requested - attempt " + retryAttempts + "/" + MAX_RETRY_ATTEMPTS);
    }

    public void onPrintSuccess() {
        if (currentState != DialogState.PRINTING) {
            Log.w(TAG, "Received print success in wrong state: " + currentState);
            return;
        }

        cancelTimers();
        setState(DialogState.SUCCESS);

        // Auto-dismiss after success delay
        autoDismissRunnable = this::dismissDialog;
        mainHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_SUCCESS_DELAY_MS);

        Log.d(TAG, "Print success - auto-dismissing in " + AUTO_DISMISS_SUCCESS_DELAY_MS + "ms");
    }

    public void onPrintFailure(String errorMessage) {
        if (currentState != DialogState.PRINTING) {
            Log.w(TAG, "Received print failure in wrong state: " + currentState);
            return;
        }

        handlePrintFailure(errorMessage, true);
    }

    private void handlePrintFailure(String errorMessage, boolean allowRetry) {
        cancelTimers();

        boolean canRetry = allowRetry && retryAttempts < MAX_RETRY_ATTEMPTS;

        setState(DialogState.FAILED);

        // Update status message
        String statusMessage = "Print failed: " + errorMessage;
        if (canRetry) {
            statusMessage += " (Can retry)";
        }

        if (tvPrintStatus != null) {
            tvPrintStatus.setText(statusMessage);
        }

        // Show/hide retry actions based on retry availability
        if (layoutRetryActions != null) {
            layoutRetryActions.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        }

        // Update retry button text
        if (btnRetryPrint != null && canRetry) {
            btnRetryPrint.setText("🔄 Retry (" + (MAX_RETRY_ATTEMPTS - retryAttempts) + " left)");
        }

        Log.w(TAG, "Print failed: " + errorMessage + " (Can retry: " + canRetry + ")");
    }

    private void handleCancelPrint() {
        cancelTimers();

        // Return to initial state
        setState(DialogState.INITIAL);

        Log.d(TAG, "Print cancelled by user");
    }

    private void handleRetryPrint() {
        if (retryAttempts >= MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "Maximum retry attempts reached");
            return;
        }

        startPrinting();
    }

    // Update the handlePrintLater method in SmartTransactionDialog
    private void handlePrintLater() {
        // Save the job to print history as QUEUED
        if (transactionData != null && receiptContent != null) {
            PrintHistoryManager historyManager = PrintHistoryManager.getInstance(context);

            PrintHistoryManager.PrintJob job = new PrintHistoryManager.PrintJob();
            job.jobId = "print_later_" + System.currentTimeMillis();
            job.jobType = PrintHistoryManager.PrintJobType.RECEIPT;
            job.contentPreview = receiptContent;
            job.status = PrintHistoryManager.PrintJobStatus.QUEUED;
            job.createdAt = new java.util.Date();
            job.transactionId = transactionData.transactionId;
            job.userId = "current_user"; // Set appropriate user ID

            historyManager.addPrintJob(job);

            Log.d(TAG, "Receipt saved for later printing: " + job.jobId);
        }

        if (listener != null) {
            listener.onPrintLater(receiptContent);
        }
        dismissDialog();
    }

    private void handleViewReceipt() {
        if (listener != null) {
            listener.onViewReceipt(receiptContent);
        }
    }

    private void setState(DialogState newState) {
        if (currentState == newState) return;

        DialogState previousState = currentState;
        currentState = newState;

        updateUIForState();

        Log.d(TAG, "State changed: " + previousState + " → " + newState);
    }

    private void updateUIForState() {
        switch (currentState) {
            case INITIAL:
                updateUIForInitialState();
                break;
            case PRINTING:
                updateUIForPrintingState();
                break;
            case SUCCESS:
                updateUIForSuccessState();
                break;
            case FAILED:
                updateUIForFailedState();
                break;
        }
    }

    private void updateUIForInitialState() {
        // Status indicator
        if (progressSpinner != null) progressSpinner.setVisibility(View.GONE);
        if (tvStatusIcon != null) {
            tvStatusIcon.setVisibility(View.VISIBLE);
            tvStatusIcon.setText("🖨️");
        }
        if (tvPrintStatus != null) {
            boolean printerConnected = listener != null && listener.isPrinterConnected();
            tvPrintStatus.setText(printerConnected ? "Ready to print receipt" : "Printer not connected");
        }

        // Buttons
        if (btnPrintReceipt != null) btnPrintReceipt.setVisibility(View.VISIBLE);
        if (btnPrintLater != null) btnPrintLater.setVisibility(View.VISIBLE);
        if (btnViewReceipt != null) btnViewReceipt.setVisibility(View.VISIBLE);
        if (btnContinue != null) btnContinue.setVisibility(View.GONE);
        if (btnCancelPrint != null) btnCancelPrint.setVisibility(View.GONE);
        if (layoutRetryActions != null) layoutRetryActions.setVisibility(View.GONE);
        if (tvRetryInfo != null) tvRetryInfo.setVisibility(View.GONE);

        // Enable/disable print button based on printer connection
        if (btnPrintReceipt != null && listener != null) {
            btnPrintReceipt.setEnabled(listener.isPrinterConnected());
        }
    }

    private void updateUIForPrintingState() {
        // Status indicator
        if (tvStatusIcon != null) tvStatusIcon.setVisibility(View.GONE);
        if (progressSpinner != null) progressSpinner.setVisibility(View.VISIBLE);
        if (tvPrintStatus != null) {
            String statusText = "Printing receipt...";
            if (retryAttempts > 1) {
                statusText += " (Attempt " + retryAttempts + ")";
            }
            tvPrintStatus.setText(statusText);
        }

        // Buttons
        if (btnPrintReceipt != null) btnPrintReceipt.setVisibility(View.GONE);
        if (btnPrintLater != null) btnPrintLater.setVisibility(View.GONE);
        if (btnViewReceipt != null) btnViewReceipt.setVisibility(View.VISIBLE);
        if (btnContinue != null) btnContinue.setVisibility(View.GONE);
        if (btnCancelPrint != null) btnCancelPrint.setVisibility(View.GONE); // Will show after timeout
        if (layoutRetryActions != null) layoutRetryActions.setVisibility(View.GONE);

        // Show retry info if this is a retry attempt
        if (tvRetryInfo != null) {
            if (retryAttempts > 1) {
                tvRetryInfo.setText("Retry attempt " + (retryAttempts - 1) + " of " + (MAX_RETRY_ATTEMPTS - 1));
                tvRetryInfo.setVisibility(View.VISIBLE);
            } else {
                tvRetryInfo.setVisibility(View.GONE);
            }
        }
    }

    private void updateUIForSuccessState() {
        // Status indicator
        if (progressSpinner != null) progressSpinner.setVisibility(View.GONE);
        if (tvStatusIcon != null) {
            tvStatusIcon.setVisibility(View.VISIBLE);
            tvStatusIcon.setText("✅");
        }
        if (tvPrintStatus != null) tvPrintStatus.setText("Receipt printed successfully");

        // Buttons
        if (btnPrintReceipt != null) btnPrintReceipt.setVisibility(View.GONE);
        if (btnPrintLater != null) btnPrintLater.setVisibility(View.GONE);
        if (btnViewReceipt != null) btnViewReceipt.setVisibility(View.VISIBLE);
        if (btnContinue != null) btnContinue.setVisibility(View.VISIBLE);
        if (btnCancelPrint != null) btnCancelPrint.setVisibility(View.GONE);
        if (layoutRetryActions != null) layoutRetryActions.setVisibility(View.GONE);
        if (tvRetryInfo != null) tvRetryInfo.setVisibility(View.GONE);
    }

    private void updateUIForFailedState() {
        // Status indicator
        if (progressSpinner != null) progressSpinner.setVisibility(View.GONE);
        if (tvStatusIcon != null) {
            tvStatusIcon.setVisibility(View.VISIBLE);
            tvStatusIcon.setText("❌");
        }
        // Print status text is set in handlePrintFailure()

        // Buttons
        if (btnPrintReceipt != null) btnPrintReceipt.setVisibility(View.GONE);
        if (btnPrintLater != null) btnPrintLater.setVisibility(View.VISIBLE);
        if (btnViewReceipt != null) btnViewReceipt.setVisibility(View.VISIBLE);
        if (btnContinue != null) btnContinue.setVisibility(View.GONE);
        if (btnCancelPrint != null) btnCancelPrint.setVisibility(View.GONE);
        if (tvRetryInfo != null) tvRetryInfo.setVisibility(View.GONE);

        // layoutRetryActions visibility is controlled in handlePrintFailure()
    }

    private void startCancelButtonTimer() {
        cancelButtonRunnable = () -> {
            if (currentState == DialogState.PRINTING && btnCancelPrint != null) {
                btnCancelPrint.setVisibility(View.VISIBLE);
                Log.d(TAG, "Cancel button shown after timeout");
            }
        };
        mainHandler.postDelayed(cancelButtonRunnable, CANCEL_BUTTON_DELAY_MS);
    }

    private void cancelTimers() {
        if (cancelButtonRunnable != null) {
            mainHandler.removeCallbacks(cancelButtonRunnable);
            cancelButtonRunnable = null;
        }

        if (autoDismissRunnable != null) {
            mainHandler.removeCallbacks(autoDismissRunnable);
            autoDismissRunnable = null;
        }
    }

    private void dismissDialog() {
        cancelTimers();

        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }

        if (listener != null) {
            listener.onDialogDismissed();
        }

        Log.d(TAG, "Dialog dismissed");
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public void dismiss() {
        dismissDialog();
    }

    // Public method to check current state
    public DialogState getCurrentState() {
        return currentState;
    }

    // Public method to get retry count
    public int getRetryAttempts() {
        return retryAttempts;
    }

    // Public method to check if more retries are available
    public boolean canRetry() {
        return retryAttempts < MAX_RETRY_ATTEMPTS;
    }


}