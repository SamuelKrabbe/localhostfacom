package com.example.localhostfacom.payment;

public record WebhookNotification(String eventId, String providerPaymentId, PaymentStatus status) {}
