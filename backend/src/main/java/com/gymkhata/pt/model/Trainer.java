package com.gymkhata.pt.model;

import com.gymkhata.domain.CommissionRule;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A trainer record in a gym. Trainers have NO login accounts (preserving the
 * strict 1-owner-per-gym rule). Each trainer can have an optional default
 * commission rule.
 */
@Document("trainers")
public class Trainer {

    @Id
    private String id;
    private String gymId;
    private String name;
    private String phone;
    private boolean active = true;
    private CommissionRule defaultCommissionRule;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public CommissionRule getDefaultCommissionRule() {
        return defaultCommissionRule;
    }

    public void setDefaultCommissionRule(CommissionRule defaultCommissionRule) {
        this.defaultCommissionRule = defaultCommissionRule;
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
