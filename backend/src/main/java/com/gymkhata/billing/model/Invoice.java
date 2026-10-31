package com.gymkhata.billing.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Issued invoice. Immutable once issued; corrections are credit notes.
 * Numbering is gapless per gym per financial year.
 */
@Document("invoices")
public class Invoice {

    public static final String TYPE_TAX_INVOICE = "TAX_INVOICE";
    public static final String TYPE_RECEIPT = "RECEIPT";
    public static final String TYPE_BILL_OF_SUPPLY = "BILL_OF_SUPPLY";
    public static final String TYPE_VOID = "VOID";
    public static final String TYPE_CREDIT_NOTE = "CREDIT_NOTE";

    public static final String STATUS_ISSUED = "ISSUED";
    public static final String STATUS_VOID = "VOID";

    @Id
    private String id;
    private String gymId;
    private String invoiceNumber;
    private String financialYear;
    private String type;
    private String memberId;
    private String membershipId;
    private String ptEnrolmentId;
    private String trainerId;
    private String originalInvoiceId;
    private List<Line> lines;
    private long totalPaise;
    private long joiningFeePaise;
    private long discountPaise;
    private String amountInWords;
    private String status;
    private String voidReason;
    private String placeOfSupplyState;
    private String supplierState;
    private boolean gstRegistered;
    private String gstin;
    private String payToken;
    private Instant createdAt;
    private String createdBy;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGymId() {
        return gymId;
    }

    public void setGymId(String gymId) {
        this.gymId = gymId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(String membershipId) {
        this.membershipId = membershipId;
    }

    public String getPtEnrolmentId() {
        return ptEnrolmentId;
    }

    public void setPtEnrolmentId(String ptEnrolmentId) {
        this.ptEnrolmentId = ptEnrolmentId;
    }

    public String getTrainerId() {
        return trainerId;
    }

    public void setTrainerId(String trainerId) {
        this.trainerId = trainerId;
    }

    public String getOriginalInvoiceId() {
        return originalInvoiceId;
    }

    public void setOriginalInvoiceId(String originalInvoiceId) {
        this.originalInvoiceId = originalInvoiceId;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }

    public long getTotalPaise() {
        return totalPaise;
    }

    public void setTotalPaise(long totalPaise) {
        this.totalPaise = totalPaise;
    }

    public long getJoiningFeePaise() {
        return joiningFeePaise;
    }

    public void setJoiningFeePaise(long joiningFeePaise) {
        this.joiningFeePaise = joiningFeePaise;
    }

    public long getDiscountPaise() {
        return discountPaise;
    }

    public void setDiscountPaise(long discountPaise) {
        this.discountPaise = discountPaise;
    }

    public String getAmountInWords() {
        return amountInWords;
    }

    public void setAmountInWords(String amountInWords) {
        this.amountInWords = amountInWords;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }

    public String getPlaceOfSupplyState() {
        return placeOfSupplyState;
    }

    public void setPlaceOfSupplyState(String placeOfSupplyState) {
        this.placeOfSupplyState = placeOfSupplyState;
    }

    public String getSupplierState() {
        return supplierState;
    }

    public void setSupplierState(String supplierState) {
        this.supplierState = supplierState;
    }

    public boolean isGstRegistered() {
        return gstRegistered;
    }

    public void setGstRegistered(boolean gstRegistered) {
        this.gstRegistered = gstRegistered;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public String getPayToken() {
        return payToken;
    }

    public void setPayToken(String payToken) {
        this.payToken = payToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Total taxable value across all lines (excluding GST).
     */
    public long totalTaxablePaise() {
        if (lines == null || lines.isEmpty()) {
            return totalPaise;
        }
        return lines.stream().mapToLong(Line::getTaxablePaise).sum();
    }

    /**
     * One invoice line. GST is snapshotted from effective-dated config and never
     * recomputed later. Lines always sum exactly to the invoice total.
     */
    public static class Line {
        private String itemType;
        private String description;
        private String sac;
        private int quantity;
        private long ratePaise;
        private long taxablePaise;
        private int gstRate;
        private String itcStatus;
        private String gstEffectiveFrom;
        private long cgstPaise;
        private long sgstPaise;
        private long igstPaise;
        private long totalPaise;

        public String getItemType() {
            return itemType;
        }

        public void setItemType(String itemType) {
            this.itemType = itemType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSac() {
            return sac;
        }

        public void setSac(String sac) {
            this.sac = sac;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public long getRatePaise() {
            return ratePaise;
        }

        public void setRatePaise(long ratePaise) {
            this.ratePaise = ratePaise;
        }

        public long getTaxablePaise() {
            return taxablePaise;
        }

        public void setTaxablePaise(long taxablePaise) {
            this.taxablePaise = taxablePaise;
        }

        public int getGstRate() {
            return gstRate;
        }

        public void setGstRate(int gstRate) {
            this.gstRate = gstRate;
        }

        public String getItcStatus() {
            return itcStatus;
        }

        public void setItcStatus(String itcStatus) {
            this.itcStatus = itcStatus;
        }

        public String getGstEffectiveFrom() {
            return gstEffectiveFrom;
        }

        public void setGstEffectiveFrom(String gstEffectiveFrom) {
            this.gstEffectiveFrom = gstEffectiveFrom;
        }

        public long getCgstPaise() {
            return cgstPaise;
        }

        public void setCgstPaise(long cgstPaise) {
            this.cgstPaise = cgstPaise;
        }

        public long getSgstPaise() {
            return sgstPaise;
        }

        public void setSgstPaise(long sgstPaise) {
            this.sgstPaise = sgstPaise;
        }

        public long getIgstPaise() {
            return igstPaise;
        }

        public void setIgstPaise(long igstPaise) {
            this.igstPaise = igstPaise;
        }

        public long getTotalPaise() {
            return totalPaise;
        }

        public void setTotalPaise(long totalPaise) {
            this.totalPaise = totalPaise;
        }
    }
}
