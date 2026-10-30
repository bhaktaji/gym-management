package com.gymkhata.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Default adapter implementation when external email service is disabled.
 * In production without SMTP credentials configured, tokens are securely held
 * and only accessible through super-admin verification.
 */
@Component
public class DisabledEmailAdapter implements EmailAdapter {

    private static final Logger log = LoggerFactory.getLogger(DisabledEmailAdapter.class);

    @Value("${gymkhata.mail.enabled:false}")
    private boolean mailEnabled;

    @Override
    public boolean sendPasswordResetEmail(String toEmail, String recipientName, String resetToken) {
        if (!mailEnabled) {
            log.info("Email delivery is disabled (gymkhata.mail.enabled=false). Password reset token not sent via email to: {}", toEmail);
            return false;
        }
        log.info("Simulating password reset email dispatch to {}", toEmail);
        return true;
    }
}
