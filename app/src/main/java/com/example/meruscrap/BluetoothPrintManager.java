package com.example.meruscrap;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BluetoothPrintManager {
    private static final String TAG = "BluetoothPrintManager";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // ESC/POS Commands
    private static final byte[] ESC_INIT = {0x1B, 0x40}; // Initialize printer
    private static final byte[] ESC_ALIGN_CENTER = {0x1B, 0x61, 0x01}; // Center alignment
    private static final byte[] ESC_ALIGN_LEFT = {0x1B, 0x61, 0x00}; // Left alignment
    private static final byte[] ESC_BOLD_ON = {0x1B, 0x45, 0x01}; // Bold on
    private static final byte[] ESC_BOLD_OFF = {0x1B, 0x45, 0x00}; // Bold off
    private static final byte[] ESC_DOUBLE_HEIGHT = {0x1B, 0x21, 0x10}; // Double height
    private static final byte[] ESC_NORMAL_SIZE = {0x1B, 0x21, 0x00}; // Normal size
    private static final byte[] ESC_CUT_PAPER = {0x1D, 0x56, 0x00}; // Cut paper
    private static final byte[] ESC_FEED_LINES = {0x1B, 0x64, 0x03}; // Feed 3 lines

    // Status query commands
    private static final byte[] DLE_EOT_STATUS = {0x10, 0x04, 0x01}; // Paper sensor status
    private static final byte[] DLE_EOT_OFFLINE = {0x10, 0x04, 0x02}; // Offline status
    private static final byte[] DLE_EOT_ERROR = {0x10, 0x04, 0x03}; // Error status
    private static final byte[] DLE_EOT_PAPER = {0x10, 0x04, 0x04}; // Paper roll sensor status

    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private ExecutorService executor;
    private Handler mainHandler;
    private Queue<PrintJob> printQueue;
    private boolean isConnected = false;
    private boolean isPrinting = false;

    private PrinterStatusListener statusListener;

    public interface PrinterStatusListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onPrintJobCompleted(String jobId);
        void onPrintJobFailed(String jobId, String error);
        void onStatusUpdate(PrinterStatus status);
    }

    public enum PrinterStatus {
        ONLINE,
        OFFLINE,
        PAPER_JAM,
        NO_PAPER,
        LOW_BATTERY,
        COVER_OPEN,
        OVERHEATED,
        UNKNOWN_ERROR
    }

    public static class PrintJob {
        public String jobId;
        public String content;
        public PrintJobType type;
        public long timestamp;
        public int retryCount;

        public PrintJob(String jobId, String content, PrintJobType type) {
            this.jobId = jobId;
            this.content = content;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
            this.retryCount = 0;
        }
    }

    public enum PrintJobType {
        RECEIPT,
        REPORT,
        TEST_PAGE
    }

    public BluetoothPrintManager(Context context) {
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.printQueue = new LinkedList<>();
    }

    public void setStatusListener(PrinterStatusListener listener) {
        this.statusListener = listener;
    }

    public void connect(BluetoothDevice device) {
        if (isConnected) {
            disconnect();
        }

        executor.execute(() -> {
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();

                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();

                isConnected = true;

                // Initialize printer
                sendRawData(ESC_INIT);
                Thread.sleep(500);

                // Check printer status
                checkPrinterStatus();

                mainHandler.post(() -> {
                    if (statusListener != null) {
                        statusListener.onConnected();
                    }
                });

                // Start status monitoring
                startStatusMonitoring();

                // Process queued jobs
                processPrintQueue();

            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Connection failed", e);
                isConnected = false;
                mainHandler.post(() -> {
                    if (statusListener != null) {
                        statusListener.onError("Connection failed: " + e.getMessage());
                    }
                });
            }
        });
    }

    public void disconnect() {
        isConnected = false;
        isPrinting = false;

        executor.execute(() -> {
            try {
                if (socket != null && socket.isConnected()) {
                    socket.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error during disconnect", e);
            } finally {
                socket = null;
                outputStream = null;
                inputStream = null;

                mainHandler.post(() -> {
                    if (statusListener != null) {
                        statusListener.onDisconnected();
                    }
                });
            }
        });
    }

    public void addPrintJob(String jobId, String content, PrintJobType type) {
        PrintJob job = new PrintJob(jobId, content, type);
        printQueue.offer(job);

        if (isConnected && !isPrinting) {
            processPrintQueue();
        }
    }

    private void processPrintQueue() {
        if (!isConnected || isPrinting || printQueue.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            isPrinting = true;

            while (!printQueue.isEmpty() && isConnected) {
                PrintJob job = printQueue.poll();

                try {
                    printJob(job);

                    mainHandler.post(() -> {
                        if (statusListener != null) {
                            statusListener.onPrintJobCompleted(job.jobId);
                        }
                    });

                    // Brief delay between jobs
                    Thread.sleep(1000);

                } catch (Exception e) {
                    Log.e(TAG, "Print job failed: " + job.jobId, e);

                    // Retry logic
                    if (job.retryCount < 3) {
                        job.retryCount++;
                        printQueue.offer(job); // Re-queue for retry
                        try {
                            Thread.sleep(2000); // Wait before retry
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        mainHandler.post(() -> {
                            if (statusListener != null) {
                                statusListener.onPrintJobFailed(job.jobId, e.getMessage());
                            }
                        });
                    }
                }
            }

            isPrinting = false;
        });
    }

    private void printJob(PrintJob job) throws IOException, InterruptedException {
        if (!isConnected || outputStream == null) {
            throw new IOException("Printer not connected");
        }

        switch (job.type) {
            case RECEIPT:
                printReceipt(job.content);
                break;
            case REPORT:
                printReport(job.content);
                break;
            case TEST_PAGE:
                printTestPage();
                break;
        }
    }

    private void printReceipt(String content) throws IOException {
        // Initialize and center align
        sendRawData(ESC_INIT);
        sendRawData(ESC_ALIGN_CENTER);

        // Print header
        sendRawData(ESC_BOLD_ON);
        sendRawData(ESC_DOUBLE_HEIGHT);
        sendText("MERU SCRAP METAL");
        sendNewLine();
        sendRawData(ESC_NORMAL_SIZE);
        sendRawData(ESC_BOLD_OFF);

        sendText("Receipt");
        sendNewLine();
        sendNewLine();

        // Print content (left aligned)
        sendRawData(ESC_ALIGN_LEFT);
        sendText(content);
        sendNewLine();
        sendNewLine();

        // Print footer
        sendRawData(ESC_ALIGN_CENTER);
        sendText("Thank you!");
        sendNewLine();
        sendText("Date: " + new java.util.Date().toString());

        // Feed and cut
        sendRawData(ESC_FEED_LINES);
        sendRawData(ESC_CUT_PAPER);
    }

    private void printReport(String content) throws IOException {
        sendRawData(ESC_INIT);
        sendRawData(ESC_ALIGN_LEFT);

        sendRawData(ESC_BOLD_ON);
        sendText("REPORT - MERU SCRAP METAL");
        sendRawData(ESC_BOLD_OFF);
        sendNewLine();
        sendNewLine();

        sendText(content);
        sendNewLine();
        sendNewLine();

        sendText("Generated: " + new java.util.Date().toString());
        sendRawData(ESC_FEED_LINES);
        sendRawData(ESC_CUT_PAPER);
    }

    private void printTestPage() throws IOException {
        sendRawData(ESC_INIT);
        sendRawData(ESC_ALIGN_CENTER);

        sendRawData(ESC_BOLD_ON);
        sendText("PRINTER TEST PAGE");
        sendRawData(ESC_BOLD_OFF);
        sendNewLine();
        sendNewLine();

        sendText("If you can read this,");
        sendNewLine();
        sendText("your printer is working!");
        sendNewLine();
        sendNewLine();

        sendText("Test performed at:");
        sendNewLine();
        sendText(new java.util.Date().toString());

        sendRawData(ESC_FEED_LINES);
        sendRawData(ESC_CUT_PAPER);
    }

    private void sendText(String text) throws IOException {
        if (outputStream != null) {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
    }

    private void sendNewLine() throws IOException {
        sendText("\n");
    }

    private void sendRawData(byte[] data) throws IOException {
        if (outputStream != null) {
            outputStream.write(data);
            outputStream.flush();
        }
    }

    private void checkPrinterStatus() {
        if (!isConnected || outputStream == null) {
            return;
        }

        executor.execute(() -> {
            try {
                // Query paper status
                sendRawData(DLE_EOT_PAPER);
                Thread.sleep(100);

                // Query offline status
                sendRawData(DLE_EOT_OFFLINE);
                Thread.sleep(100);

                // Query error status
                sendRawData(DLE_EOT_ERROR);
                Thread.sleep(100);

                // Read responses if available
                readStatusResponses();

            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Status check failed", e);
            }
        });
    }

    private void readStatusResponses() {
        if (inputStream == null) return;

        try {
            byte[] buffer = new byte[16];
            int available = inputStream.available();

            if (available > 0) {
                int bytesRead = inputStream.read(buffer, 0, Math.min(available, buffer.length));

                if (bytesRead > 0) {
                    PrinterStatus status = interpretStatusResponse(buffer, bytesRead);

                    mainHandler.post(() -> {
                        if (statusListener != null) {
                            statusListener.onStatusUpdate(status);
                        }
                    });
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading status response", e);
        }
    }

    private PrinterStatus interpretStatusResponse(byte[] response, int length) {
        if (length == 0) return PrinterStatus.UNKNOWN_ERROR;

        byte status = response[0];

        // Interpret common status bits (varies by printer manufacturer)
        if ((status & 0x04) != 0) {
            return PrinterStatus.NO_PAPER;
        }
        if ((status & 0x08) != 0) {
            return PrinterStatus.COVER_OPEN;
        }
        if ((status & 0x20) != 0) {
            return PrinterStatus.PAPER_JAM;
        }
        if ((status & 0x40) != 0) {
            return PrinterStatus.OFFLINE;
        }
        if ((status & 0x80) != 0) {
            return PrinterStatus.OVERHEATED;
        }

        return PrinterStatus.ONLINE;
    }

    private void startStatusMonitoring() {
        executor.execute(() -> {
            while (isConnected) {
                try {
                    Thread.sleep(10000); // Check every 10 seconds
                    if (isConnected) {
                        checkPrinterStatus();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isPrinting() {
        return isPrinting;
    }

    public int getQueueSize() {
        return printQueue.size();
    }

    public void clearQueue() {
        printQueue.clear();
    }

    public void shutdown() {
        disconnect();
        if (executor != null) {
            executor.shutdown();
        }
    }
}