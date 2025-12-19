package InvoiceBot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import InvoiceBot.llm.LlmClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// ÄNDERUNG: classes = LlmClient.class
// Wir laden NICHT die ganze App, sondern nur diesen einen Service.
// Das macht den Test schneller und robuster gegen Fehler in anderen Klassen.
@SpringBootTest(classes = LlmClient.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("integration")
class LlmClientLiveTest {

    @Autowired
    private LlmClient client;

    @Test
    @DisplayName("LIVE TEST: Schickt echten Request an lokales LLM")
    void testRealConnectionToLlm() {
        // ... (Rest bleibt gleich wie vorher) ...
        
        boolean isOnline = client.isServerReachable();
        if (!isOnline) {
            System.out.println("⚠️ WARNUNG: LM Studio läuft nicht. Test übersprungen.");
        }
        assumeTrue(isOnline, "Test übersprungen, da LLM offline.");

        String response = null;
        try {
            // Ein sehr kurzer Prompt spart Zeit und Token
            response = client.sendPrompt("Sag Hallo");
        } catch (Exception e) {
            fail("LLM Fehler: " + e.getMessage());
        }

        System.out.println("🤖 Antwort: " + response);
        assertNotNull(response);
    }
}