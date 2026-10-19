package com.gymkhata.billing.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A payment against an invoice. UPI payments submitted by members on the public
 * pay page stay PENDING_VERIFICATION until the Gym Owner confirms; payments
 * recorded by the owner at the counter are confirmed immediately.
 */
@Document("payments")
public class Payment {

    public static final String METHOD_UPI = "UPI";
    public static final String METHOD_CASH = "CASH";
    public static final String METHOD_CARD = "CARD";

    public static final String STATUS_PENDING = "PENDING_VERIFICATION";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    public static final String RECORDED_BY_OWNER = "OWNER";
    public static final String RECORDED_BY_MEMBER = "MEMBER";

    @Id
    private String id;
    private String gymId;
    private String invoiceId;
    private String memberId;
    private long amountPaise;
    private String method;
    private String status;
    private String utr;
    private String recordedBy;
    private Instant confirmedAt;
    private Instant createdAt;

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

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(long amountPaise) {
        this.amountPaise = amountPaise;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUtr() {
        return utr;
    }

    public void setUtr(String utr) {
        this.utr = utr;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
