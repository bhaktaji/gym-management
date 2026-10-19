package com.gymkhata.billing.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock provider for tests. Does not build real payment links.
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "mock")
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String buildPaymentLink(String vpa, String payeeName, long amountPaise, String note) {
        return "mock://pay?pa=" + vpa + "&am=" + amountPaise;
    }

    @Override
    public String name() {
        return "mock";
    }
}
