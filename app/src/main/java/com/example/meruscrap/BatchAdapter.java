package com.example.meruscrap;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying weighing batches in accumulative weighing mode
 * Used in the batch weighing card to show individual weighing batches
 */
public class BatchAdapter extends RecyclerView.Adapter<BatchAdapter.BatchViewHolder> {
    private List<WeighingBatch> batches;
    private OnBatchRemovedListener listener;
    private DecimalFormat weightFormat;
    private DecimalFormat currencyFormat;
    private SimpleDateFormat timeFormat;

    public interface OnBatchRemovedListener {
        void onBatchRemoved(int position);
    }

    public BatchAdapter(List<WeighingBatch> batches, OnBatchRemovedListener listener) {
        this.batches = batches;
        this.listener = listener;
        this.weightFormat = new DecimalFormat("0.00");
        this.currencyFormat = new DecimalFormat("KSH #,##0.00");
        this.timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    }

    @NonNull
    @Override
    public BatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_weighing_batch, parent, false);
        return new BatchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BatchViewHolder holder, int position) {
        WeighingBatch batch = batches.get(position);
        holder.bind(batch, position + 1); // 1-based numbering for display
    }

    @Override
    public int getItemCount() {
        return batches.size();
    }

    /**
     * Update the batches list and refresh the adapter
     */
    public void updateBatches(List<WeighingBatch> newBatches) {
        this.batches.clear();
        if (newBatches != null) {
            this.batches.addAll(newBatches);
        }
        notifyDataSetChanged();
    }

    /**
     * Add a new batch to the list
     */
    public void addBatch(WeighingBatch batch) {
        batches.add(batch);
        notifyItemInserted(batches.size() - 1);
    }

    /**
     * Remove batch at position
     */
    public void removeBatch(int position) {
        if (position >= 0 && position < batches.size()) {
            batches.remove(position);
            notifyItemRemoved(position);
        }
    }

    /**
     * Get batch at position
     */
    public WeighingBatch getBatchAt(int position) {
        if (position >= 0 && position < batches.size()) {
            return batches.get(position);
        }
        return null;
    }

    /**
     * Check if the list is empty
     */
    public boolean isEmpty() {
        return batches.isEmpty();
    }

    /**
     * Get total weight of all batches
     */
    public double getTotalWeight() {
        double total = 0.0;
        for (WeighingBatch batch : batches) {
            total += batch.getWeight();
        }
        return total;
    }

    /**
     * Get total value of all batches
     */
    public double getTotalValue() {
        double total = 0.0;
        for (WeighingBatch batch : batches) {
            total += batch.getValue();
        }
        return total;
    }

    public class BatchViewHolder extends RecyclerView.ViewHolder {
        private TextView tvBatchNumber;
        private TextView tvBatchWeight;
        private TextView tvBatchValue;
        private TextView tvBatchTime;
        private MaterialButton btnRemoveBatch;

        public BatchViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBatchNumber = itemView.findViewById(R.id.tv_batch_number);
            tvBatchWeight = itemView.findViewById(R.id.tv_batch_weight);
            tvBatchValue = itemView.findViewById(R.id.tv_batch_value);
            tvBatchTime = itemView.findViewById(R.id.tv_batch_time);
            btnRemoveBatch = itemView.findViewById(R.id.btn_remove_batch);
        }

        public void bind(WeighingBatch batch, int displayNumber) {
            tvBatchNumber.setText("Batch " + displayNumber);
            tvBatchWeight.setText(weightFormat.format(batch.getWeight()) + " kg");
            tvBatchValue.setText(currencyFormat.format(batch.getValue()));
            tvBatchTime.setText(timeFormat.format(new Date(batch.getTimestamp())));

            btnRemoveBatch.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBatchRemoved(getAdapterPosition());
                }
            });

            // Accessibility
            itemView.setContentDescription(
                    "Batch " + displayNumber + ", " +
                            weightFormat.format(batch.getWeight()) + " kilograms, " +
                            currencyFormat.format(batch.getValue()) + ", " +
                            "weighed at " + timeFormat.format(new Date(batch.getTimestamp())) +
                            ", tap remove button to delete"
            );
        }
    }
}