package com.example.localhostfacom.dashboard.dto;

import java.math.BigDecimal;

/** {@code current} is the net balance and may legitimately be negative. */
public record GoalResponse(BigDecimal current, BigDecimal target, String crowdfundingUrl) {}
