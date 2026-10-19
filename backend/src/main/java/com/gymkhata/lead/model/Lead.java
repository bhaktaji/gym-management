package com.gymkhata.lead.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("leads")
public class Lead {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_CONTACTED = "CONTACTED";
    public static final String STATUS_CONVERTED = "CONVERTED";
    public static final String STATUS_LOST = "LOST";

    @Id
    private String id;
    private String gymId;
    private String name;
    private String phone;
    private String source;
    private String status;
    private String notes;
    private String convertedToMemberId;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getConvertedToMemberId() {
        return convertedToMemberId;
    }

    public void setConvertedToMemberId(String convertedToMemberId) {
        this.convertedToMemberId = convertedToMemberId;
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
