package com.example.localhostfacom.dashboard;

import com.example.localhostfacom.dashboard.dto.ChartPointResponse;
import com.example.localhostfacom.dashboard.dto.DashboardResponse;
import com.example.localhostfacom.dashboard.dto.GoalResponse;
import com.example.localhostfacom.dashboard.dto.KpiResponse;
import com.example.localhostfacom.dashboard.dto.TransactionPageResponse;
import com.example.localhostfacom.dashboard.dto.TransactionResponse;
import com.example.localhostfacom.expense.ExpenseService;
import com.example.localhostfacom.order.Order;
import com.example.localhostfacom.order.OrderRepository;
import com.example.localhostfacom.order.OrderStatus;
import com.example.localhostfacom.settings.Settings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read straight from the database on every request, never cached. A stale transparency
 * figure is worse than a slow one.
 */
@Service
public class DashboardService {

    /** Otherwise an evening sale lands on the wrong day. */
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final int CHART_DAYS = 7;

    private final OrderRepository orders;
    private final ExpenseService expenses;
    private final com.example.localhostfacom.settings.SettingsService settings;

    public DashboardService(OrderRepository orders, ExpenseService expenses,
                            com.example.localhostfacom.settings.SettingsService settings) {
        this.orders = orders;
        this.expenses = expenses;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build(int page, int size) {
        BigDecimal totalRaised = scale(orders.sumPaidTotal());
        BigDecimal totalExpenses = scale(expenses.total());
        long totalOrders = orders.countPaid();

        Settings currentSettings = settings.get();
        BigDecimal netBalance = totalRaised.subtract(totalExpenses);

        KpiResponse kpis = new KpiResponse(
                totalRaised,
                totalExpenses,
                netBalance,
                totalOrders,
                averageTicket(totalRaised, totalOrders),
                topProduct(),
                scale(orders.sumPaidSince(startOfToday())),
                scale(orders.sumPaidSince(startOfDaysAgo(7))),
                scale(orders.sumPaidSince(startOfDaysAgo(30))));

        return new DashboardResponse(
                kpis,
                new GoalResponse(netBalance, currentSettings.getGoalTarget(),
                        currentSettings.getCrowdfundingUrl()),
                chart(),
                transactions(page, size));
    }

    /**
     * Guards the two ways this can blow up: dividing by zero orders, and BigDecimal's
     * refusal to divide when the result does not terminate.
     */
    private BigDecimal averageTicket(BigDecimal totalRaised, long totalOrders) {
        if (totalOrders == 0) {
            return scale(BigDecimal.ZERO);
        }
        return totalRaised.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_EVEN);
    }

    private String topProduct() {
        List<String> names = orders.findProductNamesByUnitsSold(PageRequest.of(0, 1));
        return names.isEmpty() ? null : names.getFirst();
    }

    /**
     * Buckets in Java rather than SQL. Date truncation with a time zone is spelled
     * differently on PostgreSQL and H2, and the row count here is tiny.
     */
    private List<ChartPointResponse> chart() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = today.minusDays(CHART_DAYS - 1L);

        Map<LocalDate, BigDecimal> byDay = orders.findPaidSince(from.atStartOfDay(ZONE).toInstant())
                .stream()
                .collect(Collectors.groupingBy(
                        order -> LocalDate.ofInstant(order.getPaidAt(), ZONE),
                        LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Order::getTotal, BigDecimal::add)));

        List<ChartPointResponse> points = new ArrayList<>(CHART_DAYS);
        for (int i = 0; i < CHART_DAYS; i++) {
            LocalDate day = from.plusDays(i);
            // Zero-filled, so the chart keeps a stable seven-column shape on quiet days.
            points.add(new ChartPointResponse(
                    DAY_LABEL.format(day), scale(byDay.getOrDefault(day, BigDecimal.ZERO))));
        }
        return points;
    }

    private TransactionPageResponse transactions(int page, int size) {
        Page<Order> paid = orders.findByStatusOrderByPaidAtDesc(
                OrderStatus.PAID, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)));

        List<TransactionResponse> content = paid.getContent().stream()
                .map(order -> new TransactionResponse(
                        // The sequence, not the UUID: the UUID is the status-endpoint handle.
                        String.valueOf(order.getSeq()),
                        describe(order),
                        order.getTotal(),
                        order.getPaidAt()))
                .toList();

        return new TransactionPageResponse(content, paid.getTotalPages(), paid.getTotalElements());
    }

    private String describe(Order order) {
        return order.getItems().stream()
                .map(item -> item.getQuantity() + "x " + item.getProductName())
                .collect(Collectors.joining(", "));
    }

    private Instant startOfToday() {
        return LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
    }

    private Instant startOfDaysAgo(int days) {
        return LocalDate.now(ZONE).minusDays(days).atStartOfDay(ZONE).toInstant();
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_EVEN);
    }
}
