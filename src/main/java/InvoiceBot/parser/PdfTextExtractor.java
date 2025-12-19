package InvoiceBot.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;


/* PDF-Text-Extraktor.
 * Verwendet Apache PDFBox zur Extraktion von Text aus PDF-Dokumenten.
 */

@Service
public class PdfTextExtractor {

    public static String extract(File pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}