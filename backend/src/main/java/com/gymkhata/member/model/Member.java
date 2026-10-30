package com.gymkhata.member.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A gym member. Phone is unique per gym. WhatsApp opt-in is captured at enrolment.
 * Supports 4-digit hashed PIN and up to 2 bound devices for self-service QR check-in.
 */
@Document("members")
public class Member {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    private String id;
    private String gymId;
    private String name;
    private String phone;
    private String email;
    private String gender;
    private LocalDate dob;
    private String address;
    private String state;
    private String status;
    private boolean whatsappOptIn;
    private Instant whatsappOptInAt;
    private LocalDate joinedAt;
    private String notes;

    @JsonIgnore
    private String pinHash;
    private int pinFailedAttempts;
    private Instant pinLockedUntil;
    private List<BoundDevice> boundDevices = new ArrayList<>();

    @Transient
    private String generatedPin;
    @Transient
    private String welcomeWhatsappText;

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isWhatsappOptIn() {
        return whatsappOptIn;
    }

    public void setWhatsappOptIn(boolean whatsappOptIn) {
        this.whatsappOptIn = whatsappOptIn;
    }

    public Instant getWhatsappOptInAt() {
        return whatsappOptInAt;
    }

    public void setWhatsappOptInAt(Instant whatsappOptInAt) {
        this.whatsappOptInAt = whatsappOptInAt;
    }

    public LocalDate getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDate joinedAt) {
        this.joinedAt = joinedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public int getPinFailedAttempts() {
        return pinFailedAttempts;
    }

    public void setPinFailedAttempts(int pinFailedAttempts) {
        this.pinFailedAttempts = pinFailedAttempts;
    }

    public Instant getPinLockedUntil() {
        return pinLockedUntil;
    }

    public void setPinLockedUntil(Instant pinLockedUntil) {
        this.pinLockedUntil = pinLockedUntil;
    }

    public List<BoundDevice> getBoundDevices() {
        if (boundDevices == null) {
            boundDevices = new ArrayList<>();
        }
        return boundDevices;
    }

    public void setBoundDevices(List<BoundDevice> boundDevices) {
        this.boundDevices = boundDevices;
    }

    public String getGeneratedPin() {
        return generatedPin;
    }

    public void setGeneratedPin(String generatedPin) {
        this.generatedPin = generatedPin;
    }

    public String getWelcomeWhatsappText() {
        return welcomeWhatsappText;
    }

    public void setWelcomeWhatsappText(String welcomeWhatsappText) {
        this.welcomeWhatsappText = welcomeWhatsappText;
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

    public static class BoundDevice {
        private String deviceId;
        @JsonIgnore
        private String tokenHash;
        private String userAgent;
        private Instant boundAt;

        public BoundDevice() {
        }

        public BoundDevice(String deviceId, String tokenHash, String userAgent, Instant boundAt) {
            this.deviceId = deviceId;
            this.tokenHash = tokenHash;
            this.userAgent = userAgent;
            this.boundAt = boundAt;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getTokenHash() {
            return tokenHash;
        }

        public void setTokenHash(String tokenHash) {
            this.tokenHash = tokenHash;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public Instant getBoundAt() {
            return boundAt;
        }

        public void setBoundAt(Instant boundAt) {
            this.boundAt = boundAt;
        }
    }
}
