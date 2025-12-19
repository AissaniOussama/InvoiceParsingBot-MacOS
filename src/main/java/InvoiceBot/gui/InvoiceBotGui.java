package InvoiceBot.gui;

import InvoiceBot.llm.LlmClient;
import InvoiceBot.model.InvoiceData;
import InvoiceBot.parser.InvoiceParser;
import InvoiceBot.parser.PdfTextExtractor;
import InvoiceBot.export.ExcelExporter;
import InvoiceBot.export.ExcelExporter.ProcessingResult;
import InvoiceBot.validation.TrustScoreCalculator;

// Swing/AWT Imports
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

// Java IO/NIO Imports
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Java Util Imports
import java.util.ArrayList;
import java.util.List;

/* 
 * InvoiceBotGui.java
 * 
 * Haupt-GUI-Klasse für die InvoiceBot-Anwendung.
 * Bietet eine Swing-basierte Benutzeroberfläche zur Auswahl von PDF-Rechnungen,
 * Starten der Verarbeitung, Anzeigen von Logs und Exportieren der Ergebnisse.
 *
 * 
 * Main GUI class for the InvoiceBot application.
 * Provides a Swing-based user interface for selecting PDF invoices,
 * starting processing, displaying logs, and exporting results.
*/

public class InvoiceBotGui extends JFrame {

    private final InvoiceParser parser;
    private final LlmClient llmClient;
    private final TrustScoreCalculator trustScoreCalculator;
    
    private JTextArea logArea;
    private JButton selectButton;
    private JButton startButton;
    private JButton exportButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    
    private File[] selectedFiles;
    private List<ProcessingResult> results = new ArrayList<>();
    
    // Konfigurierbare Trust-Score Schwelle - nur 85%+ Rechnungen werden exportiert
    private static final int MIN_TRUST_SCORE = 85;  // Nur vollständige Rechnungen

    public InvoiceBotGui(InvoiceParser parser, LlmClient llmClient) {
        this.parser = parser;
        this.llmClient = llmClient;
        this.trustScoreCalculator = new TrustScoreCalculator();
        
        initializeUI();
        checkServerConnection();
    }

    private void initializeUI() {
        setTitle("InvoiceBot - PDF Invoice Parser");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);
        
        // Main Panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Top Panel - Controls
        JPanel controlPanel = createControlPanel();
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        
        // Center Panel - Log Area
        JPanel logPanel = createLogPanel();
        mainPanel.add(logPanel, BorderLayout.CENTER);
        
        // Bottom Panel - Status
        JPanel statusPanel = createStatusPanel();
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        // File Selection
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectButton = new JButton("📁 PDF-Dateien auswählen");
        selectButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        selectButton.addActionListener(e -> selectFiles());
        filePanel.add(selectButton);
        
        // Start Button
        startButton = new JButton("🚀 Verarbeitung starten");
        startButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        startButton.setEnabled(false);
        startButton.addActionListener(e -> startProcessing());
        filePanel.add(startButton);
        
        // Export Button
        exportButton = new JButton("📊 Als Excel exportieren");
        exportButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        exportButton.setEnabled(false);
        exportButton.addActionListener(e -> exportResults());
        filePanel.add(exportButton);
        
        panel.add(filePanel, BorderLayout.NORTH);
        
        // Progress Bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(800, 25));
        panel.add(progressBar, BorderLayout.SOUTH);
        
        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Verarbeitungs-Log"));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(240, 240, 240));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(850, 500));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Bereit");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(statusLabel);
        return panel;
    }

    private void checkServerConnection() {
        log("🔍 Prüfe LLM-Server Verbindung...");
        
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return llmClient.isServerReachable();
            }
            
            @Override
            protected void done() {
                try {
                    boolean reachable = get();
                    if (reachable) {
                        log("✅ LLM-Server ist erreichbar und bereit!");
                        statusLabel.setText("✅ Server verbunden");
                        statusLabel.setForeground(new Color(0, 128, 0));
                    } else {
                        log("❌ LLM-Server NICHT erreichbar auf http://127.0.0.1:1234");
                        log("👉 Bitte starten Sie LM Studio und laden Sie ein Modell");
                        statusLabel.setText("❌ Server nicht erreichbar");
                        statusLabel.setForeground(Color.RED);
                        
                        JOptionPane.showMessageDialog(
                            InvoiceBotGui.this,
                            "LLM-Server ist nicht erreichbar.\nBitte starten Sie LM Studio auf Port 1234.",
                            "Verbindungsfehler",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                } catch (Exception e) {
                    log("❌ Fehler beim Verbindungstest: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void selectFiles() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF-Dateien", "pdf"));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFiles = fileChooser.getSelectedFiles();
            log("📁 " + selectedFiles.length + " Datei(en) ausgewählt:");
            for (File file : selectedFiles) {
                log("   - " + file.getName());
            }
            startButton.setEnabled(selectedFiles.length > 0);
        }
    }

    private void startProcessing() {
        if (selectedFiles == null || selectedFiles.length == 0) {
            JOptionPane.showMessageDialog(this, "Bitte wählen Sie zuerst PDF-Dateien aus.", 
                "Keine Dateien", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        selectButton.setEnabled(false);
        startButton.setEnabled(false);
        results.clear();
        
        log("\n========================================");
        log("🚀 STARTE VERARBEITUNG VON " + selectedFiles.length + " PDF(S)");
        log("========================================\n");
        
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                int total = selectedFiles.length;
                
                for (int i = 0; i < total; i++) {
                    File pdfFile = selectedFiles[i];
                    int progress = (int) ((i / (double) total) * 100);
                    setProgress(progress);
                    
                    publish("\n--- VERARBEITE (" + (i + 1) + "/" + total + "): " + pdfFile.getName() + " ---");
                    
                    ProcessingResult result = processPdf(pdfFile);
                    results.add(result);
                    
                    if (result.isSuccess()) {
                        int score = result.getTrustScore();
                        String scoreDesc = TrustScoreCalculator.getScoreDescription(score);
                        
                        publish("✅ ERFOLGREICH verarbeitet");
                        publish("   Trust-Score: " + score + "% - " + scoreDesc);
                        publish("   Firma: " + result.getData().getCompanyName());
                        publish("   Rechnungsnummer: " + result.getData().getInvoiceNumber());
                        publish("   Netto: " + result.getData().getNetAmount());
                        publish("   Brutto: " + result.getData().getGrossAmount());
                        
                        if (score < MIN_TRUST_SCORE) {
                            publish("   ⚠️ WARNUNG: Trust-Score unter Schwelle (" + MIN_TRUST_SCORE + "%)");
                        }
                    } else {
                        publish("❌ FEHLER: " + result.getErrorMessage());
                    }
                }
                
                setProgress(100);
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    log(message);
                }
            }
            
            @Override
            protected void done() {
                finishProcessing();
            }
        };
        
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);
                statusLabel.setText("Verarbeite... " + progress + "%");
            }
        });
        
        worker.execute();
    }

    private ProcessingResult processPdf(File pdfFile) {
        ProcessingResult result = new ProcessingResult();
        result.setFileName(pdfFile.getName());
        result.setFilePath(pdfFile.getAbsolutePath());
        
        try {
            // 1. PDF-Text extrahieren
            String pdfText = PdfTextExtractor.extract(pdfFile);
            
            // 2. LLM-Pipeline ausführen
            InvoiceData data = parser.parse(pdfText);
            
            // 3. Trust-Score berechnen
            int trustScore = calculateTrustScore(data);
            
            result.setData(data);
            result.setTrustScore(trustScore);
            result.setSuccess(true);
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }

    private int calculateTrustScore(InvoiceData data) {
        return trustScoreCalculator.calculate(data);
    }

    private void finishProcessing() {
        log("\n========================================");
        log("✅ VERARBEITUNG ABGESCHLOSSEN");
        log("========================================\n");
        
        // Statistiken
        long totalProcessed = results.size();
        long successCount = results.stream()
            .filter(r -> r.isSuccess() && r.getTrustScore() >= MIN_TRUST_SCORE)
            .count();
        long lowScoreCount = results.stream()
            .filter(r -> r.isSuccess() && r.getTrustScore() < MIN_TRUST_SCORE)
            .count();
        long failedCount = results.stream()
            .filter(r -> !r.isSuccess())
            .count();
        
        log("📊 STATISTIK:");
        log("   Gesamt verarbeitet: " + totalProcessed);
        log("   ✅ Exportierbar (Trust ≥" + MIN_TRUST_SCORE + "%): " + successCount);
        if (lowScoreCount > 0) {
            log("   ⚠️ Unvollständig (Trust <" + MIN_TRUST_SCORE + "%): " + lowScoreCount);
        }
        if (failedCount > 0) {
            log("   ❌ Fehlgeschlagen: " + failedCount);
        }
        
        long problematicCount = lowScoreCount + failedCount;
        
        statusLabel.setText("✅ Verarbeitung abgeschlossen");
        statusLabel.setForeground(new Color(0, 128, 0));
        selectButton.setEnabled(true);
        startButton.setEnabled(false);
        exportButton.setEnabled(results.size() > 0);
        progressBar.setValue(0);
        
        // Erfolgsmeldung
        JOptionPane.showMessageDialog(
            this,
            "Verarbeitung abgeschlossen!\n\n" +
            "Exportierbar: " + successCount + " (≥" + MIN_TRUST_SCORE + "%)\n" +
            "Problematisch: " + problematicCount + "\n\n" +
            "Die Excel-Datei enthält nur vollständige Rechnungen.",
            "Fertig",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void exportResults() {
        if (results.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Keine Ergebnisse zum Exportieren vorhanden.\nBitte verarbeiten Sie zuerst PDF-Dateien.",
                "Keine Daten",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        // Ordner-Auswahl-Dialog
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setDialogTitle("Export-Ordner auswählen");
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        
        int userSelection = folderChooser.showDialog(this, "Ordner auswählen");
        
        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File selectedBaseDir = folderChooser.getSelectedFile();
        
        try {
            // Zeitstempel für eindeutigen Ordnernamen
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            String exportFolderName = "invoice_export_" + timestamp;
            
            // Hauptexport-Ordner erstellen
            File exportDir = new File(selectedBaseDir, exportFolderName);
            if (!exportDir.mkdirs()) {
                throw new IOException("Konnte Export-Ordner nicht erstellen: " + exportDir.getAbsolutePath());
            }
            
            log("\n📁 Erstelle Export-Struktur in: " + exportDir.getAbsolutePath());
            
            // 1. Erfolgreiche PDFs Ordner erstellen
            List<ProcessingResult> successfulResults = results.stream()
                .filter(r -> r.isSuccess() && r.getTrustScore() >= MIN_TRUST_SCORE)
                .toList();
            
            File successfulPdfsDir = new File(exportDir, "successful_pdfs");
            if (!successfulPdfsDir.mkdirs()) {
                throw new IOException("Konnte successful_pdfs-Ordner nicht erstellen");
            }
            
            log("\n📋 Kopiere und benenne erfolgreiche PDFs um...");
            
            // PDFs kopieren und umbenennen
            for (ProcessingResult result : successfulResults) {
                File sourceFile = new File(result.getFilePath());
                
                // Neuen Dateinamen erstellen
                String newFileName = createRenamedFilename(result);
                
                if (newFileName == null || newFileName.isEmpty()) {
                    // Fallback: Original-Namen beibehalten
                    newFileName = result.getFileName();
                    log("   ⚠️ Konnte " + result.getFileName() + " nicht umbenennen - behalte Original-Namen");
                } else {
                    log("   ✅ " + result.getFileName() + " → " + newFileName);
                }
                
                File targetFile = new File(successfulPdfsDir, newFileName);
                
                // PDF kopieren
                Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                
                // Pfad im Result aktualisieren für Excel-Hyperlinks
                result.setFilePath(targetFile.getAbsolutePath());
                result.setFileName(newFileName);
            }
            
            // 2. Excel-Datei exportieren
            File excelFile = new File(exportDir, "rechnungen_" + timestamp + ".xlsx");
            ExcelExporter exporter = new ExcelExporter();
            exporter.export(successfulResults, excelFile);
            log("\n✅ Excel-Datei erstellt: " + excelFile.getName());
            log("   Exportierte Rechnungen: " + successfulResults.size());
            
            // 3. Failed PDFs Ordner erstellen
            List<ProcessingResult> failedResults = results.stream()
                .filter(r -> !r.isSuccess() || r.getTrustScore() < MIN_TRUST_SCORE)
                .toList();
            
            if (!failedResults.isEmpty()) {
                File failedPdfsDir = new File(exportDir, "failed_pdfs");
                if (!failedPdfsDir.mkdirs()) {
                    throw new IOException("Konnte failed_pdfs-Ordner nicht erstellen");
                }
                
                log("\n📋 Kopiere fehlerhafte PDFs...");
                
                // PDFs kopieren (OHNE Umbenennung)
                for (ProcessingResult result : failedResults) {
                    File sourceFile = new File(result.getFilePath());
                    File targetFile = new File(failedPdfsDir, result.getFileName());
                    Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                
                // Fehlerliste erstellen
                File failedListFile = new File(failedPdfsDir, "failed_list.txt");
                createFailedListFile(failedResults, failedListFile);
                
                log("✅ " + failedResults.size() + " fehlerhafte PDFs nach: " + failedPdfsDir.getName());
                log("✅ Fehlerliste erstellt: failed_list.txt");
            }
            
            // 4. Zusammenfassung erstellen
            File summaryFile = new File(exportDir, "ZUSAMMENFASSUNG.txt");
            createSummaryFile(successfulResults.size(), failedResults.size(), summaryFile);
            log("✅ Zusammenfassung erstellt: ZUSAMMENFASSUNG.txt");
            
            log("\n========================================");
            log("✅ EXPORT ABGESCHLOSSEN");
            log("========================================");
            log("📁 Export-Ordner: " + exportDir.getName());
            log("📊 Excel-Datei: " + excelFile.getName());
            log("📁 Erfolgreiche PDFs: " + successfulResults.size() + " (siehe successful_pdfs/)");
            if (!failedResults.isEmpty()) {
                log("⚠️  Fehlerhafte PDFs: " + failedResults.size() + " (siehe failed_pdfs/)");
            }
            
            // Erfolgsbestätigung mit Option Ordner zu öffnen
            int openFolder = JOptionPane.showConfirmDialog(
                this,
                "Export erfolgreich abgeschlossen!\n\n" +
                "Speicherort: " + exportDir.getAbsolutePath() + "\n\n" +
                "Inhalt:\n" +
                "  • Excel-Datei mit " + successfulResults.size() + " Rechnungen\n" +
                "  • " + successfulResults.size() + " umbenannte PDFs in successful_pdfs/\n" +
                (failedResults.isEmpty() ? "" : "  • " + failedResults.size() + " fehlerhafte PDFs in failed_pdfs/\n") +
                "  • Zusammenfassung\n\n" +
                "Möchten Sie den Ordner öffnen?",
                "Export abgeschlossen",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE
            );
            
            if (openFolder == JOptionPane.YES_OPTION) {
                openFileExplorer(exportDir);
            }
            
        } catch (IOException e) {
            log("❌ Fehler beim Exportieren: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                this,
                "Fehler beim Exportieren:\n" + e.getMessage(),
                "Export-Fehler",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    /**
     * Erstellt den neuen Dateinamen basierend auf den Rechnungsdaten.
     * Format: YYYYMMDD_Unternehmensname_Rechnungsnummer.pdf
     */
    private String createRenamedFilename(ProcessingResult result) {
        InvoiceData data = result.getData();
        
        if (data == null) {
            return null;
        }
        
        // 1. Daten vorbereiten
        String datePart = formatDateForFilename(data.getInvoiceDate());
        String companyPart = sanitizeFilename(data.getCompanyName());
        String numberPart = sanitizeFilename(data.getInvoiceNumber());
        
        // Fallback wenn essenzielle Daten fehlen
        if (datePart.isEmpty() || companyPart.isEmpty() || numberPart.isEmpty()) {
            return null; // Rückgabe null signalisiert: Original-Name verwenden
        }
        
        // 2. Neuen Dateinamen konstruieren
        return String.format("%s_%s_%s.pdf", datePart, companyPart, numberPart);
    }
    
    /**
     * Sanitizes a string for use as a filename part.
     */
    private String sanitizeFilename(String input) {
        if (input == null || input.trim().isEmpty() || input.equalsIgnoreCase("null") || input.equalsIgnoreCase("nicht vorhanden")) {
            return "";
        }
        // Ersetze alle nicht-alphanumerischen Zeichen (außer Leerzeichen, Unterstrich, Bindestrich) durch nichts
        String sanitized = input.replaceAll("[^a-zA-Z0-9\\s_\\-]", "");
        // Ersetze Leerzeichen durch Unterstrich
        sanitized = sanitized.trim().replaceAll("\\s+", "_");
        // Kürze auf max. 50 Zeichen, um zu lange Dateinamen zu vermeiden
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        return sanitized;
    }
    
    /**
     * Formats a date string (dd.MM.yyyy) to YYYYMMDD.
     */
    private String formatDateForFilename(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || dateStr.equalsIgnoreCase("null") || dateStr.equalsIgnoreCase("nicht vorhanden")) {
            return "";
        }
        try {
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            return date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (DateTimeParseException e) {
            log("⚠️ Fehler beim Formatieren des Datums " + dateStr + " für Dateinamen.");
            return "";
        }
    }
    
    /**
     * Erstellt die Fehlerliste-Datei.
     */
    private void createFailedListFile(List<ProcessingResult> failedResults, File file) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("=== FEHLERHAFTE DATEIEN ===\n");
        content.append("Erstellt am: ").append(new java.util.Date()).append("\n");
        content.append("Trust-Score Schwelle: ").append(MIN_TRUST_SCORE).append("%\n\n");
        content.append("Anzahl fehlerhafter Dateien: ").append(failedResults.size()).append("\n\n");
        content.append("========================================\n\n");
        
        for (int i = 0; i < failedResults.size(); i++) {
            ProcessingResult result = failedResults.get(i);
            
            content.append((i + 1)).append(". ").append(result.getFileName()).append("\n");
            content.append("   ").append("─".repeat(50)).append("\n");
            
            if (!result.isSuccess()) {
                content.append("   Status: ❌ Verarbeitungsfehler\n");
                content.append("   Fehler: ").append(result.getErrorMessage()).append("\n");
            } else {
                int score = result.getTrustScore();
                content.append("   Status: ⚠️ Trust-Score zu niedrig\n");
                content.append("   Trust-Score: ").append(score).append("%");
                content.append(" (Schwelle: ").append(MIN_TRUST_SCORE).append("%)\n");
                content.append("   Bewertung: ").append(TrustScoreCalculator.getScoreDescription(score)).append("\n");
                
                // Details zu extrahierten Daten
                if (result.getData() != null) {
                    InvoiceData data = result.getData();
                    content.append("\n   Extrahierte Daten:\n");
                    content.append("     • Firma: ").append(data.getCompanyName()).append("\n");
                    content.append("     • Re-Nr.: ").append(data.getInvoiceNumber()).append("\n");
                    content.append("     • Datum: ").append(data.getInvoiceDate()).append("\n");
                    content.append("     • Netto: ").append(data.getNetAmount()).append("\n");
                    content.append("     • Brutto: ").append(data.getGrossAmount()).append("\n");
                }
            }
            
            content.append("\n");
        } // Schließt die for-Schleife
        
        int failedCount = failedResults.size();
        if (failedCount > 0) {
            content.append("EMPFEHLUNG:\n");
            content.append("─".repeat(60)).append("\n");
            content.append("Prüfen Sie die PDFs im Ordner 'failed_pdfs' manuell.\n");
            content.append("Die Datei 'failed_list.txt' enthält detaillierte Informationen\n");
            content.append("zu jedem fehlgeschlagenen PDF und den Trust-Score-Bewertungen.\n");
        }
        
        content.append("\n").append("=".repeat(60)).append("\n");
        
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content.toString());
        }
    }
    
    /**
     * Öffnet den Datei-Explorer im angegebenen Ordner.
     */
    private void openFileExplorer(File directory) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory);
            }
        } catch (IOException e) {
            log("⚠️ Konnte Ordner nicht automatisch öffnen: " + e.getMessage());
        }
    }
    
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    /**
     * Erstellt die Zusammenfassungs-Datei.
     */
    private void createSummaryFile(int successCount, int failedCount, File file) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("=".repeat(60)).append("\n");
        content.append("RECHNUNGSVERARBEITUNG - ZUSAMMENFASSUNG\n");
        content.append("=".repeat(60)).append("\n\n");
        
        content.append("Datum: ").append(new java.util.Date()).append("\n\n");
        
        content.append("STATISTIK:\n");
        content.append("─".repeat(60)).append("\n");
        content.append("Gesamt verarbeitet:     ").append(successCount + failedCount).append(" PDFs\n");
        content.append("✅ Erfolgreich:          ").append(successCount).append(" PDFs\n");
        content.append("❌ Fehlgeschlagen:       ").append(failedCount).append(" PDFs\n");
        content.append("Erfolgsrate:            ")
            .append(String.format("%.1f%%", (successCount / (double)(successCount + failedCount)) * 100))
            .append("\n\n");
        
        content.append("TRUST-SCORE KONFIGURATION:\n");
        content.append("─".repeat(60)).append("\n");
        content.append("Mindestschwelle:        ").append(MIN_TRUST_SCORE).append("%\n");
        content.append("Bewertung über 85%:     Sehr hoch - Daten sehr vertrauenswürdig\n");
        content.append("Bewertung 70-84%:       Hoch - Daten vertrauenswürdig\n");
        content.append("Bewertung 50-69%:       Mittel - Manuelle Prüfung empfohlen\n");
        content.append("Bewertung 30-49%:       Niedrig - Daten unvollständig\n");
        content.append("Bewertung unter 30%:    Sehr niedrig - Parsing fehlgeschlagen\n\n");
        
        content.append("EXPORT-STRUKTUR:\n");
        content.append("─".repeat(60)).append("\n");
        content.append("📊 rechnungen_*.xlsx    - Excel mit erfolgreichen Rechnungen\n");
        content.append("📁 successful_pdfs/     - Umbenannte erfolgreiche PDFs\n");
        if (failedCount > 0) {
            content.append("📁 failed_pdfs/         - PDFs unter Mindestschwelle\n");
            content.append("   └─ failed_list.txt   - Detaillierte Fehleranalyse\n");
        }
        content.append("\n");
    }
}