package com.example.localhostfacom.order;

import com.example.localhostfacom.payment.PaymentProviderRegistry;
import com.example.localhostfacom.payment.PaymentStatus;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The webhook is the fast path, not the only one. This sweep asks each order's
 * originating provider what actually happened, so a missed, delayed or misconfigured
 * callback cannot leave a paid order stuck as pending.
 */
@Component
public class OrderReconciler {

    private static final Logger log = LoggerFactory.getLogger(OrderReconciler.class);

    private final OrderService orders;
    private final PaymentProviderRegistry providers;

    public OrderReconciler(OrderService orders, PaymentProviderRegistry providers) {
        this.orders = orders;
        this.providers = providers;
    }

    @Scheduled(fixedDelayString = "PT60S")
    public void reconcile() {
        try {
            orders.expireOverdueOrders(Instant.now());
        } catch (RuntimeException exception) {
            log.error("Failed to expire overdue orders", exception);
        }

        for (Order order : orders.reconcilableOrders()) {
            try {
                // Resolved from the order, not from current configuration, so switching
                // providers never strands orders created under the previous one.
                PaymentStatus status = providers.byName(order.getPaymentProvider())
                        .fetchStatus(order.getProviderPaymentId());
                orders.applyProviderStatus(order.getId(), status);
            } catch (RuntimeException exception) {
                // One bad order must not abort the sweep, and an exception escaping a
                // scheduled method stops it being rescheduled at all.
                log.warn("Could not reconcile order {}", order.getId(), exception);
            }
        }
    }
}
