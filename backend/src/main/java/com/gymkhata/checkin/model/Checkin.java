package com.gymkhata.checkin.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Document("checkins")
public class Checkin {

    public static final String METHOD_QR_SELF = "QR_SELF";
    public static final String METHOD_MANUAL = "MANUAL";

    @Id
    private String id;
    private String gymId;
    private String memberId;
    private String memberName;
    private Instant checkInAt;
    private Instant checkOutAt;
    private LocalDate date;
    private String method = METHOD_MANUAL;
    private Double latitude;
    private Double longitude;
    private Boolean withinGeofence;
    private boolean autoClosed;
    private String membershipBadge; // GREEN, AMBER, RED

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

    public Instant getCheckInAt() {
        return checkInAt;
    }

    public void setCheckInAt(Instant checkInAt) {
        this.checkInAt = checkInAt;
    }

    public Instant getCheckOutAt() {
        return checkOutAt;
    }

    public void setCheckOutAt(Instant checkOutAt) {
        this.checkOutAt = checkOutAt;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Boolean getWithinGeofence() {
        return withinGeofence;
    }

    public void setWithinGeofence(Boolean withinGeofence) {
        this.withinGeofence = withinGeofence;
    }

    public boolean isAutoClosed() {
        return autoClosed;
    }

    public void setAutoClosed(boolean autoClosed) {
        this.autoClosed = autoClosed;
    }

    public String getMembershipBadge() {
        return membershipBadge;
    }

    public void setMembershipBadge(String membershipBadge) {
        this.membershipBadge = membershipBadge;
    }
}
