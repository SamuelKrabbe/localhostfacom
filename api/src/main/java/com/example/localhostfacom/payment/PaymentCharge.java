package com.example.localhostfacom.payment;

import java.time.Instant;

/**
 * Deliberately not PIX-specific. {@code payload} is whatever the customer copies — an EMV
 * string today, something else for a future provider — and {@code checkoutUrl} covers
 * providers that redirect instead.
 */
public record PaymentCharge(
        String providerPaymentId,
        String payload,
        String qrImageBase64,
        String checkoutUrl,
        Instant expiresAt) {}
