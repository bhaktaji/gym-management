package com.gymkhata.billing.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Razorpay adapter, enabled via app.payments.provider=razorpay.
 * NOT PRODUCTION-TESTED: the live API call is stubbed out in this build.
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "razorpay")
public class RazorpayProvider implements PaymentProvider {

    @Value("${app.payments.razorpay.key-id:}")
    private String keyId;

    @Override
    public String buildPaymentLink(String vpa, String payeeName, long amountPaise, String note) {
        // NOT PRODUCTION-TESTED: real Razorpay order creation is not implemented in this build.
        return "razorpay://pay?pa=" + vpa + "&am=" + amountPaise + "&key=" + keyId;
    }

    @Override
    public String name() {
        return "razorpay";
    }
}
