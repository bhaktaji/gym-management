package com.gymkhata.messaging;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Default provider: builds https://wa.me/91XXXXXXXXXX?text=... links.
 * The Gym Owner taps, sends in WhatsApp, and marks the outbox entry sent.
 */
@Component
public class ClickToChatProvider implements MessagingProvider {

    @Override
    public String buildWaLink(String e164Phone, String text) {
        String digits = e164Phone.startsWith("+") ? e164Phone.substring(1) : e164Phone;
        return "https://wa.me/" + digits + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    @Override
    public String name() {
        return "clicktochat";
    }
}
