package InvoiceBot.export;

import InvoiceBot.model.InvoiceData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Verantwortlich für den Export von Rechnungsdaten nach Excel.
 * Erstellt formatierte XLSX-Dateien mit professionellem Layout.
 * 
 * Responsible for exporting invoice data to Excel.
 * Creates formatted XLSX files with a professional layout.
 * 
 */



public class ExcelExporter {

    private static final String[] COLUMN_HEADERS = {
        "PDF Datei",
        "Rechnungsnummer",
        "Rechnungsdatum",
        "Unternehmensname",
        "Leistungszeitraum Start",
        "Leistungszeitraum Ende",
        "Nettobetrag",
        "Bruttobetrag",
        "Währung"
    };

    /**
     * Exportiert eine Liste von Verarbeitungsergebnissen nach Excel.
     */



    public void export(List<ProcessingResult> results, File targetFile) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Rechnungen");
            
            // Styles erstellen
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            
            // Header-Zeile erstellen
            createHeaderRow(sheet, headerStyle);
            
            // Datenzeilen erstellen
            fillDataRows(sheet, results, dataStyle);
            
            // Spaltenbreiten optimieren
            autoSizeColumns(sheet);
            
            // Datei schreiben
            writeToFile(workbook, targetFile);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }

    private void createHeaderRow(Sheet sheet, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        
        for (int i = 0; i < COLUMN_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(COLUMN_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void fillDataRows(Sheet sheet, List<ProcessingResult> results, CellStyle dataStyle) {
        int rowNum = 1;
        
        for (ProcessingResult result : results) {
            Row row = sheet.createRow(rowNum++);
            
            if (result.isSuccess() && result.getData() != null) {
                fillSuccessRow(row, result, dataStyle);
            } else {
                fillErrorRow(row, result, dataStyle);
            }
        }
    }

    private void fillSuccessRow(Row row, ProcessingResult result, CellStyle dataStyle) {
        InvoiceData data = result.getData();
        
        // Spalte 0: PDF Datei mit Hyperlink
        createCellWithHyperlink(row, 0, result.getFileName(), result.getFilePath(), dataStyle);
        
        // Spalte 1: Rechnungsnummer
        createCell(row, 1, data.getInvoiceNumber(), dataStyle);
        
        // Spalte 2: Rechnungsdatum
        createCell(row, 2, data.getInvoiceDate(), dataStyle);
        
        // Spalte 3: Unternehmensname
        createCell(row, 3, data.getCompanyName(), dataStyle);
        
        // Spalte 4+5: Leistungszeitraum aufgeteilt
        String[] periodDates = splitServicePeriod(data.getServicePeriod(), data.getInvoiceDate());
        createCell(row, 4, periodDates[0], dataStyle); // Start
        createCell(row, 5, periodDates[1], dataStyle); // Ende
        
        // Spalte 6+7: Beträge ohne Währungssymbol
        String currency = extractCurrency(data.getNetAmount(), data.getGrossAmount());
        createCell(row, 6, stripCurrency(data.getNetAmount()), dataStyle);  // Netto
        createCell(row, 7, stripCurrency(data.getGrossAmount()), dataStyle); // Brutto
        
        // Spalte 8: Währung
        createCell(row, 8, currency, dataStyle);
    }

    private void fillErrorRow(Row row, ProcessingResult result, CellStyle dataStyle) {
        createCellWithHyperlink(row, 0, result.getFileName(), result.getFilePath(), dataStyle);
        createCell(row, 1, "FEHLER: " + getValueOrDefault(result.getErrorMessage(), "Unbekannter Fehler"), dataStyle);
        
        for (int i = 2; i < COLUMN_HEADERS.length; i++) {
            createCell(row, i, "", dataStyle);
        }
    }

/**
* Teilt den Leistungszeitraum in Start- und Enddatum auf.
* Fallback: Verwendet Rechnungsdatum für beide Felder.
Hilfsmethode 
 * Prüft, ob ein String ein Datum ist (dd.MM.yyyy oder yyyy-MM-dd).
 * Gibt normalisiertes Format dd.MM.yyyy zurück oder null.
 */


private String normalizeDate(String value) {
    if (value == null) return null;

    String v = value.trim();

    // dd.MM.yyyy
    if (v.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
        return v;
    }

    // yyyy-MM-dd → dd.MM.yyyy
    if (v.matches("\\d{4}-\\d{2}-\\d{2}")) {
        return v.substring(8, 10) + "." +
               v.substring(5, 7) + "." +
               v.substring(0, 4);
    }

    return null;
}

    private String[] splitServicePeriod(String servicePeriod, String invoiceDate) {
    String fallback = normalizeDate(invoiceDate);
    String[] result = new String[] { fallback, fallback };

    if (servicePeriod == null || servicePeriod.trim().isEmpty()) {
        return result;
    }

    String cleaned = servicePeriod.trim();

    // 1️⃣ Zeitraum mit Trennzeichen
    if (cleaned.contains("-")) {
        String[] parts = cleaned.split("-", 2);
        String start = normalizeDate(parts[0]);
        String end   = normalizeDate(parts[1]);

        if (start != null && end != null) {
            result[0] = start;
            result[1] = end;
            return result;
        }
    }

    // 2️⃣ Einzelnes Datum
    String singleDate = normalizeDate(cleaned);
    if (singleDate != null) {
        result[0] = singleDate;
        result[1] = singleDate;
        return result;
    }

    // 3️⃣ Alles andere → Fallback
    return result;
}

    /**
     * Extrahiert die Währung aus einem Betrag.
     * Reihenfolge: 1. Netto, 2. Brutto, 3. Default EUR
     */
    private String extractCurrency(String netAmount, String grossAmount) {
        // Versuche zuerst aus Nettobetrag
        if (netAmount != null && !netAmount.isEmpty()) {
            if (netAmount.contains("$") || netAmount.toUpperCase().contains("USD")) {
                return "USD";
            }
            if (netAmount.contains("€") || netAmount.toUpperCase().contains("EUR")) {
                return "EUR";
            }
        }
        
        // Dann aus Bruttobetrag
        if (grossAmount != null && !grossAmount.isEmpty()) {
            if (grossAmount.contains("$") || grossAmount.toUpperCase().contains("USD")) {
                return "USD";
            }
            if (grossAmount.contains("€") || grossAmount.toUpperCase().contains("EUR")) {
                return "EUR";
            }
        }
        
        // Default: EUR
        return "EUR";
    }

    /**
     * Entfernt Währungssymbole und lässt nur Zahlen + Dezimalzeichen.
     */
    private String stripCurrency(String amount) {
        if (amount == null || amount.isEmpty()) {
            return "";
        }
        
        // Entferne alle Währungssymbole und Texte
        String cleaned = amount.trim()
            .replace("€", "")
            .replace("$", "")
            .replaceAll("(?i)eur", "")
            .replaceAll("(?i)usd", "")
            .trim();
        
        return cleaned;
    }

    private void createCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(getValueOrDefault(value, ""));
        cell.setCellStyle(style);
    }
    
    private void createCellWithHyperlink(Row row, int columnIndex, String fileName, String filePath, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(fileName);
        
        try {
            Workbook workbook = row.getSheet().getWorkbook();
            org.apache.poi.common.usermodel.Hyperlink link = workbook.getCreationHelper()
                .createHyperlink(org.apache.poi.common.usermodel.HyperlinkType.FILE);
            
            File file = new File(filePath);
            String fileUrl;
            
            if (file.exists()) {
                fileUrl = file.toURI().toString();
            } else {
                fileUrl = filePath;
            }
            
            link.setAddress(fileUrl);
            cell.setHyperlink((Hyperlink) link);
            
            // Hyperlink-Style
            CellStyle linkStyle = workbook.createCellStyle();
            linkStyle.cloneStyleFrom(style);
            org.apache.poi.ss.usermodel.Font linkFont = workbook.createFont();
            linkFont.setUnderline(org.apache.poi.ss.usermodel.Font.U_SINGLE);
            linkFont.setColor(IndexedColors.BLUE.getIndex());
            linkStyle.setFont(linkFont);
            linkStyle.setBorderBottom(style.getBorderBottom());
            linkStyle.setBorderTop(style.getBorderTop());
            linkStyle.setBorderLeft(style.getBorderLeft());
            linkStyle.setBorderRight(style.getBorderRight());
            
            cell.setCellStyle(linkStyle);
        } catch (Exception e) {
            cell.setCellStyle(style);
            System.err.println("⚠️ Konnte Hyperlink nicht erstellen für: " + fileName);
        }
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < COLUMN_HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
            int currentWidth = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, currentWidth + 1000);
        }
    }

    private void writeToFile(Workbook workbook, File targetFile) throws IOException {
        try (FileOutputStream fileOut = new FileOutputStream(targetFile)) {
            workbook.write(fileOut);
        }
    }

    private String getValueOrDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }

    /**
     * Repräsentiert das Ergebnis einer PDF-Verarbeitung.
     */
    public static class ProcessingResult {
        private String fileName;
        private String filePath;
        private InvoiceData data;
        private int trustScore;
        private boolean success;
        private String errorMessage;

        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public InvoiceData getData() { return data; }
        public int getTrustScore() { return trustScore; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }

        public void setFileName(String fileName) { this.fileName = fileName; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public void setData(InvoiceData data) { this.data = data; }
        public void setTrustScore(int trustScore) { this.trustScore = trustScore; }
        public void setSuccess(boolean success) { this.success = success; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}