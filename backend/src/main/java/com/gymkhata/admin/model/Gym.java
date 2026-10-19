package com.gymkhata.admin.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * A gym tenant. Each gym has exactly one GYM_OWNER user account.
 */
@Document("gyms")
public class Gym {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    @Id
    private String id;
    private String name;
    private String code;
    private String ownerName;
    private String phone;
    private String email;
    private String address;
    private String city;
    private String state;
    private String gstin;
    private boolean gstRegistered;
    private String invoicePrefix;
    private String status;
    private LocalDate subscriptionValidUntil;
    private long planPricePaise;
    private Settings settings;
    private Instant createdAt;
    private Instant updatedAt;

    public Gym() {
        this.settings = new Settings();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public boolean isGstRegistered() {
        return gstRegistered;
    }

    public void setGstRegistered(boolean gstRegistered) {
        this.gstRegistered = gstRegistered;
    }

    public String getInvoicePrefix() {
        return invoicePrefix;
    }

    public void setInvoicePrefix(String invoicePrefix) {
        this.invoicePrefix = invoicePrefix;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getSubscriptionValidUntil() {
        return subscriptionValidUntil;
    }

    public void setSubscriptionValidUntil(LocalDate subscriptionValidUntil) {
        this.subscriptionValidUntil = subscriptionValidUntil;
    }

    public long getPlanPricePaise() {
        return planPricePaise;
    }

    public void setPlanPricePaise(long planPricePaise) {
        this.planPricePaise = planPricePaise;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
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

    /**
     * Per-gym editable settings: UPI credentials, WhatsApp templates (en+hi),
     * quiet-hours flag.
     */
    public static class Settings {
        private String upiVpa;
        private String upiName;
        private boolean quietHoursEnabled = true;
        private Map<String, String> templatesEn = Map.of();
        private Map<String, String> templatesHi = Map.of();

        public String getUpiVpa() {
            return upiVpa;
        }

        public void setUpiVpa(String upiVpa) {
            this.upiVpa = upiVpa;
        }

        public String getUpiName() {
            return upiName;
        }

        public void setUpiName(String upiName) {
            this.upiName = upiName;
        }

        public boolean isQuietHoursEnabled() {
            return quietHoursEnabled;
        }

        public void setQuietHoursEnabled(boolean quietHoursEnabled) {
            this.quietHoursEnabled = quietHoursEnabled;
        }

        public Map<String, String> getTemplatesEn() {
            return templatesEn;
        }

        public void setTemplatesEn(Map<String, String> templatesEn) {
            this.templatesEn = templatesEn;
        }

        public Map<String, String> getTemplatesHi() {
            return templatesHi;
        }

        public void setTemplatesHi(Map<String, String> templatesHi) {
            this.templatesHi = templatesHi;
        }
    }
}
