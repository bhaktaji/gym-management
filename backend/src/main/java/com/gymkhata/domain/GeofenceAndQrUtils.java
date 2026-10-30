package com.gymkhata.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Pure domain utilities for soft geofencing (Haversine distance) and
 * 60-second rotating QR tokens for strict self-check-in mode.
 */
public final class GeofenceAndQrUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    public static final long ROTATION_WINDOW_SECONDS = 60L;

    private GeofenceAndQrUtils() {
    }

    /**
     * Computes Haversine distance in meters between two WGS84 coordinates.
     */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Evaluates soft geofence flag. Never blocks check-in; returns null if geofence or caller coords are absent.
     */
    public static Boolean evaluateSoftGeofence(Double memberLat, Double memberLng,
                                               Double gymLat, Double gymLng, Integer radiusMeters) {
        if (memberLat == null || memberLng == null || gymLat == null || gymLng == null
                || radiusMeters == null || radiusMeters <= 0) {
            return null;
        }
        return distanceMeters(memberLat, memberLng, gymLat, gymLng) <= radiusMeters;
    }

    /**
     * Generates a 10-character uppercase hex token for the 60-second time bucket at epochSecond.
     */
    public static String generateRotatingToken(String secret, long epochSecond) {
        long bucket = epochSecond / ROTATION_WINDOW_SECONDS;
        return computeBucketToken(secret == null || secret.isBlank() ? "gymkhata-default-qr-secret" : secret, bucket);
    }

    /**
     * Validates a rotating token against the current 60s bucket (and optional 1-bucket grace for boundary transition).
     */
    public static boolean isValidRotatingToken(String secret, String token, long nowEpochSecond) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String normalized = token.trim().toUpperCase();
        String effSecret = (secret == null || secret.isBlank()) ? "gymkhata-default-qr-secret" : secret;
        long currentBucket = nowEpochSecond / ROTATION_WINDOW_SECONDS;
        String expectedCurrent = computeBucketToken(effSecret, currentBucket);
        if (MessageDigest.isEqual(
                normalized.getBytes(StandardCharsets.UTF_8),
                expectedCurrent.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        return false;
    }

    private static String computeBucketToken(String secret, long bucket) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(("QR:" + bucket).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(10);
            for (int i = 0; i < 5; i++) {
                sb.append(String.format("%02X", raw[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
