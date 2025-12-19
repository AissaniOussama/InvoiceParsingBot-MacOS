package InvoiceBot.parser;

import InvoiceBot.model.InvoiceData;

/**
 * Ergebnis der Parsing-Operation.
 * Enthält die extrahierten Rechnungsdaten, einen Erfolgsindikator und eine Fehlermeldung (falls vorhanden).
 */

public record ParseResult(InvoiceData data, boolean success, String error) {}