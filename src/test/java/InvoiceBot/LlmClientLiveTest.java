package InvoiceBot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import InvoiceBot.llm.LlmClient;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


@SpringBootTest(classes = LlmClient.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("integration")
// WICHTIG: Diese Zeile sorgt dafür, dass GitHub Actions den Test komplett ignoriert.
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
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