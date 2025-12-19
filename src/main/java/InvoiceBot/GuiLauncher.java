package InvoiceBot;

import InvoiceBot.gui.InvoiceBotGui;
import InvoiceBot.llm.LlmClient;
import InvoiceBot.llm.LlmExtractor;
import InvoiceBot.llm.LlmResponseParser;
import InvoiceBot.parser.InvoiceParser;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;





/* GUI Launcher für die InvoiceBot-Anwendung.
 * Initialisiert die GUI und setzt notwendige Systemeigenschaften.

GUI Launcher for the InvoiceBot application.
 * Initializes the GUI and sets necessary system properties.
 */





public class GuiLauncher {
    
    public static void main(String[] args) {
        // macOS Settings
        System.setProperty("apple.awt.application.name", "InvoiceBot");
        System.setProperty("java.net.preferIPv4Stack", "true");
        
        // Unbehandelte Fehler abfangen
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            
            System.err.println("Uncaught exception in thread " + t.getName() + ": " + exceptionAsString);
            
            JOptionPane.showMessageDialog(null, 
                "Ein unerwarteter Fehler ist aufgetreten:\n" + e.getMessage(), 
                "Kritischer Fehler", 
                JOptionPane.ERROR_MESSAGE);
        });
        
        // Manuelle Dependency-Initialisierung (ohne Spring Boot)
        // LlmClient erwartet baseUrl und modelName als Parameter (siehe Konstruktor)
        String baseUrl = "http://127.0.0.1:1234";
        String modelName = "meta-llama-3.1-8b-instruct";
        
        LlmClient llmClient = new LlmClient(baseUrl, modelName);
        LlmResponseParser responseParser = new LlmResponseParser();
        LlmExtractor extractor = new LlmExtractor(llmClient);
        InvoiceParser parser = new InvoiceParser(extractor, responseParser, null);
        
        // GUI starten
        SwingUtilities.invokeLater(() -> {
            try {
                // System Look & Feel verwenden für natives Aussehen
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Fallback zum Standard L&F
                e.printStackTrace();
            }
            
            InvoiceBotGui gui = new InvoiceBotGui(parser, llmClient);
            gui.setVisible(true);
        });
    }
}