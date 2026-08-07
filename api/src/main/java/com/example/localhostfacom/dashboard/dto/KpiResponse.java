package com.example.localhostfacom.dashboard.dto;

import java.math.BigDecimal;

public record KpiResponse(
        BigDecimal totalRaised,
        BigDecimal totalExpenses,
        BigDecimal netBalance,
        long totalOrders,
        BigDecimal averageTicket,
        String topProduct,
        BigDecimal soldToday,
        BigDecimal soldThisWeek,
        BigDecimal soldThisMonth) {}
