package com.gymkhata.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock provider for tests: records links instead of sending anything.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "mock")
public class MockMessagingProvider implements MessagingProvider {

    @Override
    public String buildWaLink(String e164Phone, String text) {
        return "mock://wa?to=" + e164Phone;
    }

    @Override
    public String name() {
        return "mock";
    }
}
