package InvoiceBot;

import javax.swing.SwingUtilities;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import InvoiceBot.gui.InvoiceBotGui;
import InvoiceBot.llm.LlmClient;
import InvoiceBot.parser.InvoiceParser;


@SpringBootApplication
public class InvoiceBotApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false"); 
        System.setProperty("java.net.preferIPv4Stack", "true");

        SpringApplication app = new SpringApplication(InvoiceBotApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        var context = app.run(args);

        InvoiceParser parser = context.getBean(InvoiceParser.class);
        LlmClient llmClient = context.getBean(LlmClient.class);

        SwingUtilities.invokeLater(() -> {
            InvoiceBotGui gui = new InvoiceBotGui(parser, llmClient);
            gui.setVisible(true);
        });
    }
}
