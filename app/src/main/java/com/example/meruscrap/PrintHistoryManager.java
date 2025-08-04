package com.example.meruscrap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PrintHistoryManager extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "print_history.db";
    private static final int DATABASE_VERSION = 2;

    // Table names
    private static final String TABLE_PRINT_JOBS = "print_jobs";
    private static final String TABLE_PRINTER_EVENTS = "printer_events";

    // Print jobs table columns
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_JOB_ID = "job_id";
    private static final String COLUMN_JOB_TYPE = "job_type";
    private static final String COLUMN_CONTENT_PREVIEW = "content_preview";
    private static final String COLUMN_PRINTER_ADDRESS = "printer_address";
    private static final String COLUMN_PRINTER_NAME = "printer_name";
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_STARTED_AT = "started_at";
    private static final String COLUMN_COMPLETED_AT = "completed_at";
    private static final String COLUMN_ERROR_MESSAGE = "error_message";
    private static final String COLUMN_RETRY_COUNT = "retry_count";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_TRANSACTION_ID = "transaction_id";

    // Printer events table columns
    private static final String COLUMN_EVENT_ID = "event_id";
    private static final String COLUMN_EVENT_TYPE = "event_type";
    private static final String COLUMN_EVENT_TIMESTAMP = "event_timestamp";
    private static final String COLUMN_EVENT_DETAILS = "event_details";

    private static PrintHistoryManager instance;

    public static synchronized PrintHistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new PrintHistoryManager(context.getApplicationContext());
        }
        return instance;
    }

    private PrintHistoryManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createPrintJobsTable = "CREATE TABLE " + TABLE_PRINT_JOBS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_JOB_ID + " TEXT UNIQUE NOT NULL,"
                + COLUMN_JOB_TYPE + " TEXT NOT NULL,"
                + COLUMN_CONTENT_PREVIEW + " TEXT,"
                + COLUMN_PRINTER_ADDRESS + " TEXT,"
                + COLUMN_PRINTER_NAME + " TEXT,"
                + COLUMN_STATUS + " TEXT NOT NULL,"
                + COLUMN_CREATED_AT + " INTEGER NOT NULL,"
                + COLUMN_STARTED_AT + " INTEGER,"
                + COLUMN_COMPLETED_AT + " INTEGER,"
                + COLUMN_ERROR_MESSAGE + " TEXT,"
                + COLUMN_RETRY_COUNT + " INTEGER DEFAULT 0,"
                + COLUMN_USER_ID + " TEXT,"
                + COLUMN_TRANSACTION_ID + " TEXT"
                + ")";

        String createPrinterEventsTable = "CREATE TABLE " + TABLE_PRINTER_EVENTS + "("
                + COLUMN_EVENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_EVENT_TYPE + " TEXT NOT NULL,"
                + COLUMN_PRINTER_ADDRESS + " TEXT,"
                + COLUMN_PRINTER_NAME + " TEXT,"
                + COLUMN_EVENT_TIMESTAMP + " INTEGER NOT NULL,"
                + COLUMN_EVENT_DETAILS + " TEXT"
                + ")";

        db.execSQL(createPrintJobsTable);
        db.execSQL(createPrinterEventsTable);

        // Create indexes for better performance
        db.execSQL("CREATE INDEX idx_print_jobs_created_at ON " + TABLE_PRINT_JOBS + "(" + COLUMN_CREATED_AT + ")");
        db.execSQL("CREATE INDEX idx_print_jobs_status ON " + TABLE_PRINT_JOBS + "(" + COLUMN_STATUS + ")");
        db.execSQL("CREATE INDEX idx_printer_events_timestamp ON " + TABLE_PRINTER_EVENTS + "(" + COLUMN_EVENT_TIMESTAMP + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add new columns for version 2
            db.execSQL("ALTER TABLE " + TABLE_PRINT_JOBS + " ADD COLUMN " + COLUMN_USER_ID + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_PRINT_JOBS + " ADD COLUMN " + COLUMN_TRANSACTION_ID + " TEXT");
        }
    }

    // Print job operations
    public long addPrintJob(PrintJob job) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_JOB_ID, job.jobId);
        values.put(COLUMN_JOB_TYPE, job.jobType.name());
        values.put(COLUMN_CONTENT_PREVIEW, job.contentPreview);
        values.put(COLUMN_PRINTER_ADDRESS, job.printerAddress);
        values.put(COLUMN_PRINTER_NAME, job.printerName);
        values.put(COLUMN_STATUS, job.status.name());
        values.put(COLUMN_CREATED_AT, job.createdAt.getTime());
        values.put(COLUMN_USER_ID, job.userId);
        values.put(COLUMN_TRANSACTION_ID, job.transactionId);

        long id = db.insert(TABLE_PRINT_JOBS, null, values);
        db.close();

        return id;
    }

    // Convenience method for simple job logging
    public void addPrintJob(String contentPreview, PrintJobType jobType, PrintJobStatus status) {
        PrintJob job = new PrintJob();
        job.jobId = "job_" + System.currentTimeMillis();
        job.jobType = jobType;
        job.contentPreview = contentPreview;
        job.status = status;
        job.createdAt = new Date();

        addPrintJob(job);
    }

    public void updatePrintJobStatus(String jobId, PrintJobStatus status, String errorMessage) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_STATUS, status.name());

        long currentTime = System.currentTimeMillis();

        switch (status) {
            case PRINTING:
                values.put(COLUMN_STARTED_AT, currentTime);
                break;
            case COMPLETED:
                values.put(COLUMN_COMPLETED_AT, currentTime);
                break;
            case FAILED:
                values.put(COLUMN_ERROR_MESSAGE, errorMessage);
                values.put(COLUMN_COMPLETED_AT, currentTime);
                break;
        }

        db.update(TABLE_PRINT_JOBS, values, COLUMN_JOB_ID + " = ?", new String[]{jobId});
        db.close();
    }

    public void incrementRetryCount(String jobId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_PRINT_JOBS + " SET " + COLUMN_RETRY_COUNT + " = " + COLUMN_RETRY_COUNT + " + 1 WHERE " + COLUMN_JOB_ID + " = ?", new String[]{jobId});
        db.close();
    }

    // Printer event operations
    public void logPrinterEvent(PrinterEventType eventType, String printerAddress, String printerName, String details) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_EVENT_TYPE, eventType.name());
        values.put(COLUMN_PRINTER_ADDRESS, printerAddress);
        values.put(COLUMN_PRINTER_NAME, printerName);
        values.put(COLUMN_EVENT_TIMESTAMP, System.currentTimeMillis());
        values.put(COLUMN_EVENT_DETAILS, details);

        db.insert(TABLE_PRINTER_EVENTS, null, values);
        db.close();
    }

    // Query operations
    public List<PrintJob> getAllPrintJobs() {
        return getPrintJobs(null, null, null);
    }

    public List<PrintJob> getPrintJobsByStatus(PrintJobStatus status) {
        return getPrintJobs(COLUMN_STATUS + " = ?", new String[]{status.name()}, COLUMN_CREATED_AT + " DESC");
    }

    public List<PrintJob> getPrintJobsByDateRange(long startTime, long endTime) {
        return getPrintJobs(
                COLUMN_CREATED_AT + " BETWEEN ? AND ?",
                new String[]{String.valueOf(startTime), String.valueOf(endTime)},
                COLUMN_CREATED_AT + " DESC"
        );
    }

    public List<PrintJob> getRecentPrintJobs(int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<PrintJob> jobs = new ArrayList<>();

        String query = "SELECT * FROM " + TABLE_PRINT_JOBS + " ORDER BY " + COLUMN_CREATED_AT + " DESC LIMIT " + limit;
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                jobs.add(cursorToPrintJob(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return jobs;
    }

    private List<PrintJob> getPrintJobs(String selection, String[] selectionArgs, String orderBy) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<PrintJob> jobs = new ArrayList<>();

        Cursor cursor = db.query(TABLE_PRINT_JOBS, null, selection, selectionArgs, null, null, orderBy);

        if (cursor.moveToFirst()) {
            do {
                jobs.add(cursorToPrintJob(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return jobs;
    }

    public List<PrinterEvent> getPrinterEvents(long startTime, long endTime) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<PrinterEvent> events = new ArrayList<>();

        String selection = COLUMN_EVENT_TIMESTAMP + " BETWEEN ? AND ?";
        String[] selectionArgs = {String.valueOf(startTime), String.valueOf(endTime)};

        Cursor cursor = db.query(TABLE_PRINTER_EVENTS, null, selection, selectionArgs, null, null, COLUMN_EVENT_TIMESTAMP + " DESC");

        if (cursor.moveToFirst()) {
            do {
                events.add(cursorToPrinterEvent(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return events;
    }

    // Statistics
    public PrintStatistics getPrintStatistics(long startTime, long endTime) {
        SQLiteDatabase db = this.getReadableDatabase();
        PrintStatistics stats = new PrintStatistics();

        String timeRange = COLUMN_CREATED_AT + " BETWEEN " + startTime + " AND " + endTime;

        // Total jobs
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PRINT_JOBS + " WHERE " + timeRange, null);
        if (cursor.moveToFirst()) {
            stats.totalJobs = cursor.getInt(0);
        }
        cursor.close();

        // Successful jobs
        cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PRINT_JOBS + " WHERE " + timeRange + " AND " + COLUMN_STATUS + " = ?", new String[]{PrintJobStatus.COMPLETED.name()});
        if (cursor.moveToFirst()) {
            stats.successfulJobs = cursor.getInt(0);
        }
        cursor.close();

        // Failed jobs
        cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PRINT_JOBS + " WHERE " + timeRange + " AND " + COLUMN_STATUS + " = ?", new String[]{PrintJobStatus.FAILED.name()});
        if (cursor.moveToFirst()) {
            stats.failedJobs = cursor.getInt(0);
        }
        cursor.close();

        // Jobs by type
        cursor = db.rawQuery("SELECT " + COLUMN_JOB_TYPE + ", COUNT(*) FROM " + TABLE_PRINT_JOBS + " WHERE " + timeRange + " GROUP BY " + COLUMN_JOB_TYPE, null);
        stats.jobsByType = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                JobTypeCount count = new JobTypeCount();
                count.jobType = PrintJobType.valueOf(cursor.getString(0));
                count.count = cursor.getInt(1);
                stats.jobsByType.add(count);
            } while (cursor.moveToNext());
        }
        cursor.close();

        db.close();

        stats.successRate = stats.totalJobs > 0 ? (double) stats.successfulJobs / stats.totalJobs * 100 : 0;

        return stats;
    }

    // Cleanup operations
    public void deleteOldRecords(int daysToKeep) {
        SQLiteDatabase db = this.getWritableDatabase();
        long cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L);

        db.delete(TABLE_PRINT_JOBS, COLUMN_CREATED_AT + " < ?", new String[]{String.valueOf(cutoffTime)});
        db.delete(TABLE_PRINTER_EVENTS, COLUMN_EVENT_TIMESTAMP + " < ?", new String[]{String.valueOf(cutoffTime)});

        db.close();
    }

    public void clearAllHistory() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_PRINT_JOBS, null, null);
        db.delete(TABLE_PRINTER_EVENTS, null, null);
        db.close();
    }

    // FIXED: Safe cursor methods to handle -1 column indexes
    private String getStringSafely(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex >= 0) {
            return cursor.getString(columnIndex);
        }
        return null;
    }

    private long getLongSafely(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex >= 0) {
            return cursor.getLong(columnIndex);
        }
        return 0;
    }

    private int getIntSafely(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex >= 0) {
            return cursor.getInt(columnIndex);
        }
        return 0;
    }

    // FIXED: Safe cursor to PrintJob conversion
    private PrintJob cursorToPrintJob(Cursor cursor) {
        PrintJob job = new PrintJob();

        try {
            job.id = getLongSafely(cursor, COLUMN_ID);
            job.jobId = getStringSafely(cursor, COLUMN_JOB_ID);

            String jobTypeStr = getStringSafely(cursor, COLUMN_JOB_TYPE);
            if (jobTypeStr != null) {
                try {
                    job.jobType = PrintJobType.valueOf(jobTypeStr);
                } catch (IllegalArgumentException e) {
                    job.jobType = PrintJobType.RECEIPT; // Default fallback
                }
            } else {
                job.jobType = PrintJobType.RECEIPT;
            }

            job.contentPreview = getStringSafely(cursor, COLUMN_CONTENT_PREVIEW);
            job.printerAddress = getStringSafely(cursor, COLUMN_PRINTER_ADDRESS);
            job.printerName = getStringSafely(cursor, COLUMN_PRINTER_NAME);

            String statusStr = getStringSafely(cursor, COLUMN_STATUS);
            if (statusStr != null) {
                try {
                    job.status = PrintJobStatus.valueOf(statusStr);
                } catch (IllegalArgumentException e) {
                    job.status = PrintJobStatus.QUEUED; // Default fallback
                }
            } else {
                job.status = PrintJobStatus.QUEUED;
            }

            long createdAt = getLongSafely(cursor, COLUMN_CREATED_AT);
            job.createdAt = createdAt > 0 ? new Date(createdAt) : new Date();

            long startedAt = getLongSafely(cursor, COLUMN_STARTED_AT);
            job.startedAt = startedAt > 0 ? new Date(startedAt) : null;

            long completedAt = getLongSafely(cursor, COLUMN_COMPLETED_AT);
            job.completedAt = completedAt > 0 ? new Date(completedAt) : null;

            job.errorMessage = getStringSafely(cursor, COLUMN_ERROR_MESSAGE);
            job.retryCount = getIntSafely(cursor, COLUMN_RETRY_COUNT);
            job.userId = getStringSafely(cursor, COLUMN_USER_ID);
            job.transactionId = getStringSafely(cursor, COLUMN_TRANSACTION_ID);

        } catch (Exception e) {
            // Log error but don't crash - return a basic job object
            android.util.Log.e("PrintHistoryManager", "Error parsing PrintJob from cursor", e);
            if (job.jobId == null) job.jobId = "unknown_" + System.currentTimeMillis();
            if (job.jobType == null) job.jobType = PrintJobType.RECEIPT;
            if (job.status == null) job.status = PrintJobStatus.FAILED;
            if (job.createdAt == null) job.createdAt = new Date();
        }

        return job;
    }

    // FIXED: Safe cursor to PrinterEvent conversion
    private PrinterEvent cursorToPrinterEvent(Cursor cursor) {
        PrinterEvent event = new PrinterEvent();

        try {
            event.id = getLongSafely(cursor, COLUMN_EVENT_ID);

            String eventTypeStr = getStringSafely(cursor, COLUMN_EVENT_TYPE);
            if (eventTypeStr != null) {
                try {
                    event.eventType = PrinterEventType.valueOf(eventTypeStr);
                } catch (IllegalArgumentException e) {
                    event.eventType = PrinterEventType.STATUS_CHECK; // Default fallback
                }
            } else {
                event.eventType = PrinterEventType.STATUS_CHECK;
            }

            event.printerAddress = getStringSafely(cursor, COLUMN_PRINTER_ADDRESS);
            event.printerName = getStringSafely(cursor, COLUMN_PRINTER_NAME);

            long timestamp = getLongSafely(cursor, COLUMN_EVENT_TIMESTAMP);
            event.timestamp = timestamp > 0 ? new Date(timestamp) : new Date();

            event.details = getStringSafely(cursor, COLUMN_EVENT_DETAILS);

        } catch (Exception e) {
            // Log error but don't crash - return a basic event object
            android.util.Log.e("PrintHistoryManager", "Error parsing PrinterEvent from cursor", e);
            if (event.eventType == null) event.eventType = PrinterEventType.STATUS_CHECK;
            if (event.timestamp == null) event.timestamp = new Date();
        }

        return event;
    }
// Add these methods to PrintHistoryManager class

    public List<PrintJob> getPendingPrintJobs() {
        return getPrintJobsByStatus(PrintJobStatus.QUEUED);
    }

    public List<PrintJob> getFailedPrintJobs() {
        return getPrintJobsByStatus(PrintJobStatus.FAILED);
    }

    public List<PrintJob> getAllReprintablePrintJobs() {
        SQLiteDatabase db = this.getReadableDatabase();
        List<PrintJob> jobs = new ArrayList<>();

        String selection = COLUMN_STATUS + " IN (?, ?, ?)";
        String[] selectionArgs = {
                PrintJobStatus.QUEUED.name(),
                PrintJobStatus.FAILED.name(),
                PrintJobStatus.COMPLETED.name()  // Allow reprinting completed jobs
        };

        Cursor cursor = db.query(TABLE_PRINT_JOBS, null, selection, selectionArgs,
                null, null, COLUMN_CREATED_AT + " DESC");

        if (cursor.moveToFirst()) {
            do {
                jobs.add(cursorToPrintJob(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return jobs;
    }

    public PrintJob getPrintJobById(String jobId) {
        SQLiteDatabase db = this.getReadableDatabase();
        PrintJob job = null;

        Cursor cursor = db.query(TABLE_PRINT_JOBS, null,
                COLUMN_JOB_ID + " = ?", new String[]{jobId},
                null, null, null);

        if (cursor.moveToFirst()) {
            job = cursorToPrintJob(cursor);
        }

        cursor.close();
        db.close();
        return job;
    }

    public void markJobForReprint(String jobId) {
        updatePrintJobStatus(jobId, PrintJobStatus.QUEUED, null);
        // Reset retry count for reprint
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_RETRY_COUNT, 0);
        db.update(TABLE_PRINT_JOBS, values, COLUMN_JOB_ID + " = ?", new String[]{jobId});
        db.close();
    }
    // Data classes
    public static class PrintJob {
        public long id;
        public String jobId;
        public PrintJobType jobType;
        public String contentPreview;
        public String printerAddress;
        public String printerName;
        public PrintJobStatus status;
        public Date createdAt;
        public Date startedAt;
        public Date completedAt;
        public String errorMessage;
        public int retryCount;
        public String userId;
        public String transactionId;
    }

    public static class PrinterEvent {
        public long id;
        public PrinterEventType eventType;
        public String printerAddress;
        public String printerName;
        public Date timestamp;
        public String details;
    }

    public static class PrintStatistics {
        public int totalJobs;
        public int successfulJobs;
        public int failedJobs;
        public double successRate;
        public List<JobTypeCount> jobsByType;
    }

    public static class JobTypeCount {
        public PrintJobType jobType;
        public int count;
    }

    // Enums
    public enum PrintJobType {
        RECEIPT,
        REPORT,
        TEST_PAGE,
        LABEL,
        BARCODE

    }

    public enum PrintJobStatus {
        QUEUED,
        PRINTING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum PrinterEventType {
        CONNECTED,
        DISCONNECTED,
        CONNECTION_FAILED,
        PAPER_JAM,
        NO_PAPER,
        LOW_BATTERY,
        OVERHEATED,
        COVER_OPEN,
        STATUS_CHECK,
        ERROR_RECOVERED
    }
}