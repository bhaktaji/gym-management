package com.gymkhata.admin.model;

import com.gymkhata.domain.CommissionRule;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
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
    private String slug;
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
        if (this.slug == null && code != null) {
            this.slug = code.toLowerCase(Locale.ROOT);
        }
    }

    public String getSlug() {
        if (slug != null && !slug.isBlank()) {
            return slug;
        }
        if (code != null && !code.isBlank()) {
            return code.toLowerCase(Locale.ROOT);
        }
        return id;
    }

    public void setSlug(String slug) {
        this.slug = slug == null ? null : slug.toLowerCase(Locale.ROOT).trim();
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
        if (settings == null) {
            settings = new Settings();
        }
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
     * quiet-hours flag, attendance settings (default OFF), late-renewal policy,
     * and PT/commission settings.
     */
    public static class Settings {
        public static final String RENEWAL_FROM_TODAY = "FROM_TODAY";
        public static final String RENEWAL_FROM_EXPIRY = "FROM_EXPIRY";

        public static final String TRIGGER_ON_PAYMENT = "ON_PAYMENT";
        public static final String TRIGGER_ON_SESSION = "ON_SESSION_DELIVERED";

        private String upiVpa;
        private String upiName;
        private boolean quietHoursEnabled = true;
        private Map<String, String> templatesEn = Map.of();
        private Map<String, String> templatesHi = Map.of();

        // Attendance settings (optional, default OFF)
        private boolean attendanceEnabled = false;
        private boolean strictQrMode = false;
        private String rotatingTokenSecret;
        private int minCheckinGapMinutes = 0;
        private int autoCloseDurationMinutes = 120;
        private String closingTimeIst = "22:00";
        private boolean selfCheckoutEnabled = false;
        private Double geofenceLat;
        private Double geofenceLng;
        private Integer geofenceRadiusMeters;
        private int notSeenDaysThreshold = 14;

        // Membership & Renewal settings
        private String lateRenewalPolicy = RENEWAL_FROM_TODAY;

        // Personal Training & Commission settings
        private String ptSacCode = "999723";
        private String commissionTrigger = TRIGGER_ON_PAYMENT;
        private boolean membershipReferralCommissionEnabled = false;
        private CommissionRule defaultCommissionRule;

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

        public boolean isAttendanceEnabled() {
            return attendanceEnabled;
        }

        public void setAttendanceEnabled(boolean attendanceEnabled) {
            this.attendanceEnabled = attendanceEnabled;
        }

        public boolean isStrictQrMode() {
            return strictQrMode;
        }

        public void setStrictQrMode(boolean strictQrMode) {
            this.strictQrMode = strictQrMode;
        }

        public String getRotatingTokenSecret() {
            return rotatingTokenSecret;
        }

        public void setRotatingTokenSecret(String rotatingTokenSecret) {
            this.rotatingTokenSecret = rotatingTokenSecret;
        }

        public int getMinCheckinGapMinutes() {
            return minCheckinGapMinutes;
        }

        public void setMinCheckinGapMinutes(int minCheckinGapMinutes) {
            this.minCheckinGapMinutes = minCheckinGapMinutes;
        }

        public int getAutoCloseDurationMinutes() {
            return autoCloseDurationMinutes <= 0 ? 120 : autoCloseDurationMinutes;
        }

        public void setAutoCloseDurationMinutes(int autoCloseDurationMinutes) {
            this.autoCloseDurationMinutes = autoCloseDurationMinutes;
        }

        public String getClosingTimeIst() {
            return closingTimeIst;
        }

        public void setClosingTimeIst(String closingTimeIst) {
            this.closingTimeIst = closingTimeIst;
        }

        public boolean isSelfCheckoutEnabled() {
            return selfCheckoutEnabled;
        }

        public void setSelfCheckoutEnabled(boolean selfCheckoutEnabled) {
            this.selfCheckoutEnabled = selfCheckoutEnabled;
        }

        public Double getGeofenceLat() {
            return geofenceLat;
        }

        public void setGeofenceLat(Double geofenceLat) {
            this.geofenceLat = geofenceLat;
        }

        public Double getGeofenceLng() {
            return geofenceLng;
        }

        public void setGeofenceLng(Double geofenceLng) {
            this.geofenceLng = geofenceLng;
        }

        public Integer getGeofenceRadiusMeters() {
            return geofenceRadiusMeters;
        }

        public void setGeofenceRadiusMeters(Integer geofenceRadiusMeters) {
            this.geofenceRadiusMeters = geofenceRadiusMeters;
        }

        public int getNotSeenDaysThreshold() {
            return notSeenDaysThreshold <= 0 ? 14 : notSeenDaysThreshold;
        }

        public void setNotSeenDaysThreshold(int notSeenDaysThreshold) {
            this.notSeenDaysThreshold = notSeenDaysThreshold;
        }

        public String getLateRenewalPolicy() {
            return lateRenewalPolicy == null ? RENEWAL_FROM_TODAY : lateRenewalPolicy;
        }

        public void setLateRenewalPolicy(String lateRenewalPolicy) {
            this.lateRenewalPolicy = lateRenewalPolicy;
        }

        public String getPtSacCode() {
            return ptSacCode == null || ptSacCode.isBlank() ? "999723" : ptSacCode;
        }

        public void setPtSacCode(String ptSacCode) {
            this.ptSacCode = ptSacCode;
        }

        public String getCommissionTrigger() {
            return commissionTrigger == null || commissionTrigger.isBlank() ? TRIGGER_ON_PAYMENT : commissionTrigger;
        }

        public void setCommissionTrigger(String commissionTrigger) {
            this.commissionTrigger = commissionTrigger;
        }

        public boolean isMembershipReferralCommissionEnabled() {
            return membershipReferralCommissionEnabled;
        }

        public void setMembershipReferralCommissionEnabled(boolean membershipReferralCommissionEnabled) {
            this.membershipReferralCommissionEnabled = membershipReferralCommissionEnabled;
        }

        public CommissionRule getDefaultCommissionRule() {
            return defaultCommissionRule;
        }

        public void setDefaultCommissionRule(CommissionRule defaultCommissionRule) {
            this.defaultCommissionRule = defaultCommissionRule;
        }
    }
}
