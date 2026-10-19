package com.gymkhata.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * WhatsApp Cloud API provider, enabled via app.messaging.provider=whatsappcloud.
 * NOTE: network calls are stubbed behind env flags and NOT PRODUCTION-TESTED.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "whatsappcloud")
public class WhatsAppCloudProvider implements MessagingProvider {

    @Value("${app.messaging.whatsapp-cloud.token:}")
    private String token;

    @Value("${app.messaging.whatsapp-cloud.phone-number-id:}")
    private String phoneNumberId;

    @Override
    public String buildWaLink(String e164Phone, String text) {
        // The Cloud API sends directly; no wa.me link. Return a marker the UI can show.
        return "whatsappcloud://send?to=" + e164Phone + "&text=" + text;
    }

    @Override
    public String name() {
        return "whatsappcloud";
    }
}
