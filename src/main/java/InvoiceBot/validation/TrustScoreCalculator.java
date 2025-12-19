package InvoiceBot.validation;

import org.springframework.stereotype.Component;

import InvoiceBot.model.InvoiceData;


/**
 * Berechnet einen Trust-Score für geparste Rechnungsdaten.
 * Vereinfachte Logik: Entweder alle Pflichtfelder vorhanden (85%) oder durchgefallen (0%).
 * 
 * 
 * Calculates a trust score for parsed invoice data.
 * Simplified logic: Either all required fields are present (85%) or failed (0%
 */



@Component
public class TrustScoreCalculator {

    /**
     * Berechnet den Trust-Score für die gegebenen Rechnungsdaten.
     * 
     * Logik:
     * - Alle Pflichtfelder gefüllt UND plausibel? → 85-95%
     * - Felder müssen sinnvolle Werte haben (nicht nur vorhanden)
     * - Sonst → 0%
     *
     * @param data Die zu bewertenden Rechnungsdaten
     * @return Trust-Score: 0, 85 oder 95
     */
    public int calculate(InvoiceData data) {
        if (data == null) {
            return 0;
        }

        // Pflichtfelder prüfen
        if (isEmpty(data.getCompanyName())) return 0;
        if (isEmpty(data.getInvoiceNumber())) return 0;
        if (isEmpty(data.getInvoiceDate())) return 0;
        if (isEmpty(data.getNetAmount())) return 0;
        
        // Nettobetrag darf nicht "0" sein
        if (isZeroAmount(data.getNetAmount())) return 0;
        
        // NEUE VALIDIERUNG: Prüfe ob Werte plausibel sind
        
        // 1. Firmenname muss sinnvoll sein (mindestens 2 Buchstaben, nicht nur Zahlen)
        if (!isValidCompanyName(data.getCompanyName())) {
            System.out.println("   ⚠️ Trust-Score Warnung: Firmenname erscheint ungültig");
            return 0;
        }
        
        // 2. Rechnungsnummer muss plausibel sein
        if (!isValidInvoiceNumber(data.getInvoiceNumber())) {
            System.out.println("   ⚠️ Trust-Score Warnung: Rechnungsnummer erscheint ungültig");
            return 0;
        }
        
        // 3. Datum muss im richtigen Format sein
        if (!isValidDate(data.getInvoiceDate())) {
            System.out.println("   ⚠️ Trust-Score Warnung: Datum erscheint ungültig");
            return 0;
        }
        
        // 4. Betrag muss Zahlen enthalten
        if (!containsNumbers(data.getNetAmount())) {
            System.out.println("   ⚠️ Trust-Score Warnung: Nettobetrag enthält keine Zahlen");
            return 0;
        }
        
        // Optional: Leistungszeitraum kann fehlen (kein Problem)
        // Optional: Bruttobetrag kann fehlen (falls nur Netto angegeben)
        
        // Bonus: MwSt-Prüfung (7% oder 19%)
        if (!isEmpty(data.getGrossAmount()) && !isZeroAmount(data.getGrossAmount())) {
            if (has19PercentVAT(data.getNetAmount(), data.getGrossAmount())) {
                return 95; // Perfekt mit 19% MwSt
            }
            if (has7PercentVAT(data.getNetAmount(), data.getGrossAmount())) {
                return 95; // Perfekt mit 7% MwSt
            }
        }
        
        // Alle Pflichtfelder vorhanden und plausibel
        return 85;
    }
    
    /**
     * Prüft ob der Firmenname plausibel ist.
     */
    private boolean isValidCompanyName(String name) {
        if (name == null || name.trim().length() < 2) {
            return false;
        }
        
        // Muss mindestens 2 Buchstaben enthalten
        long letterCount = name.chars().filter(Character::isLetter).count();
        if (letterCount < 2) {
            return false;
        }
        
        // Darf nicht nur aus Sonderzeichen bestehen
        if (name.replaceAll("[^a-zA-ZäöüÄÖÜß]", "").length() < 2) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Prüft ob die Rechnungsnummer plausibel ist.
     */
    private boolean isValidInvoiceNumber(String number) {
        if (number == null || number.trim().length() < 2) {
            return false;
        }
        
        // Muss mindestens eine Zahl oder Buchstaben enthalten
        return number.matches(".*[a-zA-Z0-9].*");
    }
    
    /**
     * Prüft ob das Datum im erwarteten Format ist (dd.MM.yyyy).
     */
    private boolean isValidDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return false;
        }
        
        // Sollte mindestens ein Trennzeichen haben (. / -)
        if (!date.matches(".*[./-].*")) {
            return false;
        }
        
        // Sollte Zahlen enthalten
        return date.matches(".*\\d.*");
    }
    
    /**
     * Prüft ob ein String Zahlen enthält.
     */
    private boolean containsNumbers(String str) {
        if (str == null) return false;
        return str.matches(".*\\d.*");
    }
    
    /**
     * Prüft ob Brutto = Netto * 1.07 (7% MwSt für Lebensmittel).
     * Toleranz: ±0.50€ wegen Rundungen.
     */
    private boolean has7PercentVAT(String netAmount, String grossAmount) {
        try {
            double net = parseAmount(netAmount);
            double gross = parseAmount(grossAmount);
            
            double expectedGross = net * 1.07;
            double difference = Math.abs(gross - expectedGross);
            
            return difference <= 0.50;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Prüft ob ein Feld leer oder nur Placeholder ist.
     */
    private boolean isEmpty(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }

        String lower = value.toLowerCase().trim();
        return lower.equals("nicht vorhanden") ||
               lower.equals("n/a") ||
               lower.equals("null") ||
               lower.equals("unknown") ||
               lower.equals("-") ||
               lower.equals("?") ||
               lower.equals("0");
    }
    
    /**
     * Prüft ob ein Betrag 0 ist.
     */
    private boolean isZeroAmount(String amount) {
        if (amount == null) return true;
        
        // Extrahiere nur Zahlen
        String numbers = amount.replaceAll("[^0-9,.]", "");
        if (numbers.isEmpty()) return true;
        
        try {
            // Normalisiere zu Punkt als Dezimaltrenner
            String normalized = numbers.replace(",", ".");
            double value = Double.parseDouble(normalized);
            return value == 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Prüft ob Brutto = Netto * 1.19 (deutsche 19% MwSt).
     * Toleranz: ±0.50€ wegen Rundungen.
     */
    private boolean has19PercentVAT(String netAmount, String grossAmount) {
        try {
            double net = parseAmount(netAmount);
            double gross = parseAmount(grossAmount);
            
            // Erwarteter Bruttobetrag bei 19% MwSt
            double expectedGross = net * 1.19;
            
            // Toleranz: ±0.50€
            double difference = Math.abs(gross - expectedGross);
            
            return difference <= 0.50;
            
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extrahiert einen numerischen Wert aus einem Betrags-String.
     */
    private double parseAmount(String amount) {
        // Entferne Währungssymbole und Text
        String cleaned = amount.replaceAll("[^0-9.,\\-]", "");
        
        // Ersetze Komma durch Punkt für Parsing
        if (cleaned.contains(",") && !cleaned.contains(".")) {
            cleaned = cleaned.replace(",", ".");
        } else if (cleaned.contains(".") && cleaned.contains(",")) {
            // Beide vorhanden: Tausender-Trenner entfernen
            // Europäisch: 1.234,56 → 1234.56
            if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) {
                cleaned = cleaned.replace(".", "").replace(",", ".");
            } else {
                // US: 1,234.56 → 1234.56
                cleaned = cleaned.replace(",", "");
            }
        }
        
        return Double.parseDouble(cleaned);
    }

    /**
     * Gibt eine textuelle Beschreibung des Trust-Scores zurück.
     */
    public static String getScoreDescription(int score) {
        if (score >= 95) {
            return "Perfekt - Mit 19% MwSt validiert";
        } else if (score >= 85) {
            return "Sehr gut - Alle Pflichtfelder vorhanden";
        } else {
            return "Unvollständig - Pflichtfelder fehlen";
        }
    }
}