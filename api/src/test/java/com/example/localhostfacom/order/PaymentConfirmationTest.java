package com.example.localhostfacom.order;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.payment.PaymentStatus;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import com.example.localhostfacom.product.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PaymentConfirmationTest {

    @Autowired private OrderService service;
    @Autowired private OrderRepository orders;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;
    @Autowired private AdminRepository admins;

    private Product coffee;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
        admins.deleteAll();
        coffee = productService.create("Café", new BigDecimal("3.50"), null);
    }

    private Order newOrder() {
        return service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));
    }

    @Test
    void marksAPendingOrderAsPaid() {
        Order order = newOrder();

        assertThat(service.markPaid(order.getId(), null)).isTrue();

        Order reloaded = orders.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloaded.getPaidAt()).isNotNull();
    }

    /** Two confirmation paths can land at once; the order must be credited exactly once. */
    @Test
    void isIdempotentAcrossRepeatedConfirmations() {
        Order order = newOrder();

        assertThat(service.markPaid(order.getId(), null)).isTrue();
        assertThat(service.markPaid(order.getId(), null)).isFalse();

        Order reloaded = orders.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    /**
     * The single most important behaviour in the system. Money that arrives after the
     * local deadline is still money, and leaving it out of the ledger would make the
     * public totals understate what was collected.
     */
    @Test
    void creditsAnExpiredOrderWhenThePaymentArrivesLate() {
        Order order = newOrder();
        service.expireOverdueOrders(order.getExpiresAt().plusSeconds(1));
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.EXPIRED);

        assertThat(service.markPaid(order.getId(), null)).isTrue();

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void creditsACanceledOrderWhenThePaymentArrivesAnyway() {
        Order order = newOrder();
        service.cancel(order.getId());

        assertThat(service.markPaid(order.getId(), null)).isTrue();

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void recordsWhichAdminConfirmedAPaymentByHand() {
        Order order = newOrder();
        UUID adminId = admins.save(Admin.create("owner@example.com", "hash")).getId();

        service.markPaid(order.getId(), adminId);

        assertThat(orders.findById(order.getId()).orElseThrow().getPaidManuallyBy()).isEqualTo(adminId);
    }

    @Test
    void appliesAnApprovedProviderStatus() {
        Order order = newOrder();

        service.applyProviderStatus(order.getId(), PaymentStatus.APPROVED);

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void leavesTheOrderPendingForAPendingProviderStatus() {
        Order order = newOrder();

        service.applyProviderStatus(order.getId(), PaymentStatus.PENDING);

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void neverUnpaysAnAlreadyPaidOrder() {
        Order order = newOrder();
        service.markPaid(order.getId(), null);

        service.applyProviderStatus(order.getId(), PaymentStatus.EXPIRED);

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void leavesAnOrderAloneUntilItsDeadlineHasActuallyPassed() {
        Order order = newOrder();

        service.expireOverdueOrders(order.getExpiresAt().minusSeconds(1));

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void expiresAnOrderOnceItsDeadlineHasPassed() {
        Order order = newOrder();

        service.expireOverdueOrders(order.getExpiresAt().plusSeconds(1));

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.EXPIRED);
    }

    /** Sweeping a paid order into EXPIRED would erase a real receipt. */
    @Test
    void neverExpiresAnOrderThatWasAlreadyPaid() {
        Order order = newOrder();
        service.markPaid(order.getId(), null);

        service.expireOverdueOrders(order.getExpiresAt().plusSeconds(1));

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }
}
