package com.gymkhata.billing.pdf;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.domain.Money;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Renders a GST invoice / receipt as a PDF (pure Java, OpenPDF).
 */
@Component
public class PdfRenderer {

    private static final Font H1 = new Font(Font.BOLD, 16);
    private static final Font H2 = new Font(Font.BOLD, 11);
    private static final Font NORMAL = new Font(Font.NORMAL, 9);
    private static final Font SMALL = new Font(Font.NORMAL, 8);

    public byte[] renderInvoice(Invoice invoice, Gym gym, String memberName) {
        Document doc = new Document(PageSize.A4, 36, 36, 42, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();

        String title = switch (invoice.getType()) {
            case Invoice.TYPE_TAX_INVOICE -> "TAX INVOICE";
            case Invoice.TYPE_CREDIT_NOTE -> "CREDIT NOTE";
            case Invoice.TYPE_RECEIPT -> "RECEIPT";
            default -> "BILL OF SUPPLY";
        };

        Paragraph head = new Paragraph(title, H1);
        head.setAlignment(Element.ALIGN_CENTER);
        doc.add(head);

        Paragraph gymName = new Paragraph(gym != null ? gym.getName() : "", H2);
        gymName.setAlignment(Element.ALIGN_CENTER);
        doc.add(gymName);
        if (gym != null) {
            doc.add(centered(addressLine(gym)));
            if (gym.isGstRegistered() && gym.getGstin() != null) {
                doc.add(centered("GSTIN: " + gym.getGstin()));
            }
        }
        doc.add(new Paragraph(" "));

        PdfPTable meta = new PdfPTable(new float[]{1, 1, 1, 1});
        meta.setWidthPercentage(100);
        meta.addCell(labelValue("Invoice No", invoice.getInvoiceNumber()));
        meta.addCell(labelValue("Date", invoice.getCreatedAt() == null ? "" : invoice.getCreatedAt().toString()));
        meta.addCell(labelValue("Financial Year", invoice.getFinancialYear()));
        meta.addCell(labelValue("Member", memberName == null ? "" : memberName));
        doc.add(meta);
        doc.add(new Paragraph(" "));

        PdfPTable lines = new PdfPTable(new float[]{4, 1, 1.6f, 1.6f, 1.6f});
        lines.setWidthPercentage(100);
        lines.setWidths(new float[]{4, 1, 1.6f, 1.6f, 1.6f});
        String[] headers = {"Description", "SAC", "Taxable", "GST", "Total"};
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, H2));
            c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            c.setBorderWidth(0.5f);
            lines.addCell(c);
        }
        for (Invoice.Line l : invoice.getLines()) {
            lines.addCell(cell(l.getDescription() == null ? "" : l.getDescription()));
            lines.addCell(cell(l.getSac() == null ? "" : l.getSac()));
            lines.addCell(right(cell("" + Money.formatIndian(l.getTaxablePaise()))));
            lines.addCell(right(cell(taxText(l))));
            lines.addCell(right(cell("" + Money.formatIndian(l.getTotalPaise()))));
        }
        doc.add(lines);

        PdfPTable totals = new PdfPTable(new float[]{6, 2});
        totals.setWidthPercentage(100);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.addCell(empty());
        totals.addCell(right(cell("Total: " + Money.formatIndian(invoice.getTotalPaise()), H2)));
        doc.add(totals);

        if (invoice.getAmountInWords() != null) {
            doc.add(new Paragraph("Amount in words: " + invoice.getAmountInWords(), SMALL));
        }
        if (invoice.getGstin() != null) {
            doc.add(new Paragraph("Tax payable as per the invoice. Subject to local jurisdiction.", SMALL));
        }
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("This is a computer generated document.", SMALL));
        doc.close();
        return out.toByteArray();
    }

    private String taxText(Invoice.Line l) {
        if (l.getGstRate() <= 0) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        if (l.getIgstPaise() > 0) {
            sb.append("IGST ").append(Money.formatIndian(l.getIgstPaise()));
        } else {
            sb.append("CGST ").append(Money.formatIndian(l.getCgstPaise()))
                    .append(" + SGST ").append(Money.formatIndian(l.getSgstPaise()));
        }
        sb.append(" @").append(l.getGstRate()).append("%");
        return sb.toString();
    }

    private String addressLine(Gym gym) {
        StringBuilder sb = new StringBuilder();
        if (gym.getAddress() != null) {
            sb.append(gym.getAddress()).append(", ");
        }
        if (gym.getCity() != null) {
            sb.append(gym.getCity());
        }
        if (gym.getState() != null) {
            sb.append(", ").append(gym.getState());
        }
        return sb.toString();
    }

    private Paragraph centered(String text) {
        Paragraph p = new Paragraph(text, SMALL);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private PdfPCell cell(String text) {
        return cell(text, NORMAL);
    }

    private PdfPCell labelValue(String label, String value) {
        PdfPCell c = new PdfPCell();
        c.addElement(new Phrase(label + ": ", H2));
        c.addElement(new Phrase(value == null ? "" : value, NORMAL));
        c.setBorderWidth(0.5f);
        c.setPadding(4);
        return c;
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setBorderWidth(0.5f);
        c.setPadding(4);
        return c;
    }

    private PdfPCell right(PdfPCell c) {
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private PdfPCell empty() {
        PdfPCell c = new PdfPCell(new Phrase("", NORMAL));
        c.setBorderWidth(0);
        return c;
    }
}
