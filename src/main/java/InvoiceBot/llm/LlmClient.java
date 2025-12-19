package InvoiceBot.llm;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


/* 
LM Client für die Interaktion mit der lokalen LLM API 

FALLS KOMMUNIKATION MIT DER LOKALEN LLM API FEHLERHAFT IST, ÜBERPRÜFE BITTE FOLGENDES:
- Läuft der LM Studio Server lokal auf dem erwarteten Port (Standard: 1234)?
- Ist die Firewall so konfiguriert, dass sie Verbindungen zu diesem Port zulässt?
- Ist die Basis-URL korrekt eingestellt (Standard: http://127.0.0.1:1234)?
- Wurde die richtige Modellbezeichnung verwendet (Standard: meta-llama-3.1-8b-instruct)?
- Sind die Netzwerkverbindungen stabil und funktioniert die localhost-Kommunikation einwandfrei? ggf. Teste im Browser ob die LLM Läuft 
- Haben suich durch ein Update der LLM API die Endpunkte oder das Anfrageformat geändert? GET/POST /v1/models etc (beim Start der LLM sollte in der Konsole die zu verwendenen Endpunkte stehen)

LLM Client for interacting with local LLM API 
IF COMMUNICATION WITH THE LOCAL LLM API FAILS, PLEASE CHECK THE FOLLOWING:
- Is the LM Studio server running locally on the expected port (default: 1234)?
- Is the firewall configured to allow connections to this port?
- Is the base URL set correctly (default: http://127.0.0.1:1234)?   
- Was the correct model name used (default: meta-llama-3.1-8b-instruct)?
- Are the network connections stable and is localhost communication functioning properly? If necessary, test in the
- Have the endpoints or request format changed due to an update of the LLM API? GET/POST /v1/models etc (the endpoints to be used should be displayed in the console when starting the LLM)

*/
@Component
public class LlmClient {

    private final String baseUrl;
    private final String modelName;
    private final OkHttpClient client;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public LlmClient(@Value("${llm.base-url:http://127.0.0.1:1234}") String baseUrl,
                     @Value("${llm.model:meta-llama-3.1-8b-instruct}") String modelName) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.modelName = modelName;
        
        // OkHttp Client mit großzügigen Timeouts
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .callTimeout(150, TimeUnit.SECONDS)
                .build();
        
        System.out.println("✅ LlmClient initialisiert:");
        System.out.println("   URL: " + this.baseUrl);
        System.out.println("   Model: " + this.modelName);
    }

    public String sendPrompt(String prompt) throws Exception {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", modelName);
        requestBody.put("temperature", 0.1);
        requestBody.put("max_tokens", 800);
        requestBody.put("stream", false);

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
            .put("role", "system")
            .put("content", "You are a highly accurate JSON extractor for invoices. Output valid JSON only. The company name is never 'UnitPlus InnoInvest GmbH' but most likely or a vendor like 'Zoom', 'Figma', 'Google Cloud'. Extract the exact company name from the text. For amounts, extract the numerical value and the currency symbol (e.g., '111.75 €' or '98.34'). If a net amount is missing but the gross amount and tax are present, calculate the net amount (Gross - Tax). If the invoice is from Finax, the company name is 'Finax o.c.p., a.s., Zweigniederlassung'."));
        messages.put(new JSONObject()
            .put("role", "user")
            .put("content", prompt));
        requestBody.put("messages", messages);

        String endpoint = baseUrl + "/v1/chat/completions";

        System.out.println("\n===== SENDING TO LLM =====");
        System.out.println("🌐 URL: " + endpoint);
        System.out.println("📦 Model: " + modelName);
        System.out.println("⏳ Warte auf Antwort (kann 10-30 Sekunden dauern)...");

        RequestBody body = RequestBody.create(requestBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(endpoint)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        long startTime = System.currentTimeMillis();

        try (Response response = client.newCall(request).execute()) {
            
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = response.code();
            
            System.out.println("⏱️ Antwort erhalten nach: " + duration + "ms");
            System.out.println("📥 Status Code: " + statusCode);

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                System.err.println("❌ ERROR Response: " + errorBody);
                throw new RuntimeException("LLM Error " + statusCode + ": " + errorBody);
            }

            String responseBody = response.body().string();
            System.out.println("📥 Response Length: " + responseBody.length() + " chars");

            JSONObject responseObj = new JSONObject(responseBody);
            String content = responseObj.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            System.out.println("✅ LLM ANSWER RECEIVED");
            System.out.println("===========================\n");
            
            return cleanJsonResponse(content);
            
        } catch (IOException e) {
            System.err.println("❌ IO ERROR: " + e.getMessage());
            System.err.println("   Mögliche Ursachen:");
            System.err.println("   - LM Studio Server läuft nicht");
            System.err.println("   - Falscher Port (erwartet: 1234)");
            System.err.println("   - Firewall blockiert Verbindung");
            e.printStackTrace();
            throw new RuntimeException("Verbindungsfehler zum LLM Server auf " + endpoint, e);
        }
    }

    private String cleanJsonResponse(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }
    
    public boolean isServerReachable() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/v1/models")
                    .get()
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }
}