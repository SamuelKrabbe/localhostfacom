package com.example.localhostfacom.payment;

import java.util.Map;
import java.util.Optional;

public interface PaymentProvider {

    /** Stable identifier persisted on the order and used in the webhook route. */
    String name();

    PaymentCharge createCharge(ChargeRequest request);

    PaymentStatus fetchStatus(String providerPaymentId);

    /**
     * Returns empty when the request is not authentic. Callers must treat empty as a
     * hard rejection and write nothing.
     */
    Optional<WebhookNotification> parseAndVerify(Map<String, String> headers, String rawBody);
}
