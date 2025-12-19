package InvoiceBot.llm;

import org.springframework.stereotype.Service;

import InvoiceBot.model.InvoiceData;



/* LLM Extractor für die Extraktion von Rechnungsdaten aus Texten.
  * Mehrstufiger Ansatz mit verschiedenen Prompts zur Verbesserung der Extraktionsgenauigkeit.

* Haupt-Prompt für die Rechnungsextraktion.
* Verwendet klare Anweisungen und Beispiele für bessere Ergebnisse.
* 
* Main Prompt for invoice data extraction.
* Uses clear instructions and examples for improved accuracy.
* 
    


*/
@Service
public class LlmExtractor {

    private final LlmClient client;

    public LlmExtractor(LlmClient client) {
        this.client = client;
    }

    /**
     * STUFE 1: Standard-Extraktion (funktioniert bei ~80%)
     */
    public String extract(String text) throws Exception {
        String truncated = text.length() > 2000 ? text.substring(0, 2000) : text;
        
        String prompt = """
        Extract invoice data into this strict JSON format:
        {"company_name":"","invoice_date":"","invoice_number":"","net_amount":"","gross_amount":"","service_period":""}
        
        IMPORTANT RULES:
        - company_name: Extract the VENDOR/SELLER name (who is BILLING), NOT the buyer
          * Look for "Von:", "From:", "Rechnungssteller:", company logo, header text
          * NEVER use the recipient/buyer name (often starts with "An:", "To:", "Rechnungsempfänger:")
        - net_amount: The amount WITHOUT tax (Netto, Subtotal). ALWAYS extract this if available.
        - gross_amount: The amount WITH tax (Brutto, Total). Can be same as net_amount if no tax.
        - If only one amount exists, use it for BOTH net_amount and gross_amount
        - Include currency symbol (€, $, etc.)
        - If a field is missing, use null
        
        INVOICE TEXT:
        %s
        """.formatted(truncated.replace("\"", "'"));

        return client.sendPrompt(prompt);
    }
    
    /**
     * STUFE 2: Retry mit detailliertem Prompt (fängt weitere ~15% ab)
     */
    public String extractWithRetry(String text) throws Exception {
        String truncated = text.length() > 2000 ? text.substring(0, 2000) : text;
        
        String prompt = """
        SECOND ATTEMPT - Extract invoice data more carefully:
        {"company_name":"","invoice_date":"","invoice_number":"","net_amount":"","gross_amount":"","service_period":""}
        
        CRITICAL INSTRUCTIONS:
        1. company_name: The BILLING company (who sent the invoice), NOT the recipient
           - Look at the TOP of the invoice for the sender
           - Common locations: Header, logo area, "Von:", "Rechnungssteller:"
           - AVOID: Addresses starting with "An:", "Rechnungsempfänger:"
           
        2. invoice_number: Look for "Invoice #", "Invoice Number:", "RE-", "Rechnungsnummer:", etc.
        
        3. invoice_date: The date the invoice was issued
        
        4. net_amount: Amount BEFORE tax (look for "Subtotal", "Net", "Netto", "Zwischensumme", "Gesamt netto")
           - This is MANDATORY - search the entire document
           - If you see a tax line (19%%, 7%%, etc.), the amount BEFORE that is net_amount
           - Look for "Summe netto", "Nettobetrag", "Zwischensumme"
           
        5. gross_amount: Amount AFTER tax (look for "Total", "Amount Due", "Brutto", "Gesamtbetrag", "Endbetrag")
           - Look for "Rechnungsbetrag", "Gesamtbetrag", "Total Amount"
           - If no tax is shown, this equals net_amount
           
        6. service_period: The period for which the service was provided (if mentioned)
        
        AMOUNT EXTRACTION TIPS:
        - If you see "Subtotal: 100€" and "19%% VAT: 19€" and "Total: 119€"
          → net_amount="100€", gross_amount="119€"
        - If you only see "Total: 100€" with no tax mentioned
          → net_amount="100€", gross_amount="100€"
        - NEVER leave net_amount as null if ANY amount is present
        
        INVOICE TEXT:
        %s
        """.formatted(truncated.replace("\"", "'"));

        return client.sendPrompt(prompt);
    }
    
    /**
     * STUFE 3: Validierung durch manuelle Durchrechnung
     * Wird NUR aufgerufen wenn Stufe 1+2 fehlgeschlagen sind.
     */
    public String validateAndRecalculate(String text, String currentNet, String currentGross) throws Exception {
        String truncated = text.length() > 2500 ? text.substring(0, 2500) : text;
        
        String prompt = """
        VALIDATION & RECALCULATION TASK:
        
        CURRENTLY EXTRACTED (possibly incorrect):
        - Net Amount: %s
        - Gross Amount: %s
        
        YOUR MISSION - HANDLE MIXED TAX RATES CORRECTLY:
        
        STEP 1: Find ALL line items/positions in the invoice table
        
        STEP 2: Check tax rate column (often labeled "MwSt", "USt.", "%% USt.", "VAT %%")
        - IMPORTANT: Invoices can have MIXED tax rates (7%% + 19%% on same invoice)
        - Group positions by their tax rate
        
        STEP 3: Calculate for EACH tax rate group:
        - Sum all net amounts in that group
        - Calculate gross for that group: group_net × (1 + tax_rate)
        
        STEP 4: Calculate final totals:
        - Total Net = Sum of all group nets
        - Total Gross = Sum of all group grosses (NOT net × single_rate!)
        
        EXAMPLE (office discount with mixed rates):
        Line items:
        - Position A: Net 37,96€, Tax 7%% → Gross: 37,96 × 1.07 = 40,62€
        - Position B: Net 132,02€, Tax 19%% → Gross: 132,02 × 1.19 = 157,04€
        
        Correct calculation:
        - Total Net: 37,96 + 132,02 = 169,98€
        - Total Gross: 40,62 + 157,04 = 197,66€
        
        WRONG would be: 169,98 × 1.19 = 202,28€ (ignores mixed rates!)
        
        RESPOND WITH THIS JSON:
        {
          "positions_found": [
            {"net": "37,96€", "tax_rate": "7%%", "gross_calculated": "40,62€"},
            {"net": "132,02€", "tax_rate": "19%%", "gross_calculated": "157,04€"}
          ],
          "recalculated_net": "sum of all position nets",
          "recalculated_gross": "sum of all position grosses",
          "has_mixed_tax_rates": true/false,
          "calculation_matches": true/false,
          "confidence": "high/medium/low"
        }
        
        CRITICAL RULES:
        - NEVER assume single tax rate - always check each position
        - Calculate gross per position based on ITS tax rate, then sum grosses
        - calculation_matches = true if totals match invoice (±1.00€ tolerance)
        - confidence = "high" if clear table with tax rate column found
        - confidence = "low" if no clear table structure or no tax rates visible
        
        INVOICE TEXT:
        %s
        """.formatted(
            currentNet != null ? currentNet : "not found", 
            currentGross != null ? currentGross : "not found",
            truncated.replace("\"", "'")
        );

        return client.sendPrompt(prompt);
    }
    
    /**
     * STUFE 4: Finale Qualitätsprüfung (Self-Check)
     * Wird nur aufgerufen wenn Stufe 3 verwendet wurde.
     * LLM prüft kritisch: Stimmen die extrahierten Daten wirklich?
     */
    public String performQualityCheck(String text, InvoiceData extractedData) throws Exception {
        String truncated = text.length() > 2500 ? text.substring(0, 2500) : text;
        
        String prompt = """
        QUALITY CHECK TASK - Critical Review:
        
        I have extracted the following data from an invoice. Please CRITICALLY review if this extraction is CORRECT.
        
        EXTRACTED DATA (to be verified):
        • Company Name: %s
        • Invoice Number: %s
        • Invoice Date: %s
        • Net Amount: %s
        • Gross Amount: %s
        • Service Period: %s
        
        YOUR TASK:
        Read the invoice text carefully and check EACH field:
        
        1. Does the company name match what's written on the invoice?
           - Is it the SENDER (who bills), not the recipient?
           - Look at the TOP of the document, near logo/header
        
        2. Does the invoice number match exactly?
           - Check for typos, missing digits, or confusion with other numbers
        
        3. Is the invoice date correct?
           - Not confused with due date, service period, or other dates?
        
        4. Are the amounts correct?
           - Net amount = amount BEFORE tax
           - Gross amount = amount AFTER tax (total to pay)
           - Do they match the invoice's final totals?
        
        5. Is the service period correct (if mentioned)?
           - Dates in correct format?
        
        RESPOND WITH JSON:
        {
          "all_correct": true/false,
          "issues_found": [
            {"field": "company_name", "issue": "description", "should_be": "correct value"},
            {"field": "net_amount", "issue": "description", "should_be": "correct value"}
          ],
          "confidence": "high/medium/low",
          "recommendation": "keep_extracted_data" or "use_corrections"
        }
        
        CRITICAL RULES:
        - Be VERY critical - look for ANY inconsistencies
        - all_correct = true ONLY if you're absolutely sure everything is perfect
        - If you find ANYTHING suspicious, add it to issues_found
        - confidence = "high" only if you can see clear text to verify against
        - recommendation = "use_corrections" only if you're confident the corrections are right
        
        INVOICE TEXT:
        %s
        """.formatted(
            extractedData.getCompanyName() != null ? extractedData.getCompanyName() : "not found",
            extractedData.getInvoiceNumber() != null ? extractedData.getInvoiceNumber() : "not found",
            extractedData.getInvoiceDate() != null ? extractedData.getInvoiceDate() : "not found",
            extractedData.getNetAmount() != null ? extractedData.getNetAmount() : "not found",
            extractedData.getGrossAmount() != null ? extractedData.getGrossAmount() : "not found",
            extractedData.getServicePeriod() != null ? extractedData.getServicePeriod() : "not found",
            truncated.replace("\"", "'")
        );

        return client.sendPrompt(prompt);
    }
}