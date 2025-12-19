package InvoiceBot;

import InvoiceBot.export.ExcelExporter;
import InvoiceBot.export.ExcelExporter.ProcessingResult; // Wichtig: Import der inneren Klasse
import InvoiceBot.model.InvoiceData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelExporterTest {

    // JUnit erstellt diesen Ordner für uns und löscht ihn nach dem Test automatisch
    @TempDir
    Path tempDir;

    private final ExcelExporter exporter = new ExcelExporter();

    @Test
    void testExport_CreatesValidXlsxFile() throws IOException {
        // 1. ARRANGE: Daten vorbereiten
        File targetFile = tempDir.resolve("test_export.xlsx").toFile();
        List<ProcessingResult> results = new ArrayList<>();

        // Ergebnis 1: Erfolgreiche Rechnung (Happy Path)
        ProcessingResult successResult = new ProcessingResult();
        successResult.setSuccess(true);
        successResult.setFileName("Rechnung_Test.pdf");
        successResult.setFilePath("/tmp/Rechnung_Test.pdf");
        
        InvoiceData data = new InvoiceData();
        data.setInvoiceNumber("INV-2023-001");
        data.setInvoiceDate("01.01.2023");
        data.setCompanyName("$W4G GmbH");
        data.setNetAmount("100,00 €");   // Testet stripCurrency
        data.setGrossAmount("119,00 €"); // Testet stripCurrency
        data.setServicePeriod("01.01.2023 - 31.01.2023"); // Testet splitServicePeriod
        successResult.setData(data);
        
        results.add(successResult);

        // Ergebnis 2: Fehlerhafte Datei (Error Path)
        ProcessingResult errorResult = new ProcessingResult();
        errorResult.setSuccess(false);
        errorResult.setFileName("Kaputt.pdf");
        errorResult.setErrorMessage("PDF konnte nicht gelesen werden");
        results.add(errorResult);

        // 2. ACT: Export ausführen
        exporter.export(results, targetFile);

        // 3. ASSERT: Datei prüfen
        assertTrue(targetFile.exists(), "Excel-Datei wurde nicht erstellt");
        assertTrue(targetFile.length() > 0, "Excel-Datei ist leer");

        // Jetzt lesen wir die Datei mit Apache POI wieder ein, um den Inhalt zu prüfen
        try (FileInputStream fis = new FileInputStream(targetFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet("Rechnungen");
            assertNotNull(sheet, "Sheet 'Rechnungen' fehlt");

            // --- Prüfung Header ---
            Row headerRow = sheet.getRow(0);
            assertEquals("Rechnungsnummer", headerRow.getCell(1).getStringCellValue());
            assertEquals("Nettobetrag", headerRow.getCell(6).getStringCellValue());

            // --- Prüfung Zeile 1 (Erfolg) ---
            Row row1 = sheet.getRow(1);
            assertNotNull(row1, "Datenzeile 1 fehlt");
            
            // Rechnungsnummer (Spalte 1)
            assertEquals("INV-2023-001", row1.getCell(1).getStringCellValue());
            
            // Split Service Period (Spalte 4 & 5)
            // Die Logik sollte "01.01.2023 - 31.01.2023" aufgeteilt haben
            assertEquals("01.01.2023", row1.getCell(4).getStringCellValue()); // Start
            assertEquals("31.01.2023", row1.getCell(5).getStringCellValue()); // Ende

            // Currency Stripping (Spalte 6 & 7)
            // "100,00 €" sollte zu "100,00" werden
            assertEquals("100,00", row1.getCell(6).getStringCellValue());
            
            // Währungserkennung (Spalte 8)
            assertEquals("EUR", row1.getCell(8).getStringCellValue());

            // Hyperlink Check (Spalte 0)
            Cell linkCell = row1.getCell(0);
            assertEquals("Rechnung_Test.pdf", linkCell.getStringCellValue());
            assertNotNull(linkCell.getHyperlink(), "Hyperlink fehlt in Spalte 0");

            // --- Prüfung Zeile 2 (Fehler) ---
            Row row2 = sheet.getRow(2);
            assertNotNull(row2, "Fehlerzeile fehlt");
            
            // Dateiname (Spalte 0)
            assertEquals("Kaputt.pdf", row2.getCell(0).getStringCellValue());
            
            // Fehlermeldung (Spalte 1)
            String errorMsg = row2.getCell(1).getStringCellValue();
            assertTrue(errorMsg.contains("FEHLER:"), "Fehlermarkierung fehlt");
            assertTrue(errorMsg.contains("PDF konnte nicht gelesen werden"), "Fehlermeldung fehlt");
        }
    }

    @Test
    void testCurrencyLogic_USD_Detection() throws IOException {
        // Arrange
        File targetFile = tempDir.resolve("usd_test.xlsx").toFile();
        List<ProcessingResult> results = new ArrayList<>();
        
        ProcessingResult result = new ProcessingResult();
        result.setSuccess(true);
        result.setFileName("US_Invoice.pdf");
        
        InvoiceData data = new InvoiceData();
        data.setInvoiceNumber("US-1");
        data.setNetAmount("$500.00"); // Dollarzeichen!
        data.setGrossAmount("$500.00");
        result.setData(data);
        results.add(result);

        // Act
        exporter.export(results, targetFile);

        // Assert
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(targetFile))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row row = sheet.getRow(1);
            
            // Prüfen ob $ entfernt wurde
            assertEquals("500.00", row.getCell(6).getStringCellValue());
            // Prüfen ob USD erkannt wurde
            assertEquals("USD", row.getCell(8).getStringCellValue());
        }
    }
    
    @Test
    void testDateNormalizationLogic() throws IOException {
        // Testet indirekt die private normalizeDate Methode
        File targetFile = tempDir.resolve("date_test.xlsx").toFile();
        List<ProcessingResult> results = new ArrayList<>();
        
        ProcessingResult result = new ProcessingResult();
        result.setSuccess(true);
        result.setFileName("DateTest.pdf");
        
        InvoiceData data = new InvoiceData();
        data.setInvoiceNumber("1");
        // Format yyyy-MM-dd sollte zu dd.MM.yyyy werden
        data.setServicePeriod("2023-05-01"); 
        result.setData(data);
        results.add(result);

        // Act
        exporter.export(results, targetFile);

        // Assert
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(targetFile))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            // Spalte 4 ist Startdatum. Sollte normalisiert sein.
            assertEquals("01.05.2023", row.getCell(4).getStringCellValue());
            // Da kein Enddatum da war, sollte Fallback das gleiche sein
            assertEquals("01.05.2023", row.getCell(5).getStringCellValue());
        }
    }
}