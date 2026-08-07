package com.example.localhostfacom.order;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.config.AppProperties;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.payment.ChargeRequest;
import com.example.localhostfacom.payment.PaymentCharge;
import com.example.localhostfacom.payment.PaymentProviderRegistry;
import com.example.localhostfacom.payment.PaymentStatus;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** How long after creation a non-paid order is still worth re-checking. */
    private static final Duration RECONCILE_WINDOW = Duration.ofHours(24);

    private final OrderRepository orders;
    private final ProductService products;
    private final PaymentProviderRegistry providers;
    private final Duration orderTtl;

    public OrderService(OrderRepository orders, ProductService products,
                        PaymentProviderRegistry providers, AppProperties properties) {
        this.orders = orders;
        this.products = products;
        this.providers = providers;
        this.orderTtl = properties.payments().orderTtl();
    }

    /**
     * Persists and commits the order before any provider call. Holding a database
     * transaction open across an external HTTP request would be bad enough; worse, a
     * failure after the provider had created the charge would roll the order away while
     * a real payable charge existed in the wild.
     */
    @Transactional
    public Order create(List<CreateOrderRequest.Item> items) {
        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest("empty-cart", "The cart is empty");
        }

        Order order = Order.create(providers.active().name(), Instant.now().plus(orderTtl));

        for (CreateOrderRequest.Item item : items) {
            Product product = products.requireActive(item.productId());
            order.addItem(OrderItem.snapshotOf(product, item.quantity()));
        }

        order.recalculateTotal();
        return orders.save(order);
    }

    /**
     * Creates the charge for an already committed order, or returns the existing one.
     * Idempotent, so retrying after a provider failure never produces a second charge
     * the customer could pay twice.
     */
    @Transactional
    public Order ensureCharge(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("order-not-found", "Order not found"));

        if (order.hasCharge()) {
            return order;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw ApiException.conflict("order-not-pending",
                    "This order is no longer awaiting payment");
        }

        PaymentCharge charge = providers.byName(order.getPaymentProvider())
                .createCharge(new ChargeRequest(
                        order.getId(),
                        order.getTotal(),
                        "Sala de Estudos",
                        order.getExpiresAt()));

        order.attachCharge(
                charge.providerPaymentId(), charge.payload(), charge.qrImageBase64(), charge.checkoutUrl());
        return orders.save(order);
    }

    public Order require(UUID orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("order-not-found", "Order not found"));
    }

    /**
     * The single point where an order becomes PAID, whatever confirmed it — webhook,
     * reconciler or an admin. Takes a row lock and checks the current status, so two
     * paths arriving at once credit the order exactly once.
     *
     * @return true when this call performed the transition, false when it was already paid
     */
    @Transactional
    public boolean markPaid(UUID orderId, UUID manuallyBy) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("order-not-found", "Order not found"));

        if (!order.getStatus().canTransitionToPaid()) {
            return false;
        }

        // EXPIRED and CANCELED are local states meaning "stopped waiting", not "refused
        // the money". A late payment is still a payment and must reach the ledger.
        order.markPaid(Instant.now(), manuallyBy);
        orders.save(order);
        return true;
    }

    @Transactional
    public void applyProviderStatus(UUID orderId, PaymentStatus status) {
        switch (status) {
            case APPROVED -> markPaid(orderId, null);
            case REJECTED, EXPIRED -> expireIfStillPending(orderId);
            case PENDING -> { /* nothing to do; keep waiting */ }
        }
    }

    @Transactional
    public void cancel(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("order-not-found", "Order not found"));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw ApiException.conflict("order-not-pending", "Only a pending order can be canceled");
        }

        order.markCanceled();
        orders.save(order);
    }

    /** Asks the originating provider for the current status and applies it. */
    @Transactional
    public Order syncWithProvider(UUID orderId) {
        Order order = require(orderId);

        if (!order.hasCharge()) {
            throw ApiException.conflict("order-has-no-charge",
                    "This order has no payment charge yet; create one first");
        }

        PaymentStatus status = providers.byName(order.getPaymentProvider())
                .fetchStatus(order.getProviderPaymentId());
        applyProviderStatus(orderId, status);
        return require(orderId);
    }

    @Transactional
    public void expireOverdueOrders(Instant now) {
        for (Order order : orders.findExpirable(now)) {
            order.markExpired();
            orders.save(order);
        }
    }

    public List<Order> reconcilableOrders() {
        return orders.findReconcilable(Instant.now().minus(RECONCILE_WINDOW));
    }

    public OrderStatus statusOf(UUID orderId) {
        return require(orderId).getStatus();
    }

    private void expireIfStillPending(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order != null && order.getStatus() == OrderStatus.PENDING) {
            order.markExpired();
            orders.save(order);
        }
    }
}
