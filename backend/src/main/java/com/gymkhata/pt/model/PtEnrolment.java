package com.gymkhata.pt.model;

import com.gymkhata.domain.CommissionRule;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A Personal Training enrolment (member + PT package + trainer).
 * Completely independent from gym Membership expiry/sessions.
 */
@Document("pt_enrolments")
public class PtEnrolment {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    private String id;
    private String gymId;
    private String memberId;
    private String memberName;
    private String ptPackageId;
    private String ptPackageName;
    private String packageType;
    private String trainerId;
    private String trainerName;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private Integer totalSessions;
    private Integer remainingSessions;
    private int deliveredSessions;
    private long pricePaise;
    private long discountPaise;
    private String sacCode;
    private String invoiceId;
    private CommissionRule resolvedRuleSnapshot;
    private long totalPackageCommissionPaise;
    private String status = STATUS_ACTIVE;
    private Instant createdAt;
    private Instant updatedAt;

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

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getPtPackageId() {
        return ptPackageId;
    }

    public void setPtPackageId(String ptPackageId) {
        this.ptPackageId = ptPackageId;
    }

    public String getPtPackageName() {
        return ptPackageName;
    }

    public void setPtPackageName(String ptPackageName) {
        this.ptPackageName = ptPackageName;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Integer getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(Integer totalSessions) {
        this.totalSessions = totalSessions;
    }

    public Integer getRemainingSessions() {
        return remainingSessions;
    }

    public void setRemainingSessions(Integer remainingSessions) {
        this.remainingSessions = remainingSessions;
    }

    public int getDeliveredSessions() {
        return deliveredSessions;
    }

    public void setDeliveredSessions(int deliveredSessions) {
        this.deliveredSessions = deliveredSessions;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(long pricePaise) {
        this.pricePaise = pricePaise;
    }

    public long getDiscountPaise() {
        return discountPaise;
    }

    public void setDiscountPaise(long discountPaise) {
        this.discountPaise = discountPaise;
    }

    public String getSacCode() {
        return sacCode;
    }

    public void setSacCode(String sacCode) {
        this.sacCode = sacCode;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public CommissionRule getResolvedRuleSnapshot() {
        return resolvedRuleSnapshot;
    }

    public void setResolvedRuleSnapshot(CommissionRule resolvedRuleSnapshot) {
        this.resolvedRuleSnapshot = resolvedRuleSnapshot;
    }

    public long getTotalPackageCommissionPaise() {
        return totalPackageCommissionPaise;
    }

    public void setTotalPackageCommissionPaise(long totalPackageCommissionPaise) {
        this.totalPackageCommissionPaise = totalPackageCommissionPaise;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
