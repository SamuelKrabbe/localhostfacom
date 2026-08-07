package com.example.localhostfacom.order;

import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.payment.PaymentProviderRegistry;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import com.example.localhostfacom.product.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.payments.fake.auto-confirm-after=PT0S")
class OrderReconcilerTest {

    @Autowired private OrderReconciler reconciler;
    @Autowired private OrderService service;
    @Autowired private OrderRepository orders;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;
    @Autowired private PaymentProviderRegistry providers;

    private Product coffee;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
        coffee = productService.create("Café", new BigDecimal("3.50"), null);
    }

    private Order chargedOrder() {
        Order order = service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));
        service.ensureCharge(order.getId());
        return orders.findById(order.getId()).orElseThrow();
    }

    @Test
    void confirmsAPendingOrderTheWebhookNeverReported() {
        Order order = chargedOrder();

        reconciler.reconcile();

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void confirmsAnExpiredOrderTheProviderLaterApproves() {
        Order order = chargedOrder();
        service.expireOverdueOrders(order.getExpiresAt().plusSeconds(1));
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.EXPIRED);

        reconciler.reconcile();

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void skipsAnOrderThatNeverGotACharge() {
        Order order = service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));

        reconciler.reconcile();

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void survivesAProviderThatThrows() {
        chargedOrder();

        assertThatCode(() -> reconciler.reconcile()).doesNotThrowAnyException();
    }
}
