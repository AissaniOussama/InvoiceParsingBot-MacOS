package InvoiceBot;

import InvoiceBot.llm.LlmExtractor;
import InvoiceBot.model.InvoiceData;
import InvoiceBot.parser.InvoiceParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// NEU: Das ist der Import für Spring Boot 3.4+
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("integration")
class InvoicePipelineIntegrationTest {

    @Autowired
    private InvoiceParser invoiceParser;

    // UPDATE: @MockBean ist raus, @MockitoBean ist rein!
    @MockitoBean 
    private LlmExtractor llmExtractor;

    @Test
    @DisplayName("PIPELINE TEST: Modern & Clean mit @MockitoBean")
    void testCompletePipelineFlow() throws Exception {
        System.out.println("\n🚀 START: Pipeline Integration Test (Spring Boot 3.4 Style)");

        // 1. ARRANGE
        String fakePdfText = """
            Rechnung Nr. NEW-ERA-2025
            Datum: 01.01.2025
            Von: Future Corp
            Betrag: 500,00 €
            """;
        
        String expectedJson = """
            {
                "invoice_number": "NEW-ERA-2025",
                "invoice_date": "01.01.2025",
                "company_name": "Future Corp",
                "net_amount": "500,00€",
                "gross_amount": "500,00€"
            }
            """;

        // 2. MOCKING
        when(llmExtractor.extract(anyString())).thenAnswer(invocation -> {
            System.out.println("🤖 MOCK: LLM simuliert Antwort...");
            return expectedJson;
        });

        // 3. ACT
        InvoiceData result = invoiceParser.parse(fakePdfText);

        // 4. ASSERT
        
        assertNotNull(result, "Ergebnis darf nicht null sein");
        assertEquals("NEW-ERA-2025", result.getInvoiceNumber());
        
        System.out.println("✅ TEST BESTANDEN: Modernes Mocking erfolgreich.");
    }
}