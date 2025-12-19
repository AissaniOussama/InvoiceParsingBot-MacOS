package InvoiceBot.model;

/**
 * Modellklasse für Rechnungsdaten.
 * 
 * Model class for invoice data.
 * Enthält Felder wie Firmenname, Rechnungsdatum, Rechnungsnummer, Bruttobetrag, Nettobetrag und Leistungszeitraum.
 * Contains fields like company name, invoice date, invoice number, gross amount, net amount, and service period.
 */
public class InvoiceData {

    // =====================
    // Fields
    // =====================

    private String companyName;
    private String invoiceDate;
    private String invoiceNumber;
    private String grossAmount;
    private String netAmount;
    private String servicePeriod;

    // =====================
    // Getters
    // =====================

    public String getCompanyName() { return companyName; }
    public String getInvoiceDate() { return invoiceDate; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getGrossAmount() { return grossAmount; }
    public String getNetAmount() { return netAmount; }
    public String getServicePeriod() { return servicePeriod; }

    // =====================
    // Setters
    // =====================

    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setInvoiceDate(String invoiceDate) { this.invoiceDate = invoiceDate; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public void setGrossAmount(String grossAmount) { this.grossAmount = grossAmount; }
    public void setNetAmount(String netAmount) { this.netAmount = netAmount; }
    public void setServicePeriod(String servicePeriod) { this.servicePeriod = servicePeriod; }

    // =====================
    // Utility Methods
    // =====================

    /**
     * Prüft, ob die essentiellen Felder unvollständig sind.
     *
     * Checks if essential fields are missing (companyName, invoiceNumber, grossAmount).
     */
    public boolean isIncomplete() {
        return companyName == null || invoiceNumber == null || grossAmount == null;
    }

    /**
     * Prüft, ob alle Felder vorhanden sind.
     *
     * Checks if all fields are populated.
     */
    public boolean isComplete() {
        return companyName != null && invoiceNumber != null && grossAmount != null
                && netAmount != null && invoiceDate != null && servicePeriod != null;
    }

    @Override
    public String toString() {
        return "InvoiceData{" +
                "companyName='" + companyName + '\'' +
                ", invoiceDate='" + invoiceDate + '\'' +
                ", invoiceNumber='" + invoiceNumber + '\'' +
                ", grossAmount='" + grossAmount + '\'' +
                ", netAmount='" + netAmount + '\'' +
                ", servicePeriod='" + servicePeriod + '\'' +
                '}';
    }
}
