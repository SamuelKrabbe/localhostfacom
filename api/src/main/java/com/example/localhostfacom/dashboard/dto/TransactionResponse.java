package com.example.localhostfacom.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code id} is the order sequence, never the order UUID. */
public record TransactionResponse(String id, String productNames, BigDecimal amount, Instant timestamp) {}
