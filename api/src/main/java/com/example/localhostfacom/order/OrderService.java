package com.example.localhostfacom.order;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.config.AppProperties;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.payment.ChargeRequest;
import com.example.localhostfacom.payment.PaymentCharge;
import com.example.localhostfacom.payment.PaymentProviderRegistry;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

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
}
