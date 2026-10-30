package com.gymkhata.billing.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.gymkhata.admin.model.Gym;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.domain.Money;
import com.gymkhata.pt.model.CommissionEntry;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Renders GST invoices, printable A4 self-check-in QR posters, and monthly
 * trainer commission statements as PDFs (pure Java, OpenPDF) with embedded
 * Unicode TrueType font support for Devanagari (Hindi) and the Indian Rupee symbol (₹).
 */
@Component
public class PdfRenderer {

    private final Font h1;
    private final Font h2;
    private final Font normal;
    private final Font small;

    public PdfRenderer() {
        BaseFont unicodeBase = loadUnicodeFont();
        if (unicodeBase != null) {
            this.h1 = new Font(unicodeBase, 16, Font.BOLD);
            this.h2 = new Font(unicodeBase, 11, Font.BOLD);
            this.normal = new Font(unicodeBase, 9, Font.NORMAL);
            this.small = new Font(unicodeBase, 8, Font.NORMAL);
        } else {
            this.h1 = new Font(Font.HELVETICA, 16, Font.BOLD);
            this.h2 = new Font(Font.HELVETICA, 11, Font.BOLD);
            this.normal = new Font(Font.HELVETICA, 9, Font.NORMAL);
            this.small = new Font(Font.HELVETICA, 8, Font.NORMAL);
        }
    }

    private static BaseFont loadUnicodeFont() {
        try (InputStream is = PdfRenderer.class.getResourceAsStream("/fonts/NotoSansDevanagari-Regular.ttf")) {
            if (is != null) {
                byte[] ttfBytes = is.readAllBytes();
                return BaseFont.createFont(
                        "NotoSansDevanagari-Regular.ttf",
                        BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED,
                        true,
                        ttfBytes,
                        null
                );
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public byte[] renderInvoice(Invoice invoice, Gym gym, String memberName) {
        Document doc = new Document(PageSize.A4, 36, 36, 42, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();

        String title = switch (invoice.getType() == null ? "" : invoice.getType()) {
            case Invoice.TYPE_TAX_INVOICE -> "TAX INVOICE";
            case Invoice.TYPE_CREDIT_NOTE -> "CREDIT NOTE";
            case Invoice.TYPE_RECEIPT -> "RECEIPT";
            default -> "BILL OF SUPPLY";
        };

        Paragraph head = new Paragraph(title, h1);
        head.setAlignment(Element.ALIGN_CENTER);
        doc.add(head);

        Paragraph gymName = new Paragraph(gym != null ? gym.getName() : "", h2);
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
            PdfPCell c = new PdfPCell(new Phrase(h, h2));
            c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            c.setBorderWidth(0.5f);
            lines.addCell(c);
        }
        if (invoice.getLines() != null) {
            for (Invoice.Line l : invoice.getLines()) {
                lines.addCell(cell(l.getDescription() == null ? "" : l.getDescription()));
                lines.addCell(cell(l.getSac() == null ? "" : l.getSac()));
                lines.addCell(right(cell("₹" + Money.formatIndian(l.getTaxablePaise()))));
                lines.addCell(right(cell(taxText(l))));
                lines.addCell(right(cell("₹" + Money.formatIndian(l.getTotalPaise()))));
            }
        }
        doc.add(lines);

        PdfPTable totals = new PdfPTable(new float[]{6, 2});
        totals.setWidthPercentage(100);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.addCell(empty());
        totals.addCell(right(cell("Total: ₹" + Money.formatIndian(invoice.getTotalPaise()), h2)));
        doc.add(totals);

        if (invoice.getAmountInWords() != null) {
            doc.add(new Paragraph("Amount in words: " + invoice.getAmountInWords(), small));
        }
        if (invoice.getGstin() != null) {
            doc.add(new Paragraph("Tax payable as per the invoice. Subject to local jurisdiction.", small));
        }
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("This is a computer generated document.", small));
        doc.close();
        return out.toByteArray();
    }

    /**
     * Renders a printable A4 Self Check-In QR poster for the gym reception desk.
     */
    public byte[] renderQrPoster(Gym gym, String checkinUrl) {
        Document doc = new Document(PageSize.A4, 48, 48, 56, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();

        Paragraph gymHeader = new Paragraph(gym != null ? gym.getName() : "GymKhata", h1);
        gymHeader.setAlignment(Element.ALIGN_CENTER);
        doc.add(gymHeader);

        if (gym != null) {
            doc.add(centered(addressLine(gym)));
        }
        doc.add(new Paragraph(" "));

        Paragraph titleEn = new Paragraph("SELF CHECK-IN · SCAN QR CODE", h1);
        titleEn.setAlignment(Element.ALIGN_CENTER);
        doc.add(titleEn);

        Paragraph titleHi = new Paragraph("सेल्फ चेक-इन · क्यूआर कोड स्कैन करें", h2);
        titleHi.setAlignment(Element.ALIGN_CENTER);
        doc.add(titleHi);
        doc.add(new Paragraph(" "));

        try {
            BitMatrix matrix = new QRCodeWriter().encode(checkinUrl, BarcodeFormat.QR_CODE, 320, 320);
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", pngOut);
            Image qrImg = Image.getInstance(pngOut.toByteArray());
            qrImg.setAlignment(Element.ALIGN_CENTER);
            qrImg.scaleToFit(260, 260);
            doc.add(qrImg);
        } catch (Exception ignored) {
        }

        doc.add(new Paragraph(" "));
        Paragraph urlLine = new Paragraph(checkinUrl, h2);
        urlLine.setAlignment(Element.ALIGN_CENTER);
        doc.add(urlLine);
        doc.add(new Paragraph(" "));

        Paragraph stepsEn = new Paragraph(
                "1. Scan this QR code with your phone camera.\n"
                        + "2. First time: enter your mobile number and 4-digit PIN (sent on WhatsApp at enrolment).\n"
                        + "3. Next visits: one-tap check-in on your bound phone!",
                normal);
        stepsEn.setAlignment(Element.ALIGN_CENTER);
        doc.add(stepsEn);
        doc.add(new Paragraph(" "));

        Paragraph stepsHi = new Paragraph(
                "1. अपने फोन कैमरे से इस क्यूआर कोड को स्कैन करें।\n"
                        + "2. पहली बार: अपना मोबाइल नंबर और 4-अंकों का पिन दर्ज करें।\n"
                        + "3. अगली बार से: बस एक टैप में चेक-इन करें!",
                normal);
        stepsHi.setAlignment(Element.ALIGN_CENTER);
        doc.add(stepsHi);

        doc.close();
        return out.toByteArray();
    }

    /**
     * Renders a monthly Trainer Commission Statement PDF.
     */
    public byte[] renderCommissionStatement(Gym gym,
                                            String trainerName,
                                            String month,
                                            long accruedPaise,
                                            long reversalsPaise,
                                            long adjustmentsPaise,
                                            long paidPaise,
                                            long balanceDuePaise,
                                            List<CommissionEntry> entries) {
        Document doc = new Document(PageSize.A4, 36, 36, 42, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();

        Paragraph head = new Paragraph("TRAINER COMMISSION STATEMENT", h1);
        head.setAlignment(Element.ALIGN_CENTER);
        doc.add(head);

        Paragraph gymName = new Paragraph(gym != null ? gym.getName() : "GymKhata", h2);
        gymName.setAlignment(Element.ALIGN_CENTER);
        doc.add(gymName);
        doc.add(new Paragraph(" "));

        PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
        summary.setWidthPercentage(100);
        summary.addCell(labelValue("Trainer", trainerName));
        summary.addCell(labelValue("Month", month));
        summary.addCell(labelValue("Balance Due", "₹" + Money.formatIndian(balanceDuePaise)));
        summary.addCell(labelValue("Accrued", "₹" + Money.formatIndian(accruedPaise)));
        summary.addCell(labelValue("Reversals / Adjustments",
                "₹" + Money.formatIndian(reversalsPaise) + " / ₹" + Money.formatIndian(adjustmentsPaise)));
        summary.addCell(labelValue("Paid Out", "₹" + Money.formatIndian(paidPaise)));
        doc.add(summary);
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(new float[]{1.5f, 1.5f, 2f, 2.5f, 1.5f});
        table.setWidthPercentage(100);
        for (String h : new String[]{"Date", "Type", "Rule / Trigger", "Reason / Details", "Amount"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, h2));
            c.setBorderWidth(0.5f);
            c.setPadding(4);
            table.addCell(c);
        }
        if (entries != null) {
            for (CommissionEntry e : entries) {
                table.addCell(cell(e.getCreatedAt() == null ? "" : e.getCreatedAt().toString().substring(0, 10)));
                table.addCell(cell(e.getType()));
                String ruleDesc = e.getRuleSnapshot() != null ? e.getRuleSnapshot().getType() : (e.getTriggerType() == null ? "-" : e.getTriggerType());
                table.addCell(cell(ruleDesc));
                table.addCell(cell(e.getReason() == null ? "" : e.getReason()));
                String sign = e.getAmountPaise() < 0 ? "-₹" : "₹";
                table.addCell(right(cell(sign + Money.formatIndian(Math.abs(e.getAmountPaise())))));
            }
        }
        doc.add(table);
        doc.close();
        return out.toByteArray();
    }

    private String taxText(Invoice.Line l) {
        if (l.getGstRate() <= 0) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        if (l.getIgstPaise() > 0) {
            sb.append("IGST ₹").append(Money.formatIndian(l.getIgstPaise()));
        } else {
            sb.append("CGST ₹").append(Money.formatIndian(l.getCgstPaise()))
                    .append(" + SGST ₹").append(Money.formatIndian(l.getSgstPaise()));
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
        Paragraph p = new Paragraph(text, small);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private PdfPCell cell(String text) {
        return cell(text, normal);
    }

    private PdfPCell labelValue(String label, String value) {
        PdfPCell c = new PdfPCell();
        c.addElement(new Phrase(label + ": ", h2));
        c.addElement(new Phrase(value == null ? "" : value, normal));
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
        PdfPCell c = new PdfPCell(new Phrase("", normal));
        c.setBorderWidth(0);
        return c;
    }
}
