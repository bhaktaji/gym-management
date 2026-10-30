package com.gymkhata.membership.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A membership enrolment: member + plan -> dated membership with snapshotted price,
 * optional joining fee, discount, freeze/pause tracking, and optional referral trainer.
 */
@Document("memberships")
public class Membership {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    private String id;
    private String gymId;
    private String memberId;
    private String planId;
    private String planName;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private long pricePaise;
    private long joiningFeePaise;
    private long discountPaise;
    private String status;
    private LocalDate frozenAt;
    private int totalFrozenDays;
    private String freezeReason;
    private String referralTrainerId;
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

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
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

    public long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(long pricePaise) {
        this.pricePaise = pricePaise;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getFrozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(LocalDate frozenAt) {
        this.frozenAt = frozenAt;
    }

    public int getTotalFrozenDays() {
        return totalFrozenDays;
    }

    public void setTotalFrozenDays(int totalFrozenDays) {
        this.totalFrozenDays = totalFrozenDays;
    }

    public String getFreezeReason() {
        return freezeReason;
    }

    public void setFreezeReason(String freezeReason) {
        this.freezeReason = freezeReason;
    }

    public String getReferralTrainerId() {
        return referralTrainerId;
    }

    public void setReferralTrainerId(String referralTrainerId) {
        this.referralTrainerId = referralTrainerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
