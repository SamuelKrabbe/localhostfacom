package com.example.localhostfacom;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import com.example.localhostfacom.order.Order;
import com.example.localhostfacom.order.OrderItem;
import com.example.localhostfacom.order.OrderRepository;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import com.example.localhostfacom.settings.SettingsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityMappingTest {

    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orders;
    @Autowired private AdminRepository admins;
    @Autowired private SettingsRepository settings;

    // The H2 database is shared across every test class in the same JVM run, so rows
    // another class committed (e.g. AuthenticationTest's admins) are still visible here
    // even though @Transactional rolls back what THIS class writes. Start every test
    // from a known-empty slate rather than assuming a pristine database.
    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
        admins.deleteAll();
    }

    @Test
    void persistsAnOrderWithItemsAndAssignsASequence() {
        Product product = products.save(Product.create("Café", new BigDecimal("3.50"), null));

        Order order = Order.create("fake", Instant.now().plusSeconds(600));
        order.addItem(OrderItem.snapshotOf(product, 2));
        order.recalculateTotal();
        Order saved = orders.saveAndFlush(order);

        assertThat(saved.getTotal()).isEqualByComparingTo("7.00");
        assertThat(saved.getStatus()).isEqualTo(com.example.localhostfacom.order.OrderStatus.PENDING);
        assertThat(saved.getSeq()).isNotNull().isPositive();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().getFirst().getProductName()).isEqualTo("Café");
        assertThat(saved.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("3.50");
    }

    @Test
    void orderItemKeepsItsSnapshotWhenTheProductPriceChanges() {
        Product product = products.save(Product.create("Bolo", new BigDecimal("5.00"), null));
        Order order = Order.create("fake", Instant.now().plusSeconds(600));
        order.addItem(OrderItem.snapshotOf(product, 1));
        order.recalculateTotal();
        Order saved = orders.saveAndFlush(order);

        product.update("Bolo", new BigDecimal("9.00"), null, true);
        products.saveAndFlush(product);

        Order reloaded = orders.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("5.00");
        assertThat(reloaded.getTotal()).isEqualByComparingTo("5.00");
    }

    @Test
    void findsAnAdminByEmailCaseInsensitively() {
        admins.save(Admin.create("Person@Example.com", "hash"));
        assertThat(admins.findByEmailIgnoreCase("person@example.com")).isPresent();
        assertThat(admins.countByActiveTrue()).isEqualTo(1L);
    }

    @Test
    void readsTheSingletonSettingsRow() {
        assertThat(settings.get().getGoalTarget()).isPositive();
    }

    @Test
    void listsOnlyActiveProducts() {
        products.save(Product.create("Ativo", new BigDecimal("1.00"), null));
        Product inactive = products.save(Product.create("Inativo", new BigDecimal("1.00"), null));
        inactive.update("Inativo", new BigDecimal("1.00"), null, false);
        products.saveAndFlush(inactive);

        List<Product> active = products.findByActiveTrueOrderByNameAsc();
        assertThat(active).extracting(Product::getName).containsExactly("Ativo");
    }
}
