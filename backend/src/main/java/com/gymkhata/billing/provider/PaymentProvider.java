package com.gymkhata.billing.provider;

/**
 * Payment provider adapter.
 * DirectUpi is the default: the gym collects on its own VPA and the UTR is
 * recorded at the counter (or submitted by the member on the public pay page).
 */
public interface PaymentProvider {

    /** Deep link the member taps to pay the given amount to the gym's VPA. */
    String buildPaymentLink(String vpa, String payeeName, long amountPaise, String note);

    /** Provider name. */
    String name();
}
