package com.example.localhostfacom.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_event")
public class WebhookEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_event_id")
    private String providerEventId;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(nullable = false)
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    private String error;

    protected WebhookEvent() {}

    public static WebhookEvent received(String provider, String eventId, String paymentId, String payload) {
        WebhookEvent event = new WebhookEvent();
        event.id = UUID.randomUUID();
        event.provider = provider;
        event.providerEventId = eventId;
        event.providerPaymentId = paymentId;
        event.payload = payload;
        event.receivedAt = Instant.now();
        return event;
    }

    public void markProcessed() {
        this.processedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.error = error != null && error.length() > 1024 ? error.substring(0, 1024) : error;
    }

    public UUID getId() { return id; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public Instant getProcessedAt() { return processedAt; }
    public String getError() { return error; }
}
