package com.gymkhata.common.notification;

public interface EmailAdapter {
    /**
     * Attempts to send a password reset email.
     * Returns true if sent successfully, false otherwise.
     */
    boolean sendPasswordResetEmail(String toEmail, String recipientName, String resetToken);
}
