package com.example.localhostfacom.dashboard.dto;

import java.util.List;

public record DashboardResponse(
        KpiResponse kpis,
        GoalResponse goal,
        List<ChartPointResponse> chartData,
        TransactionPageResponse transactions) {}
