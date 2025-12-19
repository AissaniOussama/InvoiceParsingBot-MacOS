package InvoiceBot.parser;

import InvoiceBot.llm.LlmExtractor;
import InvoiceBot.llm.LlmResponseParser;
import InvoiceBot.llm.LlmResponseParser.ValidationResult;
import InvoiceBot.model.InvoiceData;
import InvoiceBot.validation.TrustScoreCalculator;
import org.springframework.stereotype.Service;


/*

Logik zur mehrstufigen Extraktion und Validierung von Rechnungsdaten.
 * Nutzt LLMs für die Extraktion, Retry-Mechanismen und manuelle Validierung.


Logic for multi-stage extraction and validation of invoice data.
* Uses LLMs for extraction, retry mechanisms, and manual validation.

*/




@Service
public class InvoiceParser {

    private final LlmExtractor extractor;
    private final LlmResponseParser parser;
    private final TrustScoreCalculator trustScoreCalculator;

public InvoiceParser(LlmExtractor extractor, 
                         LlmResponseParser parser, 
                         TrustScoreCalculator trustScoreCalculator) { // <-- Hier rein
        this.extractor = extractor;
        this.parser = parser;
        this.trustScoreCalculator = trustScoreCalculator; // <-- Zuweisen
    }

    public InvoiceData parse(String text) {
        try {
            // ========================================
            // STUFE 1: Standard-Extraktion
            // ========================================
            System.out.println("\n🔍 STUFE 1: Standard-Extraktion...");
            String json = extractor.extract(text);
            InvoiceData data = new InvoiceData();
            parser.merge(data, json);
            
            int trustScore = trustScoreCalculator.calculate(data);
            System.out.println("   Trust-Score: " + trustScore + "%");
            
            if (trustScore >= 85) {
                System.out.println("   ✅ Erfolgreich auf Stufe 1!\n");
                return data;
            }
            
            // ========================================
            // STUFE 2: Retry mit besseren Prompts
            // ========================================
            System.out.println("\n⚠️ STUFE 2: Retry mit detailliertem Prompt...");
            
            // Für deutsche Rechnungen: Versuche Netto aus Brutto zu berechnen
            if (couldBeGermanInvoice(text) && hasGrossButNoNet(data)) {
                System.out.println("   🇩🇪 Deutsche Rechnung - berechne Netto aus Brutto...");
                data = tryCalculateNetFromGross(data);
                trustScore = trustScoreCalculator.calculate(data);
                System.out.println("   Trust-Score nach Berechnung: " + trustScore + "%");
            }
            
            // Retry
            String retryJson = extractor.extractWithRetry(text);
            InvoiceData retryData = new InvoiceData();
            parser.merge(retryData, retryJson);
            
            int retryScore = trustScoreCalculator.calculate(retryData);
            System.out.println("   Trust-Score nach Retry: " + retryScore + "%");
            
            // Verwende besseres Ergebnis
            if (retryScore > trustScore) {
                System.out.println("   ✅ Retry erfolgreich! Score: " + trustScore + "% → " + retryScore + "%");
                data = retryData;
                trustScore = retryScore;
            } else {
                System.out.println("   ⚠️ Retry brachte keine Verbesserung");
            }
            
            if (trustScore >= 85) {
                System.out.println("   ✅ Erfolgreich auf Stufe 2!\n");
                return data;
            }
            
            // ========================================
            // STUFE 3: Manuelle Validierung (NUR bei sehr schlechten Ergebnissen)
            // ========================================
            
            // WICHTIG: Stufe 3 nur wenn Trust-Score SEHR niedrig ist (< 50%)
            // Verhindert, dass gute Ergebnisse verschlechtert werden
            if (trustScore >= 50) {
                System.out.println("\n⚠️ Trust-Score " + trustScore + "% - keine weitere Validierung");
                System.out.println("   → Verwende Ergebnis aus Stufe 1/2 (Stufe 3 übersprungen)\n");
                return data;
            }
            
            System.out.println("\n🔬 STUFE 3: Validierung durch manuelle Durchrechnung...");
            System.out.println("   → NUR weil Trust-Score sehr niedrig ist (" + trustScore + "%)");
            System.out.println("   → LLM rechnet alle Positionen manuell durch...");
            
            // Speichere Original-Daten für Vergleich
            String originalNet = data.getNetAmount();
            String originalGross = data.getGrossAmount();
            
            String validationJson = extractor.validateAndRecalculate(
                text, 
                data.getNetAmount(), 
                data.getGrossAmount()
            );
            
            ValidationResult validation = parser.parseValidation(validationJson);
            
            // Prüfe ob Durchrechnung bessere Werte liefert
            if (validation.hasHighConfidence() && !validation.matches()) {
                System.out.println("   ⚠️ LLM fand Abweichung:");
                System.out.println("      Alt Netto: " + originalNet);
                System.out.println("      Neu Netto: " + validation.getRecalculatedNet());
                System.out.println("      Alt Brutto: " + originalGross);
                System.out.println("      Neu Brutto: " + validation.getRecalculatedGross());
                
                // Erstelle temporäre Kopie mit neuen Werten
                InvoiceData validatedData = new InvoiceData();
                validatedData.setCompanyName(data.getCompanyName());
                validatedData.setInvoiceNumber(data.getInvoiceNumber());
                validatedData.setInvoiceDate(data.getInvoiceDate());
                validatedData.setServicePeriod(data.getServicePeriod());
                validatedData.setNetAmount(validation.getRecalculatedNet());
                validatedData.setGrossAmount(validation.getRecalculatedGross());
                
                int newScore = trustScoreCalculator.calculate(validatedData);
                System.out.println("   Trust-Score mit neuen Werten: " + newScore + "%");
                
                // NUR überschreiben wenn DEUTLICH besser (mindestens +20 Punkte)
                if (newScore > trustScore + 20) {
                    System.out.println("   ✅ Validation DEUTLICH besser - übernehme neue Werte!");
                    System.out.println("      Score-Verbesserung: " + trustScore + "% → " + newScore + "%\n");
                    return validatedData;
                } else {
                    System.out.println("   ⚠️ Validation brachte keine deutliche Verbesserung");
                    System.out.println("      Behalte Original-Werte (safer choice)\n");
                }
            } else if (validation.matches()) {
                System.out.println("   ✅ Durchrechnung bestätigt: Werte sind korrekt");
            } else {
                System.out.println("   ⚠️ Durchrechnung nicht möglich (confidence: " + validation.getConfidence() + ")");
            }
            
            System.out.println("   → Verwende bestes Ergebnis mit Trust-Score: " + trustScore + "%\n");
            
            // ========================================
            // STUFE 4: Finale Qualitätsprüfung (nur wenn Stufe 3 verwendet wurde)
            // ========================================
            
            // Stufe 4 nur wenn wir bei Stufe 3 waren (als Double-Check)
            if (trustScore < 50) {
                System.out.println("\n🔍 STUFE 4: Finale Qualitätsprüfung (Self-Check)...");
                System.out.println("   → LLM prüft kritisch: Stimmen die extrahierten Daten?");
                
                try {
                    String qualityJson = extractor.performQualityCheck(text, data);
                    LlmResponseParser.QualityCheckResult qualityCheck = parser.parseQualityCheck(qualityJson);
                    
                    if (!qualityCheck.isAllCorrect() && qualityCheck.hasHighConfidence()) {
                        System.out.println("   ⚠️ LLM fand Probleme bei der Qualitätsprüfung:");
                        for (String issue : qualityCheck.getIssues()) {
                            System.out.println("      • " + issue);
                        }
                        
                        // Wenn LLM Korrekturen empfiehlt, setze Trust-Score niedriger
                        if (qualityCheck.shouldUseCorrections()) {
                            System.out.println("   ⚠️ Daten erscheinen inkorrekt - Trust-Score wird reduziert");
                            // Markiere als problematisch durch niedrigen Score
                            data.setNetAmount("0€"); // Triggert Trust-Score 0
                        } else {
                            System.out.println("   ℹ️ Behalte extrahierte Daten trotz Unsicherheit");
                        }
                    } else if (qualityCheck.isAllCorrect()) {
                        System.out.println("   ✅ Qualitätsprüfung bestätigt: Alle Daten korrekt!");
                    } else {
                        System.out.println("   ℹ️ Qualitätsprüfung inconclusive (confidence: " + qualityCheck.getConfidence() + ")");
                    }
                } catch (Exception e) {
                    System.err.println("   ⚠️ Qualitätsprüfung fehlgeschlagen: " + e.getMessage());
                }
            }
            
            return data;
            
        } catch (Exception e) {
            System.err.println("❌ Pipeline failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Pipeline failed: " + e.getMessage(), e);
        }
    }
    
    private boolean couldBeGermanInvoice(String text) {
        String lower = text.toLowerCase();
        return lower.contains("mwst") || 
               lower.contains("mehrwertsteuer") ||
               lower.contains("umsatzsteuer") ||
               lower.contains("€") ||
               lower.contains("eur") ||
               lower.contains("netto") ||
               lower.contains("brutto") ||
               lower.contains("zwischensumme") ||
               lower.contains("gesamtbetrag");
    }
    
    private boolean hasGrossButNoNet(InvoiceData data) {
        return (data.getGrossAmount() != null && !data.getGrossAmount().isEmpty()) &&
               (data.getNetAmount() == null || data.getNetAmount().isEmpty() || 
                data.getNetAmount().equals("0") || data.getNetAmount().contains("0,00"));
    }
    
    private InvoiceData tryCalculateNetFromGross(InvoiceData data) {
        try {
            String grossStr = data.getGrossAmount();
            String cleaned = grossStr.replaceAll("[^0-9.,]", "");
            
            if (cleaned.contains(",") && !cleaned.contains(".")) {
                cleaned = cleaned.replace(",", ".");
            } else if (cleaned.contains(",") && cleaned.contains(".")) {
                if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) {
                    cleaned = cleaned.replace(".", "").replace(",", ".");
                } else {
                    cleaned = cleaned.replace(",", "");
                }
            }
            
            double gross = Double.parseDouble(cleaned);
            
            // Versuche beide MwSt-Sätze
            double net19 = gross / 1.19;
            double net7 = gross / 1.07;
            
            double remainder19 = (net19 * 100) % 1;
            double remainder7 = (net7 * 100) % 1;
            
            double net;
            if (remainder19 < 0.01 || remainder19 > 0.99) {
                net = net19;
                System.out.println("      → 19% MwSt erkannt");
            } else if (remainder7 < 0.01 || remainder7 > 0.99) {
                net = net7;
                System.out.println("      → 7% MwSt erkannt");
            } else {
                net = net19;
                System.out.println("      → Default 19% MwSt");
            }
            
            String netFormatted = String.format("%.2f€", net).replace(".", ",");
            data.setNetAmount(netFormatted);
            System.out.println("      → Nettobetrag: " + netFormatted);
            
        } catch (Exception e) {
            System.err.println("      ⚠️ Berechnung fehlgeschlagen: " + e.getMessage());
        }
        
        return data;
    }
}