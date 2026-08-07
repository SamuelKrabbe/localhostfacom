package com.example.localhostfacom.order.dto;

import com.example.localhostfacom.order.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminOrderResponse(
        UUID id,
        Long seq,
        String status,
        BigDecimal total,
        String paymentProvider,
        boolean hasCharge,
        Instant createdAt,
        Instant expiresAt,
        Instant paidAt,
        UUID paidManuallyBy,
        List<Item> items) {

    public record Item(String productName, BigDecimal unitPrice, int quantity) {}

    public static AdminOrderResponse of(Order order) {
        return new AdminOrderResponse(
                order.getId(),
                order.getSeq(),
                order.getStatus().name(),
                order.getTotal(),
                order.getPaymentProvider(),
                order.hasCharge(),
                order.getCreatedAt(),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getPaidManuallyBy(),
                order.getItems().stream()
                        .map(item -> new Item(item.getProductName(), item.getUnitPrice(), item.getQuantity()))
                        .toList());
    }
}
