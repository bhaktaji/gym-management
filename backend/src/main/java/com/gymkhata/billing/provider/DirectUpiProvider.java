package com.gymkhata.billing.provider;

import com.gymkhata.domain.UpiUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default provider: builds a standard upi://pay deep link for the gym's own VPA.
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "directupi", matchIfMissing = true)
public class DirectUpiProvider implements PaymentProvider {

    @Override
    public String buildPaymentLink(String vpa, String payeeName, long amountPaise, String note) {
        if (!UpiUtils.isValidVpa(vpa)) {
            return null;
        }
        return UpiUtils.buildDeepLink(vpa, payeeName, amountPaise, note);
    }

    @Override
    public String name() {
        return "directupi";
    }
}
