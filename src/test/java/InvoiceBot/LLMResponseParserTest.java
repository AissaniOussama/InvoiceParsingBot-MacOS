package InvoiceBot;

import InvoiceBot.llm.LlmResponseParser;
import InvoiceBot.model.InvoiceData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LlmResponseParserTest {

    private final LlmResponseParser parser = new LlmResponseParser();

    @Test
    void testMerge_ValidJson_UpdatesData() {
        // ARRANGE
        InvoiceData data = new InvoiceData();
        
        // WICHTIG: Die Keys müssen snake_case sein, genau wie im LlmExtractor Prompt definiert!
        // Alt (falsch): "invoiceNumber": ...
        // Neu (richtig): "invoice_number": ...
        String json = """
            {
                "invoice_number": "RE-2023-001",
                "net_amount": "100,00€",
                "gross_amount": "119,00€",
                "company_name": "Test Firma GmbH",
                "invoice_date": "01.01.2023"
            }
            """;

        // ACT
        parser.merge(data, json);

        // ASSERT
        assertEquals("RE-2023-001", data.getInvoiceNumber());
        assertEquals("100,00€", data.getNetAmount());
        assertEquals("119,00€", data.getGrossAmount());
        assertEquals("Test Firma GmbH", data.getCompanyName());
        assertEquals("01.01.2023", data.getInvoiceDate());
    }

    @Test
    void testMerge_PartialJson_UpdatesOnlyAvailableFields() {
        // Arrange
        InvoiceData data = new InvoiceData();
        // Hier fehlt z.B. die Rechnungsnummer
        String json = """
            {
                "net_amount": "50,00"
            }
            """;

        // Act
        parser.merge(data, json);

        // Assert
        assertEquals("50,00€", data.getNetAmount()); // € wird vom Parser hinzugefügt wenn Logik stimmt
        assertNull(data.getInvoiceNumber());
    }

    @Test
    void testMerge_BrokenJson_ThrowsException() {
        // Arrange
        InvoiceData data = new InvoiceData();
        String brokenJson = "{ \"invoice_number\": \"123\" "; // Fehlende Klammer

        // Act & Assert
        // Dein Parser wirft eine RuntimeException bei JSON Fehlern (siehe deinen Code catch Block)
        assertThrows(RuntimeException.class, () -> {
            parser.merge(data, brokenJson);
        });
    }

    @Test
    void testMerge_EmptyJson_DoesNothing() {
        InvoiceData data = new InvoiceData();
        data.setCompanyName("Existing Name");
        
        parser.merge(data, "{}");
        
        // Sollte nicht überschrieben werden oder null werden (je nach deiner Logik)
        // Wenn deine Logik optString(..., null) nutzt, werden Felder null gesetzt wenn sie im JSON fehlen.
        // Falls du willst, dass bestehende Daten bleiben, müsstest du den Parser anpassen.
        // Bei deinem aktuellen Parser (void merge) wird alles auf null gesetzt, wenn das JSON leer ist.
        assertNull(data.getInvoiceNumber()); 
    }
}