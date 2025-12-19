package InvoiceBot.llm;

import InvoiceBot.model.InvoiceData;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/* 
Verantwortlich für das Parsen der Antworten des LLM.
 * Enthält Methoden zur Verarbeitung und Formatierung der extrahierten Daten.



Responsible for parsing the responses from the LLM.
 * Contains methods for processing and formatting the extracted data.
 */





@Service
public class LlmResponseParser {

    private static final DateTimeFormatter TARGET_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Locale GERMAN_LOCALE = Locale.GERMAN;

    /**
     * Standard merge - wie ursprünglich (void)
     */
    public void merge(InvoiceData target, String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            
            // Extract raw values
            String companyName = obj.optString("company_name", null);
            String invoiceNumber = obj.optString("invoice_number", null);
            String rawInvoiceDate = obj.optString("invoice_date", null);
            String rawServicePeriod = obj.optString("service_period", null);
            String rawGrossAmount = obj.optString("gross_amount", null);
            String rawNetAmount = obj.optString("net_amount", null);
            
            // Format and set values
            target.setCompanyName(companyName);
            target.setInvoiceNumber(invoiceNumber);
            target.setInvoiceDate(formatDate(rawInvoiceDate));
            target.setServicePeriod(formatServicePeriod(rawServicePeriod));
            target.setGrossAmount(formatCurrency(rawGrossAmount));
            target.setNetAmount(formatCurrency(rawNetAmount));
            
        } catch (Exception e) {
            throw new RuntimeException("JSON Parse Error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse validation response from Stage 3
     */
    public ValidationResult parseValidation(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            
            String recalculatedNet = obj.optString("recalculated_net", null);
            String recalculatedGross = obj.optString("recalculated_gross", null);
            boolean calculationMatches = obj.optBoolean("calculation_matches", false);
            String confidence = obj.optString("confidence", "low");
            
            return new ValidationResult(
                formatCurrency(recalculatedNet),
                formatCurrency(recalculatedGross),
                calculationMatches,
                confidence
            );
            
        } catch (Exception e) {
            System.err.println("⚠️ Validation JSON parse error: " + e.getMessage());
            return new ValidationResult(null, null, false, "error");
        }
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        DateTimeFormatter[] formatters = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM, d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalDate date = LocalDate.parse(dateStr.trim(), formatter);
                return date.format(TARGET_DATE_FORMATTER);
            } catch (DateTimeParseException ignored) {
            }
        }
        
        return dateStr;
    }

    private String formatServicePeriod(String periodStr) {
        if (periodStr == null || periodStr.trim().isEmpty()) {
            return null;
        }
        
        String[] separators = {" - ", " to ", " bis ", "-"};
        
        for (String sep : separators) {
            if (periodStr.contains(sep)) {
                String[] parts = periodStr.split(sep, 2);
                if (parts.length == 2) {
                    String start = formatDate(parts[0].trim());
                    String end = formatDate(parts[1].trim());
                    
                    if (start != null && end != null) {
                        return start + "-" + end;
                    }
                }
            }
        }
        
        return periodStr;
    }

    private String formatCurrency(String currencyStr) {
        if (currencyStr == null || currencyStr.trim().isEmpty()) {
            return null;
        }
        
        String cleanStr = currencyStr.trim();
        
        String currencySymbol = "€";
        if (cleanStr.contains("$")) {
            currencySymbol = "$";
        } else if (cleanStr.toLowerCase().contains("usd")) {
            currencySymbol = "$";
        } else if (cleanStr.toLowerCase().contains("eur")) {
            currencySymbol = "€";
        }
        
        cleanStr = cleanStr.replaceAll("[^\\d.,]", "").trim();
        
        int lastComma = cleanStr.lastIndexOf(',');
        int lastDot = cleanStr.lastIndexOf('.');
        
        if (lastComma > lastDot) {
            cleanStr = cleanStr.replace(".", "").replace(",", ".");
        } else if (lastDot > lastComma) {
            cleanStr = cleanStr.replace(",", "");
        }
        
        try {
            BigDecimal amount = new BigDecimal(cleanStr).setScale(2, RoundingMode.HALF_UP);
            NumberFormat nf = NumberFormat.getInstance(GERMAN_LOCALE);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            return nf.format(amount) + currencySymbol;
        } catch (NumberFormatException e) {
            return currencyStr;
        }
    }
    
    /**
     * Result from validation stage
     */
    public static class ValidationResult {
        private final String recalculatedNet;
        private final String recalculatedGross;
        private final boolean matches;
        private final String confidence;
        
        public ValidationResult(String recalculatedNet, String recalculatedGross, boolean matches, String confidence) {
            this.recalculatedNet = recalculatedNet;
            this.recalculatedGross = recalculatedGross;
            this.matches = matches;
            this.confidence = confidence;
        }
        
        public String getRecalculatedNet() { return recalculatedNet; }
        public String getRecalculatedGross() { return recalculatedGross; }
        public boolean matches() { return matches; }
        public String getConfidence() { return confidence; }
        public boolean hasHighConfidence() { return "high".equals(confidence); }
    }
    
    /**
     * Parse quality check response from Stage 4
     */
    public QualityCheckResult parseQualityCheck(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            
            boolean allCorrect = obj.optBoolean("all_correct", false);
            String confidence = obj.optString("confidence", "low");
            String recommendation = obj.optString("recommendation", "keep_extracted_data");
            
            // Parse issues_found array
            java.util.List<String> issues = new java.util.ArrayList<>();
            if (obj.has("issues_found")) {
                org.json.JSONArray issuesArray = obj.getJSONArray("issues_found");
                for (int i = 0; i < issuesArray.length(); i++) {
                    JSONObject issue = issuesArray.getJSONObject(i);
                    String field = issue.optString("field", "unknown");
                    String issueDesc = issue.optString("issue", "");
                    String shouldBe = issue.optString("should_be", "");
                    
                    issues.add(field + ": " + issueDesc + 
                              (shouldBe != null && !shouldBe.isEmpty() ? " (should be: " + shouldBe + ")" : ""));
                }
            }
            
            return new QualityCheckResult(allCorrect, confidence, recommendation, issues);
            
        } catch (Exception e) {
            System.err.println("⚠️ Quality Check JSON parse error: " + e.getMessage());
            return new QualityCheckResult(false, "error", "keep_extracted_data", 
                                         java.util.Collections.emptyList());
        }
    }
    
    /**
     * Result from quality check stage
     */
    public static class QualityCheckResult {
        private final boolean allCorrect;
        private final String confidence;
        private final String recommendation;
        private final java.util.List<String> issues;
        
        public QualityCheckResult(boolean allCorrect, String confidence, String recommendation, 
                                 java.util.List<String> issues) {
            this.allCorrect = allCorrect;
            this.confidence = confidence;
            this.recommendation = recommendation;
            this.issues = issues;
        }
        
        public boolean isAllCorrect() { return allCorrect; }
        public String getConfidence() { return confidence; }
        public boolean shouldUseCorrections() { return "use_corrections".equals(recommendation); }
        public java.util.List<String> getIssues() { return issues; }
        public boolean hasHighConfidence() { return "high".equals(confidence); }
    }
}