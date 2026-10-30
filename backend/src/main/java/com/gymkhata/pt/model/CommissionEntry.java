package com.gymkhata.pt.model;

import com.gymkhata.domain.CommissionRule;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable trainer commission ledger entry.
 * Every accrual snapshots the exact CommissionRule used at the moment of accrual.
 */
@Document("commission_ledger")
public class CommissionEntry {

    public static final String TYPE_ACCRUAL = "ACCRUAL";
    public static final String TYPE_REVERSAL = "REVERSAL";
    public static final String TYPE_ADJUSTMENT = "ADJUSTMENT";
    public static final String TYPE_PAYOUT = "PAYOUT";

    @Id
    private String id;
    private String gymId;
    private String trainerId;
    private String trainerName;
    private String memberId;
    private String ptEnrolmentId;
    private String membershipId;
    private String invoiceId;
    private String paymentId;
    private String creditNoteId;
    private String type; // ACCRUAL, REVERSAL, ADJUSTMENT, PAYOUT
    private String triggerType; // ON_PAYMENT, ON_SESSION_DELIVERED, REFERRAL, MANUAL
    private long amountPaise; // positive for ACCRUAL, negative for REVERSAL, signed for ADJUSTMENT, positive for PAYOUT
    private long taxableBasePaise;
    private String month; // YYYY-MM (IST)
    private CommissionRule ruleSnapshot;
    private String reason;
    private String payoutMode; // CASH, UPI, BANK_TRANSFER
    private LocalDate payoutDate;
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

    public String getTrainerId() {
        return trainerId;
    }

    public void setTrainerId(String trainerId) {
        this.trainerId = trainerId;
    }

    public String getTrainerName() {
        return trainerName;
    }

    public void setTrainerName(String trainerName) {
        this.trainerName = trainerName;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getPtEnrolmentId() {
        return ptEnrolmentId;
    }

    public void setPtEnrolmentId(String ptEnrolmentId) {
        this.ptEnrolmentId = ptEnrolmentId;
    }

    public String getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(String membershipId) {
        this.membershipId = membershipId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getCreditNoteId() {
        return creditNoteId;
    }

    public void setCreditNoteId(String creditNoteId) {
        this.creditNoteId = creditNoteId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(long amountPaise) {
        this.amountPaise = amountPaise;
    }

    public long getTaxableBasePaise() {
        return taxableBasePaise;
    }

    public void setTaxableBasePaise(long taxableBasePaise) {
        this.taxableBasePaise = taxableBasePaise;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public CommissionRule getRuleSnapshot() {
        return ruleSnapshot;
    }

    public void setRuleSnapshot(CommissionRule ruleSnapshot) {
        this.ruleSnapshot = ruleSnapshot;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getPayoutMode() {
        return payoutMode;
    }

    public void setPayoutMode(String payoutMode) {
        this.payoutMode = payoutMode;
    }

    public LocalDate getPayoutDate() {
        return payoutDate;
    }

    public void setPayoutDate(LocalDate payoutDate) {
        this.payoutDate = payoutDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
