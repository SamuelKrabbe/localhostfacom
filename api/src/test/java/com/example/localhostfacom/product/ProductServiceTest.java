package com.example.localhostfacom.product;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.order.Order;
import com.example.localhostfacom.order.OrderItem;
import com.example.localhostfacom.order.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProductServiceTest {

    @Autowired private ProductService service;
    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orders;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
    }

    @Test
    void createsAProduct() {
        Product product = service.create("Café", new BigDecimal("3.50"), null);

        assertThat(product.getName()).isEqualTo("Café");
        assertThat(product.isActive()).isTrue();
    }

    @Test
    void listsOnlyActiveProductsForThePublicCatalogue() {
        service.create("Ativo", new BigDecimal("1.00"), null);
        Product hidden = service.create("Escondido", new BigDecimal("1.00"), null);
        service.update(hidden.getId(), "Escondido", new BigDecimal("1.00"), null, false);

        assertThat(service.listActive()).extracting(Product::getName).containsExactly("Ativo");
        assertThat(service.listAll()).hasSize(2);
    }

    /** A product that never sold leaves no history worth keeping. */
    @Test
    void removesAProductThatWasNeverOrdered() {
        Product product = service.create("Nunca vendido", new BigDecimal("1.00"), null);

        service.remove(product.getId());

        assertThat(products.findById(product.getId())).isEmpty();
    }

    /** A product that has sold must survive, or past orders lose their referent. */
    @Test
    void deactivatesRatherThanRemovesAProductThatHasSold() {
        Product product = service.create("Já vendido", new BigDecimal("2.00"), null);
        Order order = Order.create("fake", Instant.now().plusSeconds(600));
        order.addItem(OrderItem.snapshotOf(product, 1));
        order.recalculateTotal();
        orders.saveAndFlush(order);

        service.remove(product.getId());

        Product reloaded = products.findById(product.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    void refusesAnInactiveProductWhenAnOrderAsksForIt() {
        Product product = service.create("Indisponível", new BigDecimal("1.00"), null);
        service.update(product.getId(), "Indisponível", new BigDecimal("1.00"), null, false);

        assertThatThrownBy(() -> service.requireActive(product.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void refusesAnUnknownProduct() {
        assertThatThrownBy(() -> service.requireActive(java.util.UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
    }
}
