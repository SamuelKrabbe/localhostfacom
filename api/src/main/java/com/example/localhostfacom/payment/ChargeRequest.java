package com.example.localhostfacom.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The expiry comes from the order row, so both sides always agree on the deadline. */
public record ChargeRequest(UUID orderId, BigDecimal amount, String description, Instant expiresAt) {}
