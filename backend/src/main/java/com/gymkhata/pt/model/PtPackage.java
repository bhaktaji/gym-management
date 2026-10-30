package com.gymkhata.pt.model;

import com.gymkhata.domain.CommissionRule;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Owner-defined Personal Training package:
 * - TIME_BASED (monthly 30d / quarterly 90d / custom validityDays, totalSessions == null)
 * - SESSION_BASED (totalSessions > 0 + validityDays)
 * Price is GST-inclusive in integer paise. Uses the gym's GST settings with a configurable SAC.
 */
@Document("pt_packages")
public class PtPackage {

    public static final String TYPE_TIME_BASED = "TIME_BASED";
    public static final String TYPE_SESSION_BASED = "SESSION_BASED";

    @Id
    private String id;
    private String gymId;
    private String name;
    private String packageType = TYPE_SESSION_BASED; // TIME_BASED or SESSION_BASED
    private int validityDays;
    private Integer totalSessions;
    private long pricePaise;
    private String sacCode = "999723";
    private CommissionRule commissionRuleOverride;
    private boolean active = true;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public int getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(int validityDays) {
        this.validityDays = validityDays;
    }

    public Integer getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(Integer totalSessions) {
        this.totalSessions = totalSessions;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(long pricePaise) {
        this.pricePaise = pricePaise;
    }

    public String getSacCode() {
        return sacCode;
    }

    public void setSacCode(String sacCode) {
        this.sacCode = sacCode;
    }

    public CommissionRule getCommissionRuleOverride() {
        return commissionRuleOverride;
    }

    public void setCommissionRuleOverride(CommissionRule commissionRuleOverride) {
        this.commissionRuleOverride = commissionRuleOverride;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
