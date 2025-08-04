package com.example.meruscrap;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ESCPOSCommands {

    // Basic ESC/POS Commands
    public static final byte[] ESC = {0x1B};
    public static final byte[] GS = {0x1D};
    public static final byte[] DLE = {0x10};
    public static final byte[] FS = {0x1C};

    // Initialization
    public static final byte[] INIT = {0x1B, 0x40};

    // Text formatting
    public static final byte[] BOLD_ON = {0x1B, 0x45, 0x01};
    public static final byte[] BOLD_OFF = {0x1B, 0x45, 0x00};
    public static final byte[] UNDERLINE_ON = {0x1B, 0x2D, 0x01};
    public static final byte[] UNDERLINE_OFF = {0x1B, 0x2D, 0x00};
    public static final byte[] DOUBLE_HEIGHT_ON = {0x1B, 0x21, 0x10};
    public static final byte[] DOUBLE_WIDTH_ON = {0x1B, 0x21, 0x20};
    public static final byte[] DOUBLE_SIZE_ON = {0x1B, 0x21, 0x30};
    public static final byte[] NORMAL_SIZE = {0x1B, 0x21, 0x00};

    // Alignment
    public static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};
    public static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01};
    public static final byte[] ALIGN_RIGHT = {0x1B, 0x61, 0x02};

    // Line spacing
    public static final byte[] LINE_SPACING_DEFAULT = {0x1B, 0x32};
    public static final byte[] LINE_SPACING_CUSTOM = {0x1B, 0x33, 0x20}; // 32 dots

    // Paper control
    public static final byte[] PAPER_CUT_FULL = {0x1D, 0x56, 0x00};
    public static final byte[] PAPER_CUT_PARTIAL = {0x1D, 0x56, 0x01};
    public static final byte[] PAPER_FEED_3_LINES = {0x1B, 0x64, 0x03};
    public static final byte[] PAPER_FEED_5_LINES = {0x1B, 0x64, 0x05};

    // Barcode commands
    public static final byte[] BARCODE_HEIGHT = {0x1D, 0x68, 0x64}; // Height 100 dots
    public static final byte[] BARCODE_WIDTH = {0x1D, 0x77, 0x02}; // Width 2
    public static final byte[] BARCODE_POSITION_BELOW = {0x1D, 0x48, 0x02};
    public static final byte[] BARCODE_CODE128 = {0x1D, 0x6B, 0x49};

    // QR Code commands
    public static final byte[] QR_MODEL = {0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00};
    public static final byte[] QR_SIZE = {0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x08}; // Size 8
    public static final byte[] QR_ERROR_CORRECTION = {0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30}; // Level L

    // Status commands
    public static final byte[] PAPER_SENSOR_STATUS = {0x10, 0x04, 0x01};
    public static final byte[] DRAWER_KICK_PULSE = {0x1B, 0x70, 0x00, 0x19, (byte) 0xFA};

    // Advanced formatting helper class
    public static class PrintFormatter {
        private OutputStream outputStream;

        public PrintFormatter(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public void initialize() throws IOException, InterruptedException {
            send(INIT);
            Thread.sleep(100);
        }

        public void printLine(String text) throws IOException {
            send(text.getBytes(StandardCharsets.UTF_8));
            send("\n".getBytes());
        }

        public void printBoldLine(String text) throws IOException {
            send(BOLD_ON);
            printLine(text);
            send(BOLD_OFF);
        }

        public void printCenteredLine(String text) throws IOException {
            send(ALIGN_CENTER);
            printLine(text);
            send(ALIGN_LEFT);
        }

        public void printCenteredBoldLine(String text) throws IOException {
            send(ALIGN_CENTER);
            send(BOLD_ON);
            printLine(text);
            send(BOLD_OFF);
            send(ALIGN_LEFT);
        }

        public void printLargeLine(String text) throws IOException {
            send(DOUBLE_SIZE_ON);
            printLine(text);
            send(NORMAL_SIZE);
        }

        public void printSeparatorLine() throws IOException {
            printLine("--------------------------------");
        }

        public void printDoubleSeparatorLine() throws IOException {
            printLine("================================");
        }

        public void printTwoColumn(String left, String right, int totalWidth) throws IOException {
            String formatted = formatTwoColumn(left, right, totalWidth);
            printLine(formatted);
        }

        public void printThreeColumn(String left, String center, String right, int totalWidth) throws IOException {
            String formatted = formatThreeColumn(left, center, right, totalWidth);
            printLine(formatted);
        }

        public void printBarcode(String data, BarcodeType type) throws IOException {
            send(BARCODE_HEIGHT);
            send(BARCODE_WIDTH);
            send(BARCODE_POSITION_BELOW);

            switch (type) {
                case CODE128:
                    send(BARCODE_CODE128);
                    send(data.length());
                    send(data.getBytes());
                    break;
                // Add more barcode types as needed
            }
            send("\n".getBytes());
        }

        public void printQRCode(String data) throws IOException {
            // Set QR code parameters
            send(QR_MODEL);
            send(QR_SIZE);
            send(QR_ERROR_CORRECTION);

            // Store QR code data
            byte[] storeCommand = {0x1D, 0x28, 0x6B, (byte) (data.length() + 3), 0x00, 0x31, 0x50, 0x30};
            send(storeCommand);
            send(data.getBytes());

            // Print QR code
            byte[] printCommand = {0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30};
            send(printCommand);
            send("\n".getBytes());
        }

        public void openDrawer() throws IOException {
            send(DRAWER_KICK_PULSE);
        }

        public void feedAndCut() throws IOException {
            send(PAPER_FEED_3_LINES);
            send(PAPER_CUT_PARTIAL);
        }

        public void feedLines(int lines) throws IOException {
            for (int i = 0; i < lines; i++) {
                send("\n".getBytes());
            }
        }

        private void send(byte[] data) throws IOException {
            outputStream.write(data);
            outputStream.flush();
        }

        private void send(int data) throws IOException {
            outputStream.write(data);
            outputStream.flush();
        }

        private String formatTwoColumn(String left, String right, int totalWidth) {
            int rightLen = right.length();
            int leftLen = Math.max(0, totalWidth - rightLen);

            if (left.length() > leftLen) {
                left = left.substring(0, leftLen - 3) + "...";
            }

            return String.format("%-" + leftLen + "s%s", left, right);
        }

        private String formatThreeColumn(String left, String center, String right, int totalWidth) {
            int sideWidth = (totalWidth - center.length()) / 2;

            if (left.length() > sideWidth) {
                left = left.substring(0, sideWidth - 3) + "...";
            }
            if (right.length() > sideWidth) {
                right = right.substring(0, sideWidth - 3) + "...";
            }

            return String.format("%-" + sideWidth + "s%s%" + sideWidth + "s", left, center, right);
        }
    }

    public enum BarcodeType {
        CODE128,
        CODE39,
        EAN13,
        EAN8,
        UPC_A,
        UPC_E
    }

    // Receipt template class
    public static class ReceiptTemplate {

        public static void printBusinessReceipt(PrintFormatter formatter, ReceiptData data) throws IOException, InterruptedException {
            formatter.initialize();

            // Header
            formatter.printCenteredBoldLine("MERU SCRAP METAL MARKET");
            formatter.printCenteredLine("Meru County, Kenya");
            formatter.printCenteredLine("Phone: +254 XXX XXX XXX");
            formatter.feedLines(1);

            formatter.printDoubleSeparatorLine();
            formatter.printCenteredBoldLine("SCRAP METAL RECEIPT");
            formatter.printDoubleSeparatorLine();
            formatter.feedLines(1);

            // Transaction details
            formatter.printTwoColumn("Receipt No:", data.receiptNumber, 32);
            formatter.printTwoColumn("Date:", data.date, 32);
            formatter.printTwoColumn("Time:", data.time, 32);
            formatter.printTwoColumn("Cashier:", data.cashier, 32);
            formatter.feedLines(1);

            // Customer details
            formatter.printBoldLine("CUSTOMER DETAILS:");
            formatter.printSeparatorLine();
            formatter.printTwoColumn("Name:", data.customerName, 32);
            formatter.printTwoColumn("Phone:", data.customerPhone, 32);
            formatter.printTwoColumn("ID No:", data.customerId, 32);
            formatter.feedLines(1);

            // Items
            formatter.printBoldLine("ITEMS PURCHASED:");
            formatter.printSeparatorLine();
            formatter.printThreeColumn("Material", "Weight(kg)", "Amount", 32);
            formatter.printSeparatorLine();

            double totalWeight = 0;
            double totalAmount = 0;

            for (ReceiptItem item : data.items) {
                formatter.printLine(item.materialName);
                formatter.printTwoColumn(
                        String.format("%.2f kg @ KSh %.2f/kg", item.weight, item.rate),
                        String.format("KSh %.2f", item.amount),
                        32
                );
                totalWeight += item.weight;
                totalAmount += item.amount;
            }

            formatter.printSeparatorLine();
            formatter.printTwoColumn("TOTAL WEIGHT:", String.format("%.2f kg", totalWeight), 32);
            formatter.printBoldLine(String.format("TOTAL AMOUNT: KSh %.2f", totalAmount));
            formatter.printSeparatorLine();
            formatter.feedLines(1);

            // Payment details
            formatter.printTwoColumn("Payment Method:", data.paymentMethod, 32);
            formatter.printTwoColumn("Amount Paid:", String.format("KSh %.2f", data.amountPaid), 32);
            formatter.printTwoColumn("Change:", String.format("KSh %.2f", data.change), 32);
            formatter.feedLines(1);

            // QR Code for digital receipt
            formatter.printCenteredLine("Scan for digital receipt:");
            formatter.printQRCode(data.qrCodeData);
            formatter.feedLines(1);

            // Footer
            formatter.printCenteredLine("Thank you for your business!");
            formatter.printCenteredLine("Keep this receipt for your records");
            formatter.feedLines(1);

            formatter.printCenteredLine("Powered by MeruScrap v1.0");
            formatter.feedLines(1);

            formatter.feedAndCut();
        }

        public static void printDailyReport(PrintFormatter formatter, DailyReportData data) throws IOException, InterruptedException {
            formatter.initialize();

            // Header
            formatter.printCenteredBoldLine("DAILY BUSINESS REPORT");
            formatter.printCenteredLine("Meru Scrap Metal Market");
            formatter.printDoubleSeparatorLine();
            formatter.feedLines(1);

            formatter.printTwoColumn("Date:", data.date, 32);
            formatter.printTwoColumn("Generated by:", data.generatedBy, 32);
            formatter.printTwoColumn("Print Time:", data.printTime, 32);
            formatter.feedLines(1);

            // Summary
            formatter.printBoldLine("SUMMARY:");
            formatter.printSeparatorLine();
            formatter.printTwoColumn("Total Transactions:", String.valueOf(data.totalTransactions), 32);
            formatter.printTwoColumn("Total Weight:", String.format("%.2f kg", data.totalWeight), 32);
            formatter.printTwoColumn("Total Revenue:", String.format("KSh %.2f", data.totalRevenue), 32);
            formatter.printTwoColumn("Avg. Transaction:", String.format("KSh %.2f", data.avgTransaction), 32);
            formatter.feedLines(1);

            // Material breakdown
            formatter.printBoldLine("MATERIAL BREAKDOWN:");
            formatter.printSeparatorLine();
            formatter.printThreeColumn("Material", "Weight", "Revenue", 32);
            formatter.printSeparatorLine();

            for (MaterialSummary material : data.materials) {
                formatter.printThreeColumn(
                        material.name,
                        String.format("%.1f kg", material.weight),
                        String.format("%.0f", material.revenue),
                        32
                );
            }

            formatter.feedLines(2);
            formatter.feedAndCut();
        }
    }

    // Data classes for receipt templates
    public static class ReceiptData {
        public String receiptNumber;
        public String date;
        public String time;
        public String cashier;
        public String customerName;
        public String customerPhone;
        public String customerId;
        public String paymentMethod;
        public double amountPaid;
        public double change;
        public String qrCodeData;
        public java.util.List<ReceiptItem> items;
    }

    public static class ReceiptItem {
        public String materialName;
        public double weight;
        public double rate;
        public double amount;
    }

    public static class DailyReportData {
        public String date;
        public String generatedBy;
        public String printTime;
        public int totalTransactions;
        public double totalWeight;
        public double totalRevenue;
        public double avgTransaction;
        public java.util.List<MaterialSummary> materials;
    }

    public static class MaterialSummary {
        public String name;
        public double weight;
        public double revenue;
    }
}