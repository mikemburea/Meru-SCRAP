package com.example.meruscrap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;

public class TransactionsDBHelper extends SQLiteOpenHelper {
    private static final String TAG = "TransactionsDBHelper";

    // Database Info
    private static final String DATABASE_NAME = "MeruScrapTransactions.db";
    private static final int DATABASE_VERSION = 1;

    // Singleton instance with proper synchronization
    private static TransactionsDBHelper instance;
    private static final Object LOCK = new Object();

    // Table Names
    private static final String TABLE_TRANSACTIONS = "transactions";
    private static final String TABLE_TRANSACTION_ITEMS = "transaction_items";

    // Transactions Table Columns
    private static final String COLUMN_TRANSACTION_ID = "id";
    private static final String COLUMN_TRANSACTION_REF = "transaction_ref";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_TOTAL_WEIGHT = "total_weight";
    private static final String COLUMN_TOTAL_VALUE = "total_value";
    private static final String COLUMN_MATERIAL_COUNT = "material_count";
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_NOTES = "notes";

    // Transaction Items Table Columns
    private static final String COLUMN_ITEM_ID = "id";
    private static final String COLUMN_ITEM_TRANSACTION_ID = "transaction_id";
    private static final String COLUMN_MATERIAL_NAME = "material_name";
    private static final String COLUMN_WEIGHT = "weight";
    private static final String COLUMN_PRICE_PER_KG = "price_per_kg";
    private static final String COLUMN_ITEM_TOTAL_VALUE = "total_value";
    private static final String COLUMN_ITEM_TIMESTAMP = "timestamp";
    private static final String COLUMN_ITEM_NOTES = "notes";

    // Create Tables SQL
    private static final String CREATE_TRANSACTIONS_TABLE =
            "CREATE TABLE " + TABLE_TRANSACTIONS + "(" +
                    COLUMN_TRANSACTION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    COLUMN_TRANSACTION_REF + " TEXT UNIQUE NOT NULL," +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL," +
                    COLUMN_TOTAL_WEIGHT + " REAL NOT NULL," +
                    COLUMN_TOTAL_VALUE + " REAL NOT NULL," +
                    COLUMN_MATERIAL_COUNT + " INTEGER NOT NULL," +
                    COLUMN_STATUS + " TEXT DEFAULT 'COMPLETED'," +
                    COLUMN_NOTES + " TEXT" +
                    ")";

    private static final String CREATE_TRANSACTION_ITEMS_TABLE =
            "CREATE TABLE " + TABLE_TRANSACTION_ITEMS + "(" +
                    COLUMN_ITEM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    COLUMN_ITEM_TRANSACTION_ID + " INTEGER NOT NULL," +
                    COLUMN_MATERIAL_NAME + " TEXT NOT NULL," +
                    COLUMN_WEIGHT + " REAL NOT NULL," +
                    COLUMN_PRICE_PER_KG + " REAL NOT NULL," +
                    COLUMN_ITEM_TOTAL_VALUE + " REAL NOT NULL," +
                    COLUMN_ITEM_TIMESTAMP + " INTEGER NOT NULL," +
                    COLUMN_ITEM_NOTES + " TEXT," +
                    "FOREIGN KEY(" + COLUMN_ITEM_TRANSACTION_ID + ") REFERENCES " +
                    TABLE_TRANSACTIONS + "(" + COLUMN_TRANSACTION_ID + ") ON DELETE CASCADE" +
                    ")";

    // Thread-safe singleton pattern
    public static TransactionsDBHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new TransactionsDBHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private TransactionsDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creating transactions database tables");
        db.execSQL(CREATE_TRANSACTIONS_TABLE);
        db.execSQL(CREATE_TRANSACTION_ITEMS_TABLE);

        // Create indexes for better performance
        db.execSQL("CREATE INDEX idx_transaction_timestamp ON " + TABLE_TRANSACTIONS + "(" + COLUMN_TIMESTAMP + ")");
        db.execSQL("CREATE INDEX idx_transaction_status ON " + TABLE_TRANSACTIONS + "(" + COLUMN_STATUS + ")");
        db.execSQL("CREATE INDEX idx_item_transaction_id ON " + TABLE_TRANSACTION_ITEMS + "(" + COLUMN_ITEM_TRANSACTION_ID + ")");
        db.execSQL("CREATE INDEX idx_item_material ON " + TABLE_TRANSACTION_ITEMS + "(" + COLUMN_MATERIAL_NAME + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        if (oldVersion < newVersion) {
            // Drop existing tables and recreate (for development)
            // In production, you'd want to migrate data
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTION_ITEMS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTIONS);
            onCreate(db);
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    // FIXED: Thread-safe database operations - don't close the database manually
    public synchronized long saveTransaction(Transaction transaction, List<TransactionMaterial> transactionMaterials) {
        SQLiteDatabase db = null;
        long transactionId = -1;

        try {
            db = this.getWritableDatabase();
            db.beginTransaction();

            // Insert transaction header
            ContentValues transactionValues = new ContentValues();
            transactionValues.put(COLUMN_TRANSACTION_REF, transaction.getTransactionId());
            transactionValues.put(COLUMN_TIMESTAMP, transaction.getTimestamp());
            transactionValues.put(COLUMN_TOTAL_WEIGHT, transaction.getTotalWeight());
            transactionValues.put(COLUMN_TOTAL_VALUE, transaction.getTotalValue());
            transactionValues.put(COLUMN_MATERIAL_COUNT, transaction.getMaterialCount());
            transactionValues.put(COLUMN_STATUS, transaction.getStatus());
            transactionValues.put(COLUMN_NOTES, transaction.getNotes());

            transactionId = db.insert(TABLE_TRANSACTIONS, null, transactionValues);

            if (transactionId == -1) {
                Log.e(TAG, "Failed to insert transaction");
                return -1;
            }

            Log.d(TAG, "Inserted transaction with ID: " + transactionId);

            // Insert transaction items
            for (TransactionMaterial material : transactionMaterials) {
                ContentValues itemValues = new ContentValues();
                itemValues.put(COLUMN_ITEM_TRANSACTION_ID, transactionId);
                itemValues.put(COLUMN_MATERIAL_NAME, material.getMaterialName());
                itemValues.put(COLUMN_WEIGHT, material.getWeight());
                itemValues.put(COLUMN_PRICE_PER_KG, material.getPricePerKg());
                itemValues.put(COLUMN_ITEM_TOTAL_VALUE, material.getValue());
                itemValues.put(COLUMN_ITEM_TIMESTAMP, material.getTimestamp());

                long itemId = db.insert(TABLE_TRANSACTION_ITEMS, null, itemValues);
                if (itemId == -1) {
                    Log.e(TAG, "Failed to insert transaction item: " + material.getMaterialName());
                    return -1;
                }
                Log.d(TAG, "Inserted transaction item: " + material.getMaterialName() + " with ID: " + itemId);
            }

            db.setTransactionSuccessful();
            Log.d(TAG, "Transaction saved successfully with " + transactionMaterials.size() + " items");

        } catch (Exception e) {
            Log.e(TAG, "Error saving transaction", e);
            transactionId = -1;
        } finally {
            if (db != null) {
                try {
                    db.endTransaction();
                } catch (Exception e) {
                    Log.e(TAG, "Error ending transaction", e);
                }
                // Don't close the database - let SQLiteOpenHelper manage it
            }
        }

        return transactionId;
    }

    // FIXED: Thread-safe read operations
    public synchronized List<Transaction> getAllTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            String query = "SELECT * FROM " + TABLE_TRANSACTIONS + " ORDER BY " + COLUMN_TIMESTAMP + " DESC";
            cursor = db.rawQuery(query, null);

            if (cursor.moveToFirst()) {
                do {
                    Transaction transaction = cursorToTransaction(cursor);
                    transactions.add(transaction);
                } while (cursor.moveToNext());
            }

            Log.d(TAG, "Retrieved " + transactions.size() + " transactions");

        } catch (Exception e) {
            Log.e(TAG, "Error getting transactions", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor", e);
                }
            }
            // Don't close the database - let SQLiteOpenHelper manage it
        }

        return transactions;
    }

    // FIXED: Thread-safe single transaction retrieval
    public synchronized Transaction getTransaction(long transactionId) {
        SQLiteDatabase db = null;
        Transaction transaction = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            String query = "SELECT * FROM " + TABLE_TRANSACTIONS + " WHERE " + COLUMN_TRANSACTION_ID + " = ?";
            cursor = db.rawQuery(query, new String[]{String.valueOf(transactionId)});

            if (cursor.moveToFirst()) {
                transaction = cursorToTransaction(cursor);
                // Load transaction items
                transaction.setItems(getTransactionItems(transactionId));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting transaction by ID: " + transactionId, e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor", e);
                }
            }
            // Don't close the database
        }

        return transaction;
    }

    // FIXED: Thread-safe transaction items retrieval
    public synchronized List<TransactionItem> getTransactionItems(long transactionId) {
        List<TransactionItem> items = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            String query = "SELECT * FROM " + TABLE_TRANSACTION_ITEMS + " WHERE " + COLUMN_ITEM_TRANSACTION_ID + " = ?";
            cursor = db.rawQuery(query, new String[]{String.valueOf(transactionId)});

            if (cursor.moveToFirst()) {
                do {
                    TransactionItem item = cursorToTransactionItem(cursor);
                    items.add(item);
                } while (cursor.moveToNext());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting transaction items for transaction ID: " + transactionId, e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor", e);
                }
            }
            // Don't close the database
        }

        return items;
    }

    // FIXED: Thread-safe transaction statistics
    public synchronized TransactionStats getTransactionStats() {
        SQLiteDatabase db = null;
        TransactionStats stats = new TransactionStats();
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            String query = "SELECT COUNT(*) as count, SUM(" + COLUMN_TOTAL_WEIGHT + ") as total_weight, " +
                    "SUM(" + COLUMN_TOTAL_VALUE + ") as total_value FROM " + TABLE_TRANSACTIONS +
                    " WHERE " + COLUMN_STATUS + " = 'COMPLETED'";

            cursor = db.rawQuery(query, null);

            if (cursor.moveToFirst()) {
                stats.totalTransactions = cursor.getInt(cursor.getColumnIndexOrThrow("count"));
                stats.totalWeight = cursor.getDouble(cursor.getColumnIndexOrThrow("total_weight"));
                stats.totalValue = cursor.getDouble(cursor.getColumnIndexOrThrow("total_value"));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting transaction stats", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor", e);
                }
            }
            // Don't close the database
        }

        return stats;
    }

    // FIXED: The main problematic method - now thread-safe
    public synchronized TodayStats getTodayTransactionStats() {
        SQLiteDatabase db = null;
        TodayStats stats = new TodayStats();
        Cursor cursor = null;

        try {
            // Get start and end timestamps for today
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startOfDay = calendar.getTimeInMillis();

            calendar.add(Calendar.DAY_OF_MONTH, 1);
            long endOfDay = calendar.getTimeInMillis();

            db = this.getReadableDatabase();

            // Query for today's transactions
            String query = "SELECT COUNT(*) as count, SUM(" + COLUMN_TOTAL_WEIGHT + ") as total_weight, " +
                    "SUM(" + COLUMN_TOTAL_VALUE + ") as total_value, MAX(" + COLUMN_TIMESTAMP + ") as last_transaction " +
                    "FROM " + TABLE_TRANSACTIONS +
                    " WHERE " + COLUMN_STATUS + " = 'COMPLETED' AND " +
                    COLUMN_TIMESTAMP + " >= ? AND " + COLUMN_TIMESTAMP + " < ?";

            cursor = db.rawQuery(query, new String[]{String.valueOf(startOfDay), String.valueOf(endOfDay)});

            if (cursor.moveToFirst()) {
                stats.transactionCount = cursor.getInt(cursor.getColumnIndexOrThrow("count"));
                stats.totalWeight = cursor.getDouble(cursor.getColumnIndexOrThrow("total_weight"));
                stats.totalValue = cursor.getDouble(cursor.getColumnIndexOrThrow("total_value"));
                stats.lastTransactionTime = cursor.getLong(cursor.getColumnIndexOrThrow("last_transaction"));
            }

            // Get yesterday's total for comparison
            stats.yesterdayTotal = getYesterdayTotalInternal(db);

        } catch (Exception e) {
            Log.e(TAG, "Error getting today's transaction stats", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor", e);
                }
            }
            // Don't close the database - let SQLiteOpenHelper manage it
        }

        return stats;
    }

    // FIXED: Internal method to get yesterday's total using existing connection
    private double getYesterdayTotalInternal(SQLiteDatabase db) {
        double yesterdayTotal = 0.0;
        Cursor cursor = null;

        try {
            // Get yesterday's timestamps
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startOfYesterday = calendar.getTimeInMillis();

            calendar.add(Calendar.DAY_OF_MONTH, 1);
            long endOfYesterday = calendar.getTimeInMillis();

            String query = "SELECT SUM(" + COLUMN_TOTAL_VALUE + ") as total FROM " + TABLE_TRANSACTIONS +
                    " WHERE " + COLUMN_STATUS + " = 'COMPLETED' AND " +
                    COLUMN_TIMESTAMP + " >= ? AND " + COLUMN_TIMESTAMP + " < ?";

            cursor = db.rawQuery(query, new String[]{String.valueOf(startOfYesterday), String.valueOf(endOfYesterday)});

            if (cursor.moveToFirst()) {
                yesterdayTotal = cursor.getDouble(cursor.getColumnIndexOrThrow("total"));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting yesterday's total", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor", e);
                }
            }
        }

        return yesterdayTotal;
    }

    // Separate method if you need yesterday's total independently
    private double getYesterdayTotal() {
        SQLiteDatabase db = null;
        double yesterdayTotal = 0.0;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            yesterdayTotal = getYesterdayTotalInternal(db);
        } catch (Exception e) {
            Log.e(TAG, "Error getting yesterday's total", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor", e);
                }
            }
        }

        return yesterdayTotal;
    }

    // Helper methods - unchanged
    private Transaction cursorToTransaction(Cursor cursor) {
        return new Transaction(
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TRANSACTION_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TRANSACTION_REF)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_TOTAL_WEIGHT)),
                cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_TOTAL_VALUE)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MATERIAL_COUNT)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTES))
        );
    }

    private TransactionItem cursorToTransactionItem(Cursor cursor) {
        return new TransactionItem(
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ITEM_ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ITEM_TRANSACTION_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MATERIAL_NAME)),
                cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_WEIGHT)),
                cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PRICE_PER_KG)),
                cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_ITEM_TOTAL_VALUE)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ITEM_TIMESTAMP)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ITEM_NOTES))
        );
    }

    // Stats helper classes - unchanged
    public static class TransactionStats {
        public int totalTransactions = 0;
        public double totalWeight = 0.0;
        public double totalValue = 0.0;

        public String getFormattedTotalWeight() {
            return String.format(java.util.Locale.getDefault(), "%.2f kg", totalWeight);
        }

        public String getFormattedTotalValue() {
            return String.format(java.util.Locale.getDefault(), "KSH %.2f", totalValue);
        }
    }

    public static class TodayStats {
        public int transactionCount = 0;
        public double totalWeight = 0.0;
        public double totalValue = 0.0;
        public double yesterdayTotal = 0.0;
        public long lastTransactionTime = 0;

        public String getFormattedTotalValue() {
            return String.format(java.util.Locale.getDefault(), "KSh %,.0f", totalValue);
        }

        public String getFormattedTransactionCount() {
            return transactionCount + " Today";
        }

        public String getGrowthPercentage() {
            if (yesterdayTotal == 0) {
                return totalValue > 0 ? "↗ New Sales!" : "No change";
            }

            double growth = ((totalValue - yesterdayTotal) / yesterdayTotal) * 100;
            String arrow = growth >= 0 ? "↗" : "↘";
            String sign = growth >= 0 ? "+" : "";

            return String.format(java.util.Locale.getDefault(), "%s %s%.1f%% from yesterday",
                    arrow, sign, Math.abs(growth));
        }

        public String getGrowthColor() {
            if (yesterdayTotal == 0) {
                return totalValue > 0 ? "#27AE60" : "#546E7A";
            }
            return totalValue >= yesterdayTotal ? "#27AE60" : "#F44336";
        }

        public String getTimeSinceLastTransaction() {
            if (lastTransactionTime == 0) {
                return "No transactions yet";
            }

            long currentTime = System.currentTimeMillis();
            long diff = currentTime - lastTransactionTime;

            long minutes = diff / (1000 * 60);
            long hours = minutes / 60;

            if (minutes < 1) {
                return "Just now";
            } else if (minutes < 60) {
                return "Last: " + minutes + " min" + (minutes > 1 ? "s" : "") + " ago";
            } else if (hours < 24) {
                return "Last: " + hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            } else {
                return "Last: Today";
            }
        }
    }
}