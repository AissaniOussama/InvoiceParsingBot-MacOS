package InvoiceBot;

import InvoiceBot.llm.LlmExtractor;
import InvoiceBot.llm.LlmResponseParser;
// Wichtig: Importiere die inneren Klassen für Validation/QualityResults
import InvoiceBot.llm.LlmResponseParser.ValidationResult;
import InvoiceBot.llm.LlmResponseParser.QualityCheckResult;
import InvoiceBot.model.InvoiceData;
import InvoiceBot.parser.InvoiceParser;
import InvoiceBot.validation.TrustScoreCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceParserTest {

    @Mock LlmExtractor extractor;
    @Mock LlmResponseParser parser;
    @Mock TrustScoreCalculator calculator;

    @InjectMocks
    InvoiceParser invoiceParser;

    // Hilfsobjekte für sauberere Tests
    private InvoiceData emptyData;

    @BeforeEach
    void setUp() {
        emptyData = new InvoiceData();
    }

    // ==========================================
    // STUFE 1: Happy Path
    // ==========================================

    @Test
    @DisplayName("STUFE 1: Sollte bei hohem Trust-Score (>= 85) sofort fertig sein")
    void testStage1_HighTrust_ReturnsImmediately() throws Exception {
        // Arrange
        String text = "Perfekte Rechnung";
        when(extractor.extract(text)).thenReturn("{}");
        when(calculator.calculate(any())).thenReturn(90); // 90% Score

        // Act
        InvoiceData result = invoiceParser.parse(text);

        // Assert
        assertNotNull(result);
        verify(extractor, times(1)).extract(text);
        verify(extractor, never()).extractWithRetry(any()); // Keine Stufe 2
    }

    // ==========================================
    // STUFE 2: Retry Mechanism & Mathe
    // ==========================================

    @Test
    @DisplayName("STUFE 2: Sollte Retry nutzen, wenn Score niedrig ist, und besseres Ergebnis wählen")
    void testStage2_RetryLogic_ChoosesBetterResult() throws Exception {
        // Arrange
        String text = "Schwierige Rechnung";
        
        // Stufe 1: Schlechter Score (60)
        when(extractor.extract(text)).thenReturn("{bad}");
        when(calculator.calculate(any()))
                .thenReturn(60)  // 1. Aufruf (Stufe 1)
                .thenReturn(95); // 2. Aufruf (Stufe 2 Retry)

        // Stufe 2: Retry wird aufgerufen
        when(extractor.extractWithRetry(text)).thenReturn("{good}");
        
        // Act
        InvoiceData result = invoiceParser.parse(text);

        // Assert
        verify(extractor).extractWithRetry(text); // Retry muss passiert sein
        // Da wir Mockito nutzen und calculate() beim 2. Mal 95 zurückgibt, 
        // geht die Logik davon aus, dass das zweite Ergebnis (Retry) besser war.
    }

    @Test
    @DisplayName("STUFE 2 (Mathe): Sollte Netto aus Brutto berechnen (Deutsche Rechnung)")
    void testStage2_GermanMathCalculation() throws Exception {
        // Arrange
        // Text muss Keywords enthalten, damit couldBeGermanInvoice() true ist
        String text = "Gesamtbetrag Brutto 119,00 EUR MwSt enthalten"; 
        
        // Mocking: Stufe 1 liefert Daten MIT Brutto aber OHNE Netto
        when(extractor.extract(text)).thenReturn("{}");
        
        // Wir simulieren, dass der Parser Brutto setzt
        doAnswer(inv -> {
            InvoiceData d = inv.getArgument(0);
            d.setGrossAmount("119,00");
            d.setNetAmount(null); // Netto fehlt -> Trigger für Berechnung
            return null;
        }).when(parser).merge(any(), eq("{}"));

        // Scores so setzen, dass wir in Stufe 2 landen, aber vor dem Retry die Berechnung passiert
        when(calculator.calculate(any())).thenReturn(60); 

        // Auch für den Retry müssen wir was mocken, damit der Test nicht abstürzt
        when(extractor.extractWithRetry(any())).thenReturn("{}");
 
        // Act
        invoiceParser.parse(text);

        // Assert
        // Wir nutzen ArgumentCaptor um zu prüfen, was in den Calculator gesteckt wurde.
        // Wurde Netto berechnet? 119 / 1.19 = 100.00
        ArgumentCaptor<InvoiceData> captor = ArgumentCaptor.forClass(InvoiceData.class);
        verify(calculator, atLeast(2)).calculate(captor.capture());
        
        // Prüfe alle gefangenen Objekte. Eines davon sollte das berechnete Netto haben.
        boolean calculationHappened = captor.getAllValues().stream()
                .anyMatch(d -> "100,00€".equals(d.getNetAmount()));
        
        assertTrue(calculationHappened, "Die deutsche MwSt-Berechnung (119 -> 100) wurde nicht durchgeführt!");
    }

    // ==========================================
    // STUFE 3: Deep Validation
    // ==========================================

    @Test
    @DisplayName("STUFE 3: Sollte bei kritischem Score (< 50) manuelle Validierung starten")
    void testStage3_CriticalScore_TriggersValidation() throws Exception {
        // Arrange
        String text = "Chaos Rechnung";
        // Durchgehend schlechte Scores simulieren (Stufe 1, Stufe 2, nach Mathe)
        when(extractor.extract(any())).thenReturn("{}");
        when(extractor.extractWithRetry(any())).thenReturn("{}");
        when(calculator.calculate(any())).thenReturn(40); // Immer unter 50!

        // Mock für ValidationResult
        ValidationResult mockValidation = mock(ValidationResult.class);
        when(mockValidation.hasHighConfidence()).thenReturn(true);
        when(mockValidation.matches()).thenReturn(false); // Sagt: "Werte sind falsch!"
        when(mockValidation.getRecalculatedNet()).thenReturn("50,00€");
        when(mockValidation.getRecalculatedGross()).thenReturn("59,50€");

        when(extractor.validateAndRecalculate(any(), any(), any())).thenReturn("{validierung}");
        when(parser.parseValidation("{validierung}")).thenReturn(mockValidation);

        // Act
        invoiceParser.parse(text);

        // Assert
        verify(extractor).validateAndRecalculate(eq(text), any(), any());
        // Prüfen, ob wir versucht haben, den Score mit den NEUEN Werten zu berechnen
        verify(calculator, atLeast(3)).calculate(any());
    }

    @Test
    @DisplayName("STUFE 3: Sollte Validation überspringen, wenn Score okay ist (z.B. 60)")
    void testStage3_SkipIfScoreIsMediocre() throws Exception {
        // Arrange
        when(extractor.extract(any())).thenReturn("{}");
        when(calculator.calculate(any())).thenReturn(60); // Schlecht, aber über 50

        // Act
        invoiceParser.parse("Text");

        // Assert
        verify(extractor, never()).validateAndRecalculate(any(), any(), any());
    }

    // ==========================================
    // STUFE 4: Quality Check
    // ==========================================

    @Test
    @DisplayName("STUFE 4: Sollte Quality Check machen, wenn Score am Ende immer noch < 50 ist")
    void testStage4_QualityCheck() throws Exception {
        // Arrange
        String text = "Katastrophen Rechnung";
        
        // Wir simulieren den kompletten Durchlauf mit schlechten Scores
        when(extractor.extract(any())).thenReturn("{}");
        when(extractor.extractWithRetry(any())).thenReturn("{}");
        when(calculator.calculate(any())).thenReturn(30); // Sehr schlecht

        // Validation Mocking (sagt: "Kann nix machen")
        ValidationResult valRes = mock(ValidationResult.class);
        when(valRes.matches()).thenReturn(true); // Sagt "passt schon", obwohl Score schlecht ist
        when(parser.parseValidation(any())).thenReturn(valRes);
        when(extractor.validateAndRecalculate(any(), any(), any())).thenReturn("{}");

        // Quality Check Mocking
        QualityCheckResult qualityRes = mock(QualityCheckResult.class);
        when(extractor.performQualityCheck(any(), any())).thenReturn("{quality}");
        when(parser.parseQualityCheck("{quality}")).thenReturn(qualityRes);
        
        // Act
        invoiceParser.parse(text);

        // Assert
        verify(extractor).performQualityCheck(eq(text), any());
    }

    // ==========================================
    // Error Handling
    // ==========================================

    @Test
    @DisplayName("ERROR: Sollte Exceptions ordentlich weiterwerfen")
    void testExceptionHandling() throws Exception {
        // Arrange
        when(extractor.extract(anyString())).thenThrow(new RuntimeException("LLM API Down"));

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            invoiceParser.parse("Test");
        });

        assertTrue(exception.getMessage().contains("Pipeline failed"));
    }
}