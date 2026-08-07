package com.example.localhostfacom.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    // Assigned by the database identity column and read back after insert.
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "payment_provider", nullable = false)
    private String paymentProvider;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "payment_payload")
    private String paymentPayload;

    @Column(name = "payment_qr_base64")
    private String paymentQrBase64;

    @Column(name = "payment_checkout_url")
    private String paymentCheckoutUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_manually_by")
    private UUID paidManuallyBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public static Order create(String paymentProvider, Instant expiresAt) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.status = OrderStatus.PENDING;
        order.total = BigDecimal.ZERO;
        order.paymentProvider = paymentProvider;
        order.createdAt = Instant.now();
        order.expiresAt = expiresAt;
        return order;
    }

    public void addItem(OrderItem item) {
        item.assignTo(this);
        items.add(item);
    }

    public void recalculateTotal() {
        this.total = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    public void attachCharge(String providerPaymentId, String payload, String qrBase64, String checkoutUrl) {
        this.providerPaymentId = providerPaymentId;
        this.paymentPayload = payload;
        this.paymentQrBase64 = qrBase64;
        this.paymentCheckoutUrl = checkoutUrl;
    }

    public void markPaid(Instant paidAt, UUID manuallyBy) {
        this.status = OrderStatus.PAID;
        this.paidAt = paidAt;
        this.paidManuallyBy = manuallyBy;
    }

    public void markExpired() {
        this.status = OrderStatus.EXPIRED;
    }

    public void markCanceled() {
        this.status = OrderStatus.CANCELED;
    }

    public boolean hasCharge() {
        return providerPaymentId != null;
    }

    public UUID getId() { return id; }
    public Long getSeq() { return seq; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public String getPaymentProvider() { return paymentProvider; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getPaymentPayload() { return paymentPayload; }
    public String getPaymentQrBase64() { return paymentQrBase64; }
    public String getPaymentCheckoutUrl() { return paymentCheckoutUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public UUID getPaidManuallyBy() { return paidManuallyBy; }
    public List<OrderItem> getItems() { return items; }
}
