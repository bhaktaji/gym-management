package com.gymkhata.messaging;

/**
 * Messaging provider adapter. ClickToChat is the default (wa.me deep links);
 * WhatsAppCloud implements the same interface behind env flags; Mock is for tests.
 */
public interface MessagingProvider {

    /** One-tap link the owner clicks to open WhatsApp with the message pre-filled. */
    String buildWaLink(String e164Phone, String text);

    /** Provider name. */
    String name();
}
