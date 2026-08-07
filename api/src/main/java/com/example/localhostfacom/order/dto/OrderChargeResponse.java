package com.example.localhostfacom.order.dto;

import com.example.localhostfacom.order.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderChargeResponse(
        UUID orderId,
        BigDecimal total,
        String payload,
        String qrImageBase64,
        String checkoutUrl,
        Instant expiresAt) {

    public static OrderChargeResponse of(Order order) {
        return new OrderChargeResponse(
                order.getId(),
                order.getTotal(),
                order.getPaymentPayload(),
                order.getPaymentQrBase64(),
                order.getPaymentCheckoutUrl(),
                order.getExpiresAt());
    }
}
