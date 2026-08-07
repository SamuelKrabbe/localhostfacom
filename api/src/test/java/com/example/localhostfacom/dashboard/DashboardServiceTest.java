package com.example.localhostfacom.dashboard;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import com.example.localhostfacom.dashboard.dto.DashboardResponse;
import com.example.localhostfacom.expense.ExpenseRepository;
import com.example.localhostfacom.expense.ExpenseService;
import com.example.localhostfacom.order.OrderRepository;
import com.example.localhostfacom.order.OrderService;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import com.example.localhostfacom.product.ProductService;
import com.example.localhostfacom.settings.SettingsService;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class DashboardServiceTest {

    @Autowired private DashboardService dashboard;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orders;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;
    @Autowired private ExpenseService expenses;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private SettingsService settings;
    @Autowired private AdminRepository adminRepository;

    private Product coffee;
    private Product cake;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
        expenseRepository.deleteAll();
        adminRepository.deleteAll();
        settings.update(new BigDecimal("2000.00"), "https://vakinha.example/sala");
        coffee = productService.create("Café", new BigDecimal("3.00"), null);
        cake = productService.create("Bolo", new BigDecimal("5.00"), null);
        adminId = adminRepository.save(Admin.create("owner@example.com", "hash")).getId();
    }

    private void paidOrder(Product product, int quantity) {
        var order = orderService.create(List.of(new CreateOrderRequest.Item(product.getId(), quantity)));
        orderService.markPaid(order.getId(), null);
    }

    /** An unsold room is the day-one state, not an edge case. */
    @Test
    void reportsZeroesAndANullTopProductWithNoSales() {
        DashboardResponse result = dashboard.build(0, 20);

        assertThat(result.kpis().totalRaised()).isEqualByComparingTo("0.00");
        assertThat(result.kpis().totalOrders()).isZero();
        assertThat(result.kpis().averageTicket()).isEqualByComparingTo("0.00");
        assertThat(result.kpis().topProduct()).isNull();
        assertThat(result.transactions().content()).isEmpty();
    }

    @Test
    void countsOnlyPaidOrders() {
        paidOrder(coffee, 1);
        orderService.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 5)));

        DashboardResponse result = dashboard.build(0, 20);

        assertThat(result.kpis().totalRaised()).isEqualByComparingTo("3.00");
        assertThat(result.kpis().totalOrders()).isEqualTo(1L);
    }

    /**
     * BigDecimal.divide without an explicit scale and RoundingMode throws on a
     * non-terminating result, which would take down the whole public dashboard.
     */
    @Test
    void roundsTheAverageTicketInsteadOfThrowing() {
        paidOrder(coffee, 1);
        paidOrder(coffee, 1);
        paidOrder(cake, 2);

        DashboardResponse result = dashboard.build(0, 20);

        assertThat(result.kpis().totalRaised()).isEqualByComparingTo("16.00");
        assertThat(result.kpis().averageTicket()).isEqualByComparingTo("5.33");
    }

    @Test
    void subtractsExpensesFromTheNetBalance() {
        paidOrder(coffee, 10);
        expenses.create("Insumos", new BigDecimal("12.00"), LocalDate.now(), adminId);

        DashboardResponse result = dashboard.build(0, 20);

        assertThat(result.kpis().totalRaised()).isEqualByComparingTo("30.00");
        assertThat(result.kpis().totalExpenses()).isEqualByComparingTo("12.00");
        assertThat(result.kpis().netBalance()).isEqualByComparingTo("18.00");
        assertThat(result.goal().current()).isEqualByComparingTo("18.00");
    }

    /** Buying stock before selling it is normal, and hiding the deficit would be dishonest. */
    @Test
    void reportsANegativeBalanceWhenExpensesExceedRevenue() {
        paidOrder(coffee, 1);
        expenses.create("Estoque inicial", new BigDecimal("100.00"), LocalDate.now(), adminId);

        DashboardResponse result = dashboard.build(0, 20);

        assertThat(result.kpis().netBalance()).isEqualByComparingTo("-97.00");
        assertThat(result.goal().current()).isEqualByComparingTo("-97.00");
    }

    @Test
    void namesTheBestSellingProductFromTheItemSnapshot() {
        paidOrder(cake, 5);
        paidOrder(coffee, 1);

        assertThat(dashboard.build(0, 20).kpis().topProduct()).isEqualTo("Bolo");
    }

    /** A renamed product must not rewrite what past sales recorded. */
    @Test
    void keepsTheOldNameInTheTopProductAfterARename() {
        paidOrder(cake, 5);
        productService.update(cake.getId(), "Bolo de Cenoura", new BigDecimal("5.00"), null, true);

        assertThat(dashboard.build(0, 20).kpis().topProduct()).isEqualTo("Bolo");
    }

    @Test
    void alwaysReturnsSevenZeroFilledChartDays() {
        paidOrder(coffee, 1);

        assertThat(dashboard.build(0, 20).chartData()).hasSize(7);
    }

    @Test
    void exposesTheOrderSequenceRatherThanTheOrderUuid() {
        paidOrder(coffee, 2);

        var transaction = dashboard.build(0, 20).transactions().content().getFirst();

        assertThat(transaction.id()).isNotBlank();
        assertThat(transaction.productNames()).isEqualTo("2x Café");
        assertThat(transaction.amount()).isEqualByComparingTo("6.00");
        // The UUID is the handle for the status endpoint and has no place on a public feed.
        assertThat(transaction.id()).doesNotContain("-");
    }

    @Test
    void carriesTheGoalAndCrowdfundingLink() {
        var goal = dashboard.build(0, 20).goal();

        assertThat(goal.target()).isEqualByComparingTo("2000.00");
        assertThat(goal.crowdfundingUrl()).isEqualTo("https://vakinha.example/sala");
    }
}
