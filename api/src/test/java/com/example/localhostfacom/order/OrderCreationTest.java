package com.example.localhostfacom.order;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrderCreationTest {

    @Autowired private OrderService service;
    @Autowired private OrderRepository orders;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;

    private Product coffee;
    private Product cake;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
        coffee = productService.create("Café", new BigDecimal("3.50"), null);
        cake = productService.create("Bolo", new BigDecimal("5.25"), null);
    }

    @Test
    void computesTheTotalFromDatabasePrices() {
        Order order = service.create(List.of(
                new CreateOrderRequest.Item(coffee.getId(), 2),
                new CreateOrderRequest.Item(cake.getId(), 1)));

        assertThat(order.getTotal()).isEqualByComparingTo("12.25");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    /** The request carries no prices at all, so a manipulated client cannot set its own. */
    @Test
    void snapshotsNameAndPriceOntoEachItem() {
        Order order = service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));

        OrderItem item = order.getItems().getFirst();
        assertThat(item.getProductName()).isEqualTo("Café");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("3.50");
    }

    @Test
    void refusesAnEmptyCart() {
        assertThatThrownBy(() -> service.create(List.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void refusesAnInactiveProduct() {
        productService.update(coffee.getId(), "Café", new BigDecimal("3.50"), null, false);

        assertThatThrownBy(() -> service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void refusesAnUnknownProduct() {
        assertThatThrownBy(() -> service.create(List.of(new CreateOrderRequest.Item(UUID.randomUUID(), 1))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void createsTheOrderWithoutAChargeAttached() {
        Order order = service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));

        assertThat(order.hasCharge()).isFalse();
        assertThat(orders.findById(order.getId())).isPresent();
    }

    @Test
    void attachesAChargeOnDemand() {
        Order order = service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));

        Order charged = service.ensureCharge(order.getId());

        assertThat(charged.hasCharge()).isTrue();
        assertThat(charged.getPaymentPayload()).isNotBlank();
        assertThat(charged.getPaymentQrBase64()).isNotBlank();
        assertThat(charged.getPaymentProvider()).isEqualTo("fake");
    }

    /** Retrying after a provider failure must not create a second payable charge. */
    @Test
    void reusesTheExistingChargeWhenAskedTwice() {
        Order order = service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));

        String first = service.ensureCharge(order.getId()).getProviderPaymentId();
        String second = service.ensureCharge(order.getId()).getProviderPaymentId();

        assertThat(second).isEqualTo(first);
    }
}
